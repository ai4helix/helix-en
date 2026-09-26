package com.helix.console.batch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helix.console.batch.client.DataCenterClient;
import com.helix.console.batch.entity.IndicatorBatch;
import com.helix.console.batch.entity.IndicatorBatchItem;
import com.helix.console.batch.mapper.IndicatorBatchItemMapper;
import com.helix.console.batch.mapper.IndicatorBatchMapper;
import com.helix.console.batch.service.BatchService;
import com.helix.console.batch.support.CsvDownloadUtil;
import com.helix.console.batch.support.CsvUtil;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.datamanage.entity.Field;
import com.helix.console.datamanage.mapper.FieldMapper;
import com.helix.console.knowledge.entity.KnowledgeTree;
import com.helix.console.knowledge.enums.TreeType;
import com.helix.console.knowledge.mapper.KnowledgeTreeMapper;
import com.helix.console.engine.client.EngineClient;
import com.helix.facade.engine.EngineApiRsp;
import com.helix.console.engine.entity.Engine;
import com.helix.console.engine.mapper.EngineMapper;
import com.helix.console.system.entity.SysRole;
import com.helix.console.system.entity.SysUser;
import com.helix.console.system.mapper.SysRelationMapper;
import com.helix.console.system.mapper.SysRoleMapper;
import com.helix.console.system.mapper.SysUserMapper;
import com.helix.console.system.security.TenantScope;
import com.helix.console.system.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tenant batch run service implementation.
 *
 * <p>Data flow: CSV upload -> field visibility validation -> task/items persisted
 * into the engine DB -> async row-by-row helix-engine decision calls -> results
 * written back to items -> page query / CSV download.
 * During import, indicator data is also forwarded to the Data Center for
 * retention (failure does not block the import, only returns a warning).</p>
 */
@Slf4j
@Service
public class BatchServiceImpl implements BatchService {

    /** Max rows per task */
    private static final int MAX_ROWS = 5000;

    /** Chunk size for Data Center retention forwarding */
    private static final int DC_CHUNK = 200;

