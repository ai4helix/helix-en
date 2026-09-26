package com.helix.engine.node.impl;

import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.node.NodeExecutor;
import com.helix.engine.node.NodeTypes;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Scorecard node executor.
 *
 * <p>Scorecard dimensions JSON is batch-prefetched and pre-parsed at snapshot load time;
 * at execution time it only does "fetch variable -> bin matching -> sum",
 * with zero DB queries and zero parsing.</p>
 *
 * <p>Bin semantics: [min, max); a field with no value is scored 0 and recorded in the trace.</p>
 */
@Slf4j
@Component
public class ScorecardNodeExecutor implements NodeExecutor {

    @Override
    public int supportType() {
        return NodeTypes.SCORECARD;
    }

    @Override
    public void execute(DecisionContext ctx, EngineNode node) {
        DecisionContext.NodeTrace trace = DecisionContext.newTrace(node);

        // Config lookup goes through the publish channel hit by this request (gray/shadow each use their own artifact)
        com.helix.engine.config.EngineSnapshot.Channel channel = ctx.getChannel();
        com.helix.engine.config.EngineSnapshot.ScorecardConfig config =
                channel == null ? null : channel.getScorecardConfig(node.getNodeCode());

        if (config == null) {
            trace.setMessage("Scorecard node not configured or scorecard does not exist");
            ctx.getTraces().add(trace);
            return;
        }
        if (!config.isConfigured()) {
            trace.setMessage("Scorecard [" + config.getName() + "] has no dimensions configured");
            ctx.getTraces().add(trace);
            return;
        }

        int total = evaluate(config, ctx, trace);
        if (total != 0) {
            ctx.addScore(total);
            trace.setScoreDelta(total);
        }
        trace.setHit(true);
        trace.setMessage("Scorecard [" + config.getName() + "] scored " + total + ", total " + ctx.getScore());
        ctx.getTraces().add(trace);
    }

    /** Score each dimension */
    private int evaluate(com.helix.engine.config.EngineSnapshot.ScorecardConfig config,
                         DecisionContext ctx, DecisionContext.NodeTrace trace) {
        int total = 0;
        try {
            for (JsonNode dim : config.getDims()) {
                String field = dim.path("field").asText(null);
                if (field == null) {
                    continue;
                }
                Object raw = ctx.getVar(field);
                if (raw == null) {
                    trace.getHitDetails().add(field + " has no value, scored 0");
                    continue;
                }
                // Non-numeric values share the same semantics as "no value" (skip the dimension) --
                // prevents text fields from silently falling into the 0-score bin.
                Double value = toDoubleOrNull(raw);
                if (value == null) {
                    trace.getHitDetails().add(field + "=" + raw + " non-numeric value, scored 0");
                    continue;
                }
                int dimScore = matchBin(dim.path("bins"), value);
                total += dimScore;
                trace.getHitDetails().add(field + "=" + raw + " -> " + dimScore + " pts");
            }
        } catch (Exception e) {
            log.warn("Scorecard {} evaluation failed: {}", config.getName(), e.getMessage());
        }
        return total;
    }

    /** Bin range is [min, max) */
    private int matchBin(JsonNode bins, double value) {
        if (!bins.isArray()) {
            return 0;
        }
        for (JsonNode bin : bins) {
            double min = bin.path("min").asDouble(Double.NEGATIVE_INFINITY);
            double max = bin.path("max").asDouble(Double.POSITIVE_INFINITY);
            if (value >= min && value < max) {
                return bin.path("score").asInt(0);
            }
        }
        return 0;
    }

    /** Parse a number; return null for non-numeric (caller treats it as "no value", never falls into the 0-score bin) */
    private Double toDoubleOrNull(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
