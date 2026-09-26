package com.helix.engine.result;

import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.DecisionLogEntity;
import com.helix.engine.mapper.DecisionLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decision log persistence.
 *
 * <p>Hit details and node traces are inlined as JSON in the main record
 * ({@code hits_json}/{@code traces_json}); one decision = <b>one insert</b>,
 * combined with the orchestrator's asynchronous persistence.</p>
 *
 * <p>JSON fields retain full information: hit details contain rule id/code/name/conclusion/
 * score/expression snapshot (enabling historical hit-rate statistics per rule); traces contain nodes and
 * costs in execution order (the decision process is replayable); the full variable snapshot supports value-by-value replay.</p>
 *
 * <p>Persistence strategy: <b>logs are observation data and do not constitute an availability dependency of the
 * decision chain</b> — any failure only affects log completeness, never the decision response.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionLogWriter {

    private final DecisionLogMapper logMapper;
    private final ObjectMapper objectMapper;

    public void write(DecisionContext ctx, String resultTypeText, long totalCostMs) {
        try {
            DecisionLogEntity logRow = new DecisionLogEntity();
            logRow.setTraceId(ctx.getTraceId());
            logRow.setEngineId(ctx.getEngineId());
            logRow.setEngineCode(ctx.getEngineCode());
            // Tenant ownership follows the engine
            logRow.setOrganId(ctx.getOrganId() == null ? 0L : ctx.getOrganId());
            logRow.setVersionId(ctx.getVersionId());
            logRow.setPublishId(ctx.getPublishId());
            logRow.setShadow(ctx.isShadowRun() ? 1 : 0);
            logRow.setShadowOf(ctx.getShadowOf());
            logRow.setPid(ctx.getPid());
            logRow.setUid(ctx.getUid());
            logRow.setResultType(resultCode(resultTypeText));
            logRow.setTotalScore(ctx.getScore());
            logRow.setRejected(ctx.isRejected() ? 1 : 0);
            logRow.setCostMs((int) Math.min(totalCostMs, Integer.MAX_VALUE));
            logRow.setInputJson(toJson(ctx.getVariables()));
            logRow.setHitsJson(toJson(buildHits(ctx)));
            logRow.setTracesJson(toJson(ctx.getTraces()));
            logRow.setCreatedTime(LocalDateTime.now());
            logRow.setDeleted(0);
            // Main record + detail/trace JSON, one insert
            logMapper.insert(logRow);
        } catch (Exception e) {
            log.error("Failed to persist decision log, traceId={}", ctx.getTraceId(), e);
        }
    }

    /**
     * Hit details → JSON structure (field names follow {@code t_decision_hit} column names;
     * the display side parses by these fields).
     */
    private List<Map<String, Object>> buildHits(DecisionContext ctx) {
        List<Map<String, Object>> hits = new ArrayList<>();
        ctx.getNodeResults().forEach((nodeCode, results) -> {
            if (results == null) {
                return;
            }
            for (com.helix.engine.entity.engine.model.Result r : results) {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("nodeCode", nodeCode);
                h.put("ruleId", r.getId());
                h.put("ruleCode", r.getCode());
                h.put("ruleName", r.getName());
                h.put("resultType", r.getResultType());
                h.put("scoreValue", parseIntSafe(r.getValue()));
                h.put("expression", r.getExpression());
                hits.add(h);
            }
        });
        return hits;
    }

    /** Conclusion text → code (1 Pass / 3 Manual Review / others Reject) */
    private int resultCode(String resultTypeText) {
        if ("Pass".equals(resultTypeText) || "1".equals(resultTypeText)) {
            return 1;
        }
        if ("Manual Review".equals(resultTypeText) || "3".equals(resultTypeText)) {
            return 3;
        }
        return 2;
    }

    /** Tolerant parsing of score value: in the legacy structure the score is carried as a string */
    private Integer parseIntSafe(String v) {
        if (v == null || v.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("Failed to serialize decision log JSON: {}", e.getMessage());
            return null;
        }
    }
}
