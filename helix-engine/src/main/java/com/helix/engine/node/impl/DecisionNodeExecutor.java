package com.helix.engine.node.impl;

import com.helix.engine.config.EngineSnapshot;
import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.node.NodeExecutor;
import com.helix.engine.node.NodeTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Decision node executor.
 *
 * <p>Real structure of {@code node_json} (quota decision as an example):
 * <pre>
 * {
 *   "inputs": 1,
 *   "condition_type": 1,
 *   "input":  [ {"field_code":"f_PRE_APPROVED_LIMIT", ...} ],
 *   "output": {"field_code":"f_final_result", "field_name":"decision and rate"},
 *   "conditions": [
 *      { "result":"1000,13",
 *        "formula":[ {"field_code":"f_PRE_APPROVED_LIMIT","operator":">","result":"0","sign":"and"},
 *                    {"field_code":"f_PRE_APPROVED_LIMIT","operator":"<=","result":"1000"} ] },
 *      ...
 *   ]
 * }
 * </pre>
 * </p>
 *
 * <p><b>Semantics</b>: the {@code result} after a condition hit is a <b>business code</b>
 * (e.g. {@code "1000,13"} meaning limit 1000, rate 13%), not a "pass/reject" flag.
 * Therefore this executor only writes the result into the output field and does
 * <b>not</b> set the decision conclusion from it.</p>
 *
 * <p>Inline conditions are compiled at <b>load time</b> by
 * {@code EngineSnapshotLoader#compileInlineDecision} into a synthetic decision table
 * (FIRST policy + AST conditions); the executor uniformly goes through {@link #executeTable}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionNodeExecutor implements NodeExecutor {

    private final ObjectMapper objectMapper;

    @Override
    public int supportType() {
        return NodeTypes.DECISION;
    }

    @Override
    public void execute(DecisionContext ctx, EngineNode node) {
        DecisionContext.NodeTrace trace = newTrace(node);

        if (StringUtils.isBlank(node.getNodeJson())) {
            trace.setMessage("Decision node not configured");
            ctx.getTraces().add(trace);
            return;
        }

        // Config lookup goes through the publish channel hit by this request (gray/shadow each use their own artifact).
        // Whether "referenced decision table" or "inline conditions",
        // both were compiled into the channel's dtMap at load time (inline as a synthetic decision table)
        // and consumed uniformly here.
        com.helix.engine.config.EngineSnapshot.Channel channel = ctx.getChannel();
        if (channel == null) {
            // Defensive: the orchestrator blocks channel-less requests before runFlow; normally unreachable
            trace.setMessage("Decision node has no available channel configuration");
            ctx.getTraces().add(trace);
            return;
        }
        com.helix.engine.config.EngineSnapshot.DecisionTableConfig dt =
                channel.getDecisionTableConfig(node.getNodeCode());
        if (dt == null) {
            // Referenced table missing/disabled, or all inline conditions broken (warned at load time)
            trace.setMessage("Decision node not configured");
            ctx.getTraces().add(trace);
            return;
        }

        executeTable(dt, node, ctx, trace);
        ctx.getTraces().add(trace);
    }

    /**
     * Decision table evaluation: adjudicate by hit policy; hit-row outputs are written
     * into the node's output field.
     *
     * <p>FIRST: the first hit row takes effect (default policy);
     * ALL: all hit rows take effect in row order (later rows overwrite the same output field),
     * and row scores accumulate; other policies are treated as FIRST.</p>
     */
    private void executeTable(EngineSnapshot.DecisionTableConfig dt,
                              EngineNode node, DecisionContext ctx,
                              DecisionContext.NodeTrace trace) {
        boolean all = "ALL".equalsIgnoreCase(dt.getHitPolicy());
        String outputField = resolveOutputField(node);
        EngineSnapshot.DecisionTableConfig.TableRow hitRow = null;
        int matchedCount = 0;

        for (EngineSnapshot.DecisionTableConfig.TableRow row : dt.getRows()) {
            boolean hit = row.getCondition() == null
                    || com.helix.engine.rule.ast.ConditionEvaluator.match(row.getCondition(), ctx.getVariables());
            if (!hit) {
                continue;
            }
            matchedCount++;
            if (!all && hitRow == null) {
                hitRow = row;
                break;
            }
            if (all) {
                applyRow(row, outputField, ctx);
                trace.getHitDetails().add("#" + row.getRowNo() + " " + row.getExpression() + " → " + row.getResultValue());
            }
        }

        if (!all && hitRow != null) {
            applyRow(hitRow, outputField, ctx);
            trace.getHitDetails().add("#" + hitRow.getRowNo() + " " + hitRow.getExpression() + " → " + hitRow.getResultValue());
        }

        if (matchedCount == 0) {
            trace.setMessage("Decision table [" + dt.getName() + "] matched no rows");
            return;
        }
        trace.setHit(true);
        trace.setMessage("Decision table [" + dt.getName() + "] hit " + matchedCount + " row(s) (" + dt.getHitPolicy() + ")");
    }

    /** Row takes effect: output value written to the output field; row conclusion/score applied per config */
    private void applyRow(EngineSnapshot.DecisionTableConfig.TableRow row,
                          String outputField, DecisionContext ctx) {
        if (row.getResultValue() != null && outputField != null) {
            // result is a business code, written to the output field for downstream use
            ctx.putVar(outputField, row.getResultValue());
        }
        if ("DENY".equalsIgnoreCase(row.getResultType())) {
            ctx.setRejected(true);
            // Must overwrite the conclusion in sync: if only rejected is set without resultType,
            // a flow that hit "manual review" first (resultType="3") would keep conclusion "3",
            // and normalizeResultType would downgrade the rejection to manual review
            // (a rejected customer routed to manual review, distorting the conclusion).
            // Keeps the same standard as PolicyNodeExecutor's DENY aggregation (rejected ⇔ resultType="2").
            ctx.setResultType(String.valueOf(com.helix.engine.rule.ast.ResultType.DENY.legacyCode()));
        }
        if (row.getScoreValue() != null) {
            ctx.addScore(row.getScoreValue());
        }
    }

    /** Read output.field_code from the node's node_json; return null when missing */
    private String resolveOutputField(EngineNode node) {
        try {
            return objectMapper.readTree(node.getNodeJson())
                    .path("output").path("field_code").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private DecisionContext.NodeTrace newTrace(EngineNode node) {
        DecisionContext.NodeTrace t = new DecisionContext.NodeTrace();
        t.setNodeId(node.getNodeId());
        t.setNodeCode(node.getNodeCode());
        t.setNodeName(node.getNodeName());
        t.setNodeType(node.getNodeType());
        return t;
    }
}