    /** Batch run thread pool: rows progress serially within a task; pool size caps concurrent tasks */
    private final ExecutorService executor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "indicator-batch-runner");
        t.setDaemon(true);
        return t;
    });

    private static final Pattern FIELD_EN_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    private static final String STATUS_PENDING = "0";
    private static final String STATUS_SUCCESS = "1";
    private static final String STATUS_FAIL = "2";

    /** Default initial password for batch user import */
    private static final String DEFAULT_USER_PASSWORD = "Init@1234";

    private final IndicatorBatchMapper batchMapper;
    private final IndicatorBatchItemMapper itemMapper;
    private final FieldMapper fieldMapper;
    private final EngineMapper engineMapper;
    private final EngineClient engineClient;
    private final DataCenterClient dataCenterClient;
    private final ObjectMapper objectMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRelationMapper sysRelationMapper;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeTreeMapper knowledgeTreeMapper;
    /** engine DB transaction template (importData insert section runs in one transaction to prevent orphan tasks) */
    private final TransactionTemplate engineTx;

    public BatchServiceImpl(IndicatorBatchMapper batchMapper,
                            IndicatorBatchItemMapper itemMapper,
                            FieldMapper fieldMapper,
                            EngineMapper engineMapper,
                            EngineClient engineClient,
                            DataCenterClient dataCenterClient,
                            ObjectMapper objectMapper,
                            SysUserMapper sysUserMapper,
                            SysRoleMapper sysRoleMapper,
                            SysRelationMapper sysRelationMapper,
                            PasswordEncoder passwordEncoder,
                            KnowledgeTreeMapper knowledgeTreeMapper,
                            @Qualifier("engineTxManager") PlatformTransactionManager engineTxManager) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.fieldMapper = fieldMapper;
        this.engineMapper = engineMapper;
        this.engineClient = engineClient;
        this.dataCenterClient = dataCenterClient;
        this.objectMapper = objectMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRelationMapper = sysRelationMapper;
        this.passwordEncoder = passwordEncoder;
        this.knowledgeTreeMapper = knowledgeTreeMapper;
        this.engineTx = new TransactionTemplate(engineTxManager);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    // ===== 1. Indicator field batch import =====

    @Override
    public Map<String, Object> importFields(MultipartFile file) {
        List<String[]> rows = readCsv(file);
        if (rows.size() < 2) {
            throw BizException.of(ResultCode.PARAM_INVALID, "CSV must contain a header and at least one data row");
        }
        Map<String, Integer> header = headerIndex(rows.get(0),
                Arrays.asList("field_en", "field_cn", "value_type", "is_output", "catalog"));
        if (!header.containsKey("field_en")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Header is missing field_en column");
        }

        // catalog column matches the FIELD category tree by name (visible scope); empty = uncategorized; unknown name raises an error
        Map<String, Integer> catalogNameToId = loadFieldCatalogNameMap();

        List<Long> organs = TenantScope.visibleOrgans();
        // Existing field index: field_en -> entity (for update detection)
        Map<String, Field> existing = organs == null
                ? fieldMapper.selectList(new LambdaQueryWrapper<Field>()).stream()
                        .collect(Collectors.toMap(Field::getFieldEn, f -> f, (a, b) -> a))
                : fieldMapper.selectList(new LambdaQueryWrapper<Field>()
                        .in(Field::getOrganId, organs)).stream()
                        .collect(Collectors.toMap(Field::getFieldEn, f -> f, (a, b) -> a));

        int inserted = 0;
        int updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i < rows.size(); i++) {
            String[] cells = rows.get(i);
            String fieldEn = cellAt(cells, header.get("field_en"));
            if (StringUtils.isBlank(fieldEn)) {
                continue;
            }
            fieldEn = fieldEn.trim();
            if (!FIELD_EN_PATTERN.matcher(fieldEn).matches()) {
                throw BizException.of(ResultCode.PARAM_INVALID,
                        "Row " + i + " field_en is invalid (must be letters/digits/underscore and start with a letter): " + fieldEn);
            }
            Integer valueType = parseIntCell(cellAt(cells, header.get("value_type")), 1);
            if (valueType < 1 || valueType > 4) {
                throw BizException.of(ResultCode.PARAM_INVALID,
                        "Row " + i + " value_type must be 1 number/2 string/3 enum/4 decimal: " + valueType);
            }
            Integer isOutput = parseIntCell(cellAt(cells, header.get("is_output")), 0);
            if (isOutput != 0 && isOutput != 1) {
                throw BizException.of(ResultCode.PARAM_INVALID,
                        "Row " + i + " is_output must be 0 or 1");
            }
            String fieldCn = cellAt(cells, header.get("field_cn"));
            Integer catalogId = resolveCatalog(cellAt(cells, header.get("catalog")), catalogNameToId, i);

            Field exist = existing.get(fieldEn);
            if (exist != null) {
                exist.setFieldCn(StringUtils.defaultIfBlank(fieldCn, exist.getFieldCn()));
                exist.setValueType(valueType);
                exist.setIsOutput(isOutput);
                if (header.containsKey("catalog")) {
                    exist.setCatalogId(catalogId);
                }
                exist.setUpdatedTime(now);
                fieldMapper.updateById(exist);
                updated++;
            } else {
                Field f = new Field();
                f.setFieldEn(fieldEn);
                f.setFieldCn(fieldCn);
                f.setFieldTypeid(0);
                f.setValueType(valueType);
                f.setIsOutput(isOutput);
                f.setIsCommon(0);
                f.setIsDerivative(0);
                f.setCatalogId(catalogId);
                // Admin users default to platform common (0); org users are forced to their own organization
                f.setOrganId(TenantScope.writeOrgan((Integer) null));
                f.setCreatedTime(now);
                f.setUpdatedTime(now);
                fieldMapper.insert(f);
                existing.put(fieldEn, f);
                inserted++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inserted", inserted);
        out.put("updated", updated);
        out.put("total", rows.size() - 1);
        return out;
    }

    /** FIELD category tree name -> id within visible scope (first wins on duplicates) */
    private Map<String, Integer> loadFieldCatalogNameMap() {
        LambdaQueryWrapper<KnowledgeTree> qw = new LambdaQueryWrapper<KnowledgeTree>()
                .eq(KnowledgeTree::getTreeType, TreeType.FIELD.getCode())
                .eq(KnowledgeTree::getStatus, 1)
                .isNull(KnowledgeTree::getEngineId);
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(KnowledgeTree::getOrganId, organs);
        }
        Map<String, Integer> map = new HashMap<>();
        for (KnowledgeTree n : knowledgeTreeMapper.selectList(qw)) {
            map.putIfAbsent(n.getName(), n.getId());
        }
        return map;
    }

    /** catalog cell parsing: empty = uncategorized (null); unknown name raises a row-level error */
    private Integer resolveCatalog(String raw, Map<String, Integer> nameToId, int rowNo) {
        String name = StringUtils.trimToNull(raw);
        if (name == null) {
            return null;
        }
        Integer id = nameToId.get(name);
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID,
                    "Row " + rowNo + " catalog does not exist (must be a category name from field management): " + name);
        }
        return id;
    }

    // ===== 2. User indicator data batch import (creates batch run task) =====

    @Override
    public Map<String, Object> importData(String name, String engineCode, String keyField, MultipartFile file) {
        if (StringUtils.isBlank(name)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Task name must not be blank");
        }
        if (!"uid".equals(keyField) && !"pid".equals(keyField)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "keyField only supports uid or pid");
        }
        Engine engine = engineMapper.selectOne(new LambdaQueryWrapper<Engine>()
                .eq(Engine::getCode, engineCode).eq(Engine::getStatus, 1));
        if (engine == null) {
            throw BizException.of(ResultCode.ENGINE_NOT_FOUND);
        }
        TenantScope.checkVisible(engine.getOrganId() == null ? null : engine.getOrganId().longValue());

        List<String[]> rows = readCsv(file);
        if (rows.size() < 2) {
            throw BizException.of(ResultCode.PARAM_INVALID, "CSV must contain a header and at least one data row");
        }
        List<String> headers = Arrays.stream(rows.get(0))
                .map(String::trim).collect(Collectors.toList());
        int keyIdx = headers.indexOf(keyField);
        if (keyIdx < 0) {
            throw BizException.of(ResultCode.PARAM_INVALID,
                    "Header is missing user key column: " + keyField + " (current header: " + String.join(",", headers) + ")");
        }
        List<String> fieldCols = new ArrayList<>();
        for (int c = 0; c < headers.size(); c++) {
            if (c != keyIdx && StringUtils.isNotBlank(headers.get(c))) {
                fieldCols.add(headers.get(c));
            }
        }
        if (fieldCols.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "No indicator columns found besides " + keyField);
        }

        // Validate all indicator columns exist in the visible field dictionary (avoid silently dropping variables in batch runs)
        List<Long> organs = TenantScope.visibleOrgans();
        LambdaQueryWrapper<Field> fw = new LambdaQueryWrapper<Field>()
                .in(Field::getFieldEn, fieldCols);
        if (organs != null) {
            fw.in(Field::getOrganId, organs);
        }
        Map<String, Field> known = fieldMapper.selectList(fw).stream()
                .collect(Collectors.toMap(Field::getFieldEn, f -> f, (a, b) -> a));
        List<String> missing = fieldCols.stream().filter(c -> !known.containsKey(c))
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw BizException.of(ResultCode.FIELD_NOT_FOUND,
                    "The following indicators are not registered in the field dictionary (or not visible to the current organization); import indicator fields first: " + String.join(",", missing));
        }

        int dataRows = rows.size() - 1;
        if (dataRows > MAX_ROWS) {
            throw BizException.of(ResultCode.PARAM_INVALID, "A task allows at most " + MAX_ROWS + " rows, current " + dataRows + " rows");
        }

        // Persist task + items (single transaction: a row validation failure
        // (e.g. an empty bizKey) would leave an orphan task + partial items,
        // and re-import would create a new task; engine DB transactions use
        // engineTxManager explicitly, see AGENTS.md)
        LocalDateTime now = LocalDateTime.now();
        IndicatorBatch batch = new IndicatorBatch();
        batch.setName(name.trim());
        batch.setEngineCode(engineCode);
        batch.setKeyField(keyField);
        batch.setStatus(0);
        batch.setTotalRows(dataRows);
        batch.setSuccessRows(0);
        batch.setFailRows(0);
        batch.setFileName(file == null ? null : file.getOriginalFilename());
        batch.setOrganId(TenantScope.writeOrgan((Long) null));
        batch.setCreatedBy(UserContext.currentUserId());
        batch.setCreatedTime(now);
        batch.setUpdatedTime(now);
        List<Map<String, Object>> dcRows = new ArrayList<>();
        engineTx.executeWithoutResult(status -> {
            batchMapper.insert(batch);
            for (int i = 1; i < rows.size(); i++) {
                String[] cells = rows.get(i);
                String bizKey = cellAt(cells, keyIdx);
                if (StringUtils.isBlank(bizKey)) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "Row " + i + " " + keyField + " must not be blank");
                }
                bizKey = bizKey.trim();
                Map<String, Object> values = new LinkedHashMap<>();
                Map<String, String> rawValues = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    if (c == keyIdx || StringUtils.isBlank(headers.get(c))) {
                        continue;
                    }
                    String raw = cellAt(cells, c);
                    rawValues.put(headers.get(c), raw);
                    values.put(headers.get(c), typedValue(raw));
                }
                IndicatorBatchItem item = new IndicatorBatchItem();
                item.setBatchId(batch.getId());
                item.setOrganId(batch.getOrganId());
                item.setRowNo(i);
                item.setBizKey(bizKey);
                item.setStatus(0);
                item.setCreatedTime(now);
                item.setUpdatedTime(now);
                item.setDataJson(toJson(values));
                itemMapper.insert(item);

                Map<String, Object> dcRow = new HashMap<>();
                dcRow.put("userKey", bizKey);
                dcRow.put("fields", rawValues);
                dcRows.add(dcRow);
            }
        });

        // Data Center retention (chunked forwarding; failure does not block the import)
        String warning = null;
        try {
            int written = 0;
            for (int from = 0; from < dcRows.size(); from += DC_CHUNK) {
                List<Map<String, Object>> chunk =
                        dcRows.subList(from, Math.min(from + DC_CHUNK, dcRows.size()));
                written += dataCenterClient.importUserData(String.valueOf(batch.getId()), chunk);
            }
            log.info("Batch task {} indicator data Data Center retention completed: {} pairs", batch.getId(), written);
        } catch (Exception e) {
            log.warn("Batch task {} indicator data Data Center retention failed (does not affect the batch run)", batch.getId(), e);
            warning = "Indicator data imported and usable for batch runs, but Data Center retention failed: " + e.getMessage();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batchId", batch.getId());
        out.put("totalRows", dataRows);
        out.put("warning", warning);
        return out;
    }

    // ===== 3. Start batch run =====

    @Override
    public void run(Long batchId) {
        requireVisibleBatch(batchId);
        // Conditional-update claim (UPDATE ... WHERE status<>1, judged by affected rows):
        // if we first selected status by id then updated unconditionally, two concurrent run()
        // calls (double click / replay) would both pass the check -> two runners consume the
        // same items twice (duplicate engine calls, doubled counters); concurrent callers that
        // fail to claim are rejected directly without touching any data.
        Integer claimed = batchMapper.update(null, new LambdaUpdateWrapper<IndicatorBatch>()
                .eq(IndicatorBatch::getId, batchId)
                .and(w -> w.ne(IndicatorBatch::getStatus, 1).or().isNull(IndicatorBatch::getStatus))
                .set(IndicatorBatch::getStatus, 1)
                .set(IndicatorBatch::getSuccessRows, 0)
                .set(IndicatorBatch::getFailRows, 0)
                .set(IndicatorBatch::getErrorMsg, null)
                .set(IndicatorBatch::getFinishedTime, null)
                .set(IndicatorBatch::getUpdatedTime, LocalDateTime.now()));
        if (claimed == null || claimed == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Task is already running, please try again later");
        }
        // Reset items only after a successful claim (the failed caller must not clear others' progress)
        itemMapper.update(null, new LambdaUpdateWrapper<IndicatorBatchItem>()
                .eq(IndicatorBatchItem::getBatchId, batchId)
                .set(IndicatorBatchItem::getStatus, 0)
                .set(IndicatorBatchItem::getTraceId, null)
                .set(IndicatorBatchItem::getResultText, null)
                .set(IndicatorBatchItem::getTotalScore, null)
                .set(IndicatorBatchItem::getHitRules, null)
                .set(IndicatorBatchItem::getErrorMsg, null)
                .set(IndicatorBatchItem::getUpdatedTime, LocalDateTime.now()));
        executor.submit(() -> doRun(batchId));
    }

    /** Async execution: call the engine row by row, write back results and progress */
    private void doRun(Long batchId) {
        // Engine code never changes; query once and pass it down --
        // avoids a per-row batchCode(batchId) selectById (5000 rows = 5000 redundant queries)
        String engineCode = batchCode(batchId);
        try {
            while (true) {
                List<IndicatorBatchItem> pending = itemMapper.selectList(
                        new LambdaQueryWrapper<IndicatorBatchItem>()
                                .eq(IndicatorBatchItem::getBatchId, batchId)
                                .eq(IndicatorBatchItem::getStatus, 0)
                                .orderByAsc(IndicatorBatchItem::getId)
                                .last("LIMIT " + DC_CHUNK));
                if (pending.isEmpty()) {
                    break;
                }
                for (IndicatorBatchItem item : pending) {
                    execOne(batchId, engineCode, item);
                }
            }
            IndicatorBatch batch = batchMapper.selectById(batchId);
            if (batch != null) {
                batch.setStatus(batch.getFailRows() != null && batch.getFailRows() > 0
                        && batch.getSuccessRows() != null && batch.getSuccessRows() == 0 ? 3 : 2);
                batch.setFinishedTime(LocalDateTime.now());
                batch.setUpdatedTime(LocalDateTime.now());
                batchMapper.updateById(batch);
            }
            log.info("Batch task {} run completed", batchId);
        } catch (Exception e) {
            log.error("Batch task {} run aborted with error", batchId, e);
            IndicatorBatch batch = batchMapper.selectById(batchId);
            if (batch != null) {
                batch.setStatus(3);
                batch.setErrorMsg(CsvUtil.truncate(e.getMessage(), 480));
                batch.setFinishedTime(LocalDateTime.now());
                batch.setUpdatedTime(LocalDateTime.now());
                batchMapper.updateById(batch);
            }
        }
    }

    private void execOne(Long batchId, String engineCode, IndicatorBatchItem item) {
        try {
            Map<String, Object> data = objectMapper.readValue(item.getDataJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
            EngineApiRsp.EngineData rsp = engineClient.decision(
                    engineCode, item.getBizKey(), data).getData();
            IndicatorBatchItem upd = new IndicatorBatchItem();
            upd.setId(item.getId());
            upd.setStatus(1);
            upd.setUpdatedTime(LocalDateTime.now());
            if (rsp != null) {
                upd.setTraceId(rsp.getTraceId());
                upd.setResultText(resultText(rsp.getResultType()));
                upd.setTotalScore(rsp.getScore());
                upd.setHitRules(CsvUtil.truncate(hitSummary(rsp), 2000));
            }
            itemMapper.updateById(upd);
            batchMapper.update(null, new LambdaUpdateWrapper<IndicatorBatch>()
                    .eq(IndicatorBatch::getId, batchId)
                    .setSql("success_rows = success_rows + 1"));
        } catch (Exception e) {
            log.warn("Batch task {} row {} execution failed", batchId, item.getRowNo(), e);
            IndicatorBatchItem upd = new IndicatorBatchItem();
            upd.setId(item.getId());
            upd.setStatus(2);
            upd.setErrorMsg(CsvUtil.truncate(e.getMessage(), 480));
            upd.setUpdatedTime(LocalDateTime.now());
            itemMapper.updateById(upd);
            batchMapper.update(null, new LambdaUpdateWrapper<IndicatorBatch>()
                    .eq(IndicatorBatch::getId, batchId)
                    .setSql("fail_rows = fail_rows + 1"));
        }
    }

    private String batchCode(Long batchId) {
        IndicatorBatch batch = batchMapper.selectById(batchId);
        return batch == null ? null : batch.getEngineCode();
    }

    // ===== 4. Query and download =====

    @Override
    public IndicatorBatch detail(Long batchId) {
        return requireVisibleBatch(batchId);
    }

    @Override
    public PageResult<IndicatorBatch> pageBatches(long pageNo, long pageSize) {
        LambdaQueryWrapper<IndicatorBatch> qw = new LambdaQueryWrapper<>();
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(IndicatorBatch::getOrganId, organs);
        }
        qw.orderByDesc(IndicatorBatch::getId);
        Page<IndicatorBatch> page = batchMapper.selectPage(Page.of(pageNo, pageSize), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public PageResult<IndicatorBatchItem> pageItems(Long batchId, long pageNo, long pageSize,
                                                    Integer status, String keyword) {
        requireVisibleBatch(batchId);
        LambdaQueryWrapper<IndicatorBatchItem> qw = new LambdaQueryWrapper<>();
        qw.eq(IndicatorBatchItem::getBatchId, batchId);
        if (status != null) {
            qw.eq(IndicatorBatchItem::getStatus, status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.like(IndicatorBatchItem::getBizKey, keyword.trim());
        }
        qw.orderByAsc(IndicatorBatchItem::getRowNo);
        Page<IndicatorBatchItem> page = itemMapper.selectPage(Page.of(pageNo, pageSize), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public void download(Long batchId, HttpServletResponse response) {
        requireVisibleBatch(batchId);
        try {
            response.setContentType("text/csv;charset=UTF-8");
            String fileName = java.net.URLEncoder.encode("batch_" + batchId + "_results.csv", "UTF-8")
                    .replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
            java.io.Writer writer = new java.io.OutputStreamWriter(
                    response.getOutputStream(), StandardCharsets.UTF_8);
            // BOM: lets Excel detect UTF-8
            writer.write('\uFEFF');
            writer.write("biz_key,row_no,result,total_score,trace_id,hit_rules,error_msg,data_json\n");
            long lastId = 0L;
            while (true) {
                List<IndicatorBatchItem> chunk = itemMapper.selectList(
                        new LambdaQueryWrapper<IndicatorBatchItem>()
                                .eq(IndicatorBatchItem::getBatchId, batchId)
                                .gt(IndicatorBatchItem::getId, lastId)
                                .orderByAsc(IndicatorBatchItem::getId)
                                .last("LIMIT " + DC_CHUNK));
                if (chunk.isEmpty()) {
                    break;
                }
                for (IndicatorBatchItem item : chunk) {
                    lastId = item.getId();
                    writer.write(CsvUtil.escape(item.getBizKey()));
                    writer.write(',');
                    writer.write(String.valueOf(item.getRowNo()));
                    writer.write(',');
                    writer.write(CsvUtil.escape(item.getResultText()));
                    writer.write(',');
                    writer.write(item.getTotalScore() == null ? "" : String.valueOf(item.getTotalScore()));
                    writer.write(',');
                    writer.write(CsvUtil.escape(item.getTraceId()));
                    writer.write(',');
                    writer.write(CsvUtil.escape(item.getHitRules()));
                    writer.write(',');
                    writer.write(CsvUtil.escape(item.getErrorMsg()));
                    writer.write(',');
                    writer.write(CsvUtil.escape(item.getDataJson()));
                    writer.write('\n');
                }
                writer.flush();
            }
            writer.flush();
        } catch (IOException e) {
            throw BizException.of(ResultCode.SYSTEM_ERROR, "Result download failed: " + e.getMessage());
        }
    }

    // ===== Private utilities =====

    private IndicatorBatch requireVisibleBatch(Long batchId) {
        IndicatorBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Batch run task does not exist");
        }
        TenantScope.checkVisible(batch.getOrganId());
        return batch;
    }

    private List<String[]> readCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Please upload a CSV file");
        }
        String name = StringUtils.defaultString(file.getOriginalFilename()).toLowerCase();
        if (!name.endsWith(".csv")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Only UTF-8 encoded CSV files (.csv) are supported");
        }
        try {
            return CsvUtil.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw BizException.of(ResultCode.SYSTEM_ERROR, "Failed to read file: " + e.getMessage());
        }
    }

    /** Header normalization: lowercase/trim -> column name to index */
    private Map<String, Integer> headerIndex(String[] cells, List<String> names) {
        Map<String, Integer> idx = new HashMap<>();
        for (int c = 0; c < cells.length; c++) {
            String h = cells[c] == null ? "" : cells[c].trim().toLowerCase();
            if (names.contains(h) && !idx.containsKey(h)) {
                idx.put(h, c);
            }
        }
        return idx;
    }

    private String cellAt(String[] cells, Integer idx) {
        if (idx == null || idx < 0 || idx >= cells.length) {
            return null;
        }
        return cells[idx];
    }

    private Integer parseIntCell(String value, int def) {
        if (StringUtils.isBlank(value)) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** CSV value typing: numbers -> BigDecimal, true/false -> Boolean, others as-is strings (usable as run inputs) */
    private Object typedValue(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return v;
        }
        if ("true".equalsIgnoreCase(v)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(v)) {
            return Boolean.FALSE;
        }
        if (v.matches("-?\\d{1,15}")) {
            try {
                return new BigDecimal(v);
            } catch (NumberFormatException ignore) {
                return v;
            }
        }
        if (v.matches("-?\\d+\\.\\d+")) {
            try {
                return new BigDecimal(v);
            } catch (NumberFormatException ignore) {
                return v;
            }
        }
        return v;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.of(ResultCode.SYSTEM_ERROR, "Data serialization failed: " + e.getMessage());
        }
    }

    private String resultText(String resultType) {
        if (resultType == null) {
            return null;
        }
        switch (resultType) {
            case "1":
                return "Pass";
            case "2":
                return "Reject";
            case "3":
                return "Manual Review";
            default:
                return resultType;
        }
    }

    /** Hit rule summary: ruleName(result[=value]), joined by semicolons */
    private String hitSummary(EngineApiRsp.EngineData data) {
        if (data.getHitRules() == null || data.getHitRules().isEmpty()) {
            return "";
        }
        return data.getHitRules().stream()
                .map(h -> {
                    String text = h.getName() + "(" + resultText(h.getResultType());
                    if (h.getValue() != null) {
                        text = text + "=" + h.getValue();
                    }
                    return text + ")";
                })
                .collect(Collectors.joining("; "));
    }

    // ===== 5. Template download =====

    @Override
    public void downloadFieldTemplate(HttpServletResponse response) {
        List<String> lines = new ArrayList<>();
        lines.add("field_en,field_cn,value_type,is_output,catalog");
        lines.add("age,Age,1,1,Basic Info");
        lines.add("income,Annual Income,4,1,Income & Debt");
        lines.add("city,City,2,0,Basic Info");
        lines.add("# value_type: 1 number / 2 string / 3 enum / 4 decimal; is_output: 0 no / 1 yes");
        lines.add("# catalog: field category name (optional; must be an existing category in field management; empty = uncategorized)");
        lines.add("# field_en required: letters/digits/underscore starting with a letter; existing rows update type and name");
        try {
            CsvDownloadUtil.write(response, "Indicator Field Import Template", lines);
        } catch (IOException e) {
            throw BizException.of(ResultCode.SYSTEM_ERROR, "Template download failed: " + e.getMessage());
        }
    }

    @Override
    public void downloadDataTemplate(String engineCode, String keyField, boolean example,
                                     HttpServletResponse response) {
        String key = StringUtils.isBlank(keyField) ? "uid" : keyField.trim();
        // Indicator columns come from the field dictionary visible to the current user (consistent with import validation)
        LambdaQueryWrapper<Field> fw = new LambdaQueryWrapper<Field>()
                .eq(Field::getIsOutput, 1)
                .orderByAsc(Field::getId)
                .last("LIMIT 50");
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            fw.in(Field::getOrganId, organs);
        }
        List<Field> fields = fieldMapper.selectList(fw);

        List<String> header = new ArrayList<>();
        header.add(key);
        for (Field f : fields) {
            header.add(f.getFieldEn());
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", header));
        if (example && !fields.isEmpty()) {
            List<String> sample = new ArrayList<>();
            sample.add("U0001");
            for (Field f : fields) {
                sample.add(CsvUtil.escape(sampleValue(f)));
            }
            lines.add(String.join(",", sample));
        }
        lines.add("# First column is the user key (" + key + "); remaining columns are indicator English names; indicators must be registered in the field dictionary");
        if (StringUtils.isNotBlank(engineCode)) {
            lines.add("# Target engine: " + engineCode + "; a task allows at most " + MAX_ROWS + " rows");
        }
        try {
            String name = "User Indicator Data Import Template" + (fields.isEmpty() ? "" : "_" + fields.size() + " cols");
            CsvDownloadUtil.write(response, name, lines);
        } catch (IOException e) {
            throw BizException.of(ResultCode.SYSTEM_ERROR, "Template download failed: " + e.getMessage());
        }
    }

    /** Generate a sample value by field type */
    private String sampleValue(Field f) {
        Integer type = f.getValueType();
        if (type == null) {
            return "Sample";
        }
        switch (type) {
            case 1:
                return "100";
            case 4:
                return "100.00";
            case 3:
                return "EnumValue";
            default:
                return "Sample";
        }
    }

}
