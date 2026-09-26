package com.helix.console.result.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.engine.client.EngineClient;
import com.helix.facade.engine.EngineApiRsp;
import com.helix.console.engine.dto.ExecutionResult;
import com.helix.console.engine.support.ExecutionResultConverter;
import com.helix.console.result.dto.*;
import com.helix.console.result.entity.DecisionLog;
import com.helix.console.result.mapper.DecisionLogMapper;
import com.helix.console.result.service.ResultSetService;
import com.helix.console.system.security.TenantScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Execution result query service (decision log JSON inline parsing for display).
 *
 * <p>Hit details and node traces are stored inline as JSON in
 * {@code t_decision_log} ({@code hits_json}/{@code traces_json}); this service
 * parses the JSON for display, empty JSON displays as empty.</p>
 *
 * <p>The unified writer is helix-engine's {@code DecisionLogWriter}; the console
 * is read-only and excludes shadow-track evaluation records (shadow=0).</p>
 *
 * <p>{@link #persist} originally wrote console-forwarded execution results into
 * t_resultset; the engine now writes a decision log for every decision
 * (including console-forwarded ones), so writing here is disabled as a no-op.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultSetServiceImpl implements ResultSetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Result code -> text (t_decision_log.result_type) */
    private static final Map<Integer, String> RESULT_TEXT = new LinkedHashMap<Integer, String>() {{
        put(1, "Pass");
        put(2, "Reject");
        put(3, "Manual Review");
    }};
    /** Query input text -> code (accepts both code and text forms) */
    private static final Map<String, Integer> RESULT_CODE = new LinkedHashMap<String, Integer>() {{
        put("1", 1);
        put("2", 2);
        put("3", 3);
        put("Pass", 1);
        put("Reject", 2);
        put("Manual Review", 3);
    }};

    private final DecisionLogMapper decisionLogMapper;
    private final EngineClient engineClient;
    private final ObjectMapper objectMapper;

    @Override
    public void persist(ExecutionResult result, Map<String, Object> input,
                        Integer type, String pid, String batchNo) {
        // t_resultset writes are disabled: helix-engine writes t_decision_log for every
        // decision, and batch test/try-run calls leave traces on the engine side too.
        // Kept as an empty implementation to preserve the caller signature.
    }

    @Override
    public PageResult<ResultSetVO> page(ResultQuery query, long pageNo, long pageSize) {
        LambdaQueryWrapper<DecisionLog> qw = buildQuery(query);
        qw.orderByDesc(DecisionLog::getId);
        Page<DecisionLog> page = decisionLogMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<ResultSetVO> vos = page.getRecords().stream()
                .map(l -> toVO(l, false)).collect(Collectors.toList());
        return PageResult.of(vos, page.getTotal(), pageNo, pageSize);
    }

    @Override
    public ResultSetVO detail(Integer id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Decision log id must not be blank");
        }
        DecisionLog logRow = decisionLogMapper.selectById(id.longValue());
        if (logRow == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Decision log does not exist");
        }
        // Viewing decision logs of other organizations is forbidden
        TenantScope.checkVisible(logRow.getOrganId());
        return toVO(logRow, true);
    }

    @Override
    public ResultSetVO detailByTraceId(String traceId) {
        DecisionLog logRow = decisionLogMapper.selectOne(new LambdaQueryWrapper<DecisionLog>()
                .eq(DecisionLog::getTraceId, traceId)
                .eq(DecisionLog::getShadow, 0)
                .last("limit 1"));
        if (logRow == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Decision log does not exist: " + traceId);
        }
        // Viewing decision logs of other organizations is forbidden
        TenantScope.checkVisible(logRow.getOrganId());
        return toVO(logRow, true);
    }

    @Override
    public BatchTestResultVO batchTest(BatchTestDTO dto) {
        if (dto.getSamples() == null || dto.getSamples().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Test samples must not be blank");
        }
        if (dto.getSamples().size() > 1000) {
            throw BizException.of(ResultCode.PARAM_INVALID, "At most 1000 samples per batch test");
        }
        String batchNo = "B" + System.currentTimeMillis();

        BatchTestResultVO summary = new BatchTestResultVO();
        summary.setBatchNo(batchNo);
        summary.setEngineCode(dto.getEngineCode());
        summary.setTotal(dto.getSamples().size());

        long start = System.currentTimeMillis();
        AtomicInteger pass = new AtomicInteger();
        AtomicInteger reject = new AtomicInteger();
        AtomicInteger manual = new AtomicInteger();
        AtomicInteger error = new AtomicInteger();

        // Single try-compute scenarios should return node traces; large batch samples default to not returning them to avoid oversized responses
        boolean withTrace = Boolean.TRUE.equals(dto.getWithTrace());

        for (Map<String, Object> sample : dto.getSamples()) {
            try {
                // Execution is uniformly forwarded to helix-engine; logs are written to t_decision_log on the engine side.
                // versionId pass-through: null runs the current effective version; a given value
                // try-computes/replays by decision flow version.
                EngineApiRsp rsp = engineClient.decision(dto.getEngineCode(), null, dto.getVersionId(), sample);
                ExecutionResult r = ExecutionResultConverter.from(rsp.getData());
                String code = r.getResult();
                if ("1".equals(code)) {
                    pass.incrementAndGet();
                } else if ("3".equals(code)) {
                    manual.incrementAndGet();
                } else {
                    reject.incrementAndGet();
                }
                summary.getDetails().add(briefOf(r, sample, withTrace));
            } catch (Exception e) {
                error.incrementAndGet();
                log.warn("Batch test sample execution failed: {}", e.getMessage());
                summary.getDetails().add(errorDetail(sample, e.getMessage()));
            }
        }

        summary.setPassCount(pass.get());
        summary.setRejectCount(reject.get());
        summary.setManualCount(manual.get());
        summary.setErrorCount(error.get());
        summary.setCostMs(System.currentTimeMillis() - start);
        int valid = summary.getTotal() - error.get();
        summary.setPassRate(valid == 0 ? 0D : Math.round(pass.get() * 10000D / valid) / 100D);
        return summary;
    }

    @Override
    public void removeBatch(String batchNo) {
        // Since v3 batch tests no longer write a local batch table (traces are unified on the engine side); kept as an empty implementation for caller compatibility.
    }

    // ------------------------------------------------------------------ Internal methods

    private LambdaQueryWrapper<DecisionLog> buildQuery(ResultQuery q) {
        LambdaQueryWrapper<DecisionLog> qw = new LambdaQueryWrapper<>();
        // Main decisions only; exclude shadow-track evaluation records
        qw.eq(DecisionLog::getShadow, 0);
        qw.eq(DecisionLog::getDeleted, 0);
        // Org users only see decision logs of their organization and platform common engines
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(DecisionLog::getOrganId, organs);
        }
        if (q == null) {
            return qw;
        }
        if (StringUtils.isNotBlank(q.getEngineCode())) {
            qw.eq(DecisionLog::getEngineCode, q.getEngineCode());
        }
        if (StringUtils.isNotBlank(q.getResult())) {
            Integer code = RESULT_CODE.get(q.getResult().trim());
            if (code == null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Unrecognized result filter value: " + q.getResult());
            }
            qw.eq(DecisionLog::getResultType, code);
        }
        if (StringUtils.isNotBlank(q.getUuid())) {
            qw.eq(DecisionLog::getTraceId, q.getUuid());
        }
        if (StringUtils.isNotBlank(q.getPid())) {
            qw.eq(DecisionLog::getPid, q.getPid());
        }
        // batchNo is a legacy-table-only condition with no corresponding column in the new pipeline; ignored
        if (StringUtils.isNotBlank(q.getStartTime())) {
            qw.ge(DecisionLog::getCreatedTime, LocalDateTime.parse(q.getStartTime(), TIME_FMT));
        }
        if (StringUtils.isNotBlank(q.getEndTime())) {
            qw.le(DecisionLog::getCreatedTime, LocalDateTime.parse(q.getEndTime(), TIME_FMT));
        }
        return qw;
    }

    private ResultSetVO toVO(DecisionLog logRow, boolean withDetail) {
        ResultSetVO vo = new ResultSetVO();
        vo.setId(logRow.getId() == null ? null : logRow.getId().intValue());
        vo.setTraceId(logRow.getTraceId());
        vo.setEngineCode(logRow.getEngineCode());
        vo.setEngineName(logRow.getEngineCode());
        vo.setEngineVersion(logRow.getVersionId());
        vo.setResult(RESULT_TEXT.getOrDefault(logRow.getResultType(), String.valueOf(logRow.getResultType())));
        vo.setPass(Objects.equals(logRow.getResultType(), 1));
        vo.setManualReview(Objects.equals(logRow.getResultType(), 3));
        vo.setScore(logRow.getTotalScore() == null ? 0 : logRow.getTotalScore());
        vo.setPid(logRow.getPid());
        vo.setUid(logRow.getUid());
        vo.setCreatedTime(logRow.getCreatedTime());
        vo.setInput(readMap(logRow.getInputJson()));
        if (withDetail) {
            // Inline JSON parsing
            vo.setTraces(loadTraces(logRow));
            vo.setHitDetails(loadHits(logRow));
        }
        return vo;
    }

    /** Node traces: parse traces_json */
    private List<EngineApiRsp.Trace> loadTraces(DecisionLog logRow) {
        if (StringUtils.isBlank(logRow.getTracesJson())) {
            return new ArrayList<>();
        }
        try {
            List<EngineApiRsp.Trace> traces = objectMapper.readValue(
                    logRow.getTracesJson(),
                    new TypeReference<List<EngineApiRsp.Trace>>() {
                    });
            return traces == null ? new ArrayList<>() : traces;
        } catch (Exception e) {
            log.warn("Failed to parse node trace JSON decisionId={}: {}", logRow.getId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Hit details: parse hits_json -> "name -> expression" display rows, consistent with the old format */
    private List<String> loadHits(DecisionLog logRow) {
        if (StringUtils.isBlank(logRow.getHitsJson())) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> hits = objectMapper.readValue(
                    logRow.getHitsJson(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            return hits == null ? new ArrayList<>()
                    : hits.stream().map(ResultSetServiceImpl::formatHit).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to parse hit details JSON decisionId={}: {}", logRow.getId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /** JSON hit details -> display row "name(code) -> expression", consistent with the old format */
    private static String formatHit(Map<String, Object> h) {
        String name = str(h.get("ruleName"));
        if (StringUtils.isBlank(name)) {
            String code = str(h.get("ruleCode"));
            name = StringUtils.isNotBlank(code)
                    ? code
                    : "Rule #" + Objects.toString(h.get("ruleId"), "?");
        }
        String expr = str(h.get("expression"));
        return expr == null ? name : name + " → " + expr;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Execution response result code -> display text (consistent with t_decision_log semantics) */
    private String resultTextOf(String code) {
        Integer c = RESULT_CODE.get(code == null ? "" : code.trim());
        return c == null ? code : RESULT_TEXT.get(c);
    }

    /** Lightweight result for batch tests; returns node-level execution traces when withTrace is true */
    private ResultSetVO briefOf(ExecutionResult r, Map<String, Object> sample, boolean withTrace) {
        ResultSetVO vo = new ResultSetVO();
        vo.setTraceId(r.getTraceId());
        vo.setEngineCode(r.getEngineCode());
        vo.setEngineName(r.getEngineName());
        vo.setResult(resultTextOf(r.getResult()));
        vo.setPass("1".equals(r.getResult()));
        vo.setManualReview("3".equals(r.getResult()));
        vo.setScore(r.getScore());
        vo.setInput(sample);
        List<String> hits = new ArrayList<>();
        if (r.getTraces() != null) {
            r.getTraces().stream().filter(EngineApiRsp.Trace::isHit)
                    .forEach(t -> {
                        if (t.getHitDetails() != null) {
                            hits.addAll(t.getHitDetails());
                        }
                    });
        }
        vo.setHitDetails(hits);
        if (withTrace) {
            vo.setTraces(r.getTraces());
        }
        return vo;
    }

    private ResultSetVO errorDetail(Map<String, Object> sample, String msg) {
        ResultSetVO vo = new ResultSetVO();
        vo.setResult("Execution Failed");
        vo.setInput(sample);
        vo.setHitDetails(Collections.singletonList(msg));
        return vo;
    }

    private Map<String, Object> readMap(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
