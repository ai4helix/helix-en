package com.helix.engine.node.impl;

import com.helix.engine.config.EngineSnapshot;
import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.RuleEntity;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.node.NodeExecutor;
import com.helix.engine.node.NodeTypes;
import com.helix.engine.rule.ast.Condition;
import com.helix.engine.rule.ast.ConditionEvaluator;
import com.helix.engine.rule.ast.ResultType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Policy rule node executor (pure Java evaluation).
 *
 * <p>Rule conditions are compiled into in-memory ASTs at snapshot load time;
 * this executor evaluates them one by one in memory.</p>
 *
 * <p>Execution characteristics: zero DB queries, zero JSON parsing, zero compilation --
 * condition trees are fully assembled at snapshot load time.</p>
 *
 * <h3>Result semantics</h3>
 * <ul>
 *   <li><b>Reject type</b>: by default any hit rejects; when node_json declares serial
 *       (isSerial=1), the hit count must reach the total rule count to reject;</li>
 *   <li><b>Score type</b>: the accumulated absolute score must reach the threshold
 *       to be added to the total.</li>
 * </ul>
 */
@Slf4j
@Component
public class PolicyNodeExecutor implements NodeExecutor {

    @Override
    public int supportType() {
        return NodeTypes.POLICY;
    }

    @Override
    public void execute(DecisionContext ctx, EngineNode node) {
        DecisionContext.NodeTrace trace = DecisionContext.newTrace(node);

        // Config lookup goes through the publish channel hit by this request (gray/shadow each use their own artifact)
        EngineSnapshot.Channel channel = ctx.getChannel();
        EngineSnapshot.PolicyConfig config =
                channel == null ? null : channel.getPolicyConfig(node.getNodeCode());

        if (config == null) {
            trace.setMessage("Node has no policy config (channel not hit)");
            ctx.getTraces().add(trace);
            return;
        }
        if (!config.hasRules()) {
            trace.setMessage("Node has no rules configured or none enabled");
            ctx.getTraces().add(trace);
            return;
        }

        // ---------- Evaluate rule by rule ----------
        List<EngineSnapshot.RulePlan> hits = new ArrayList<>();
        for (EngineSnapshot.RulePlan plan : config.getRulePlans()) {
            if (matches(plan, ctx.getVariables())) {
                hits.add(plan);
            }
        }
        if (hits.isEmpty()) {
            trace.setMessage("No rule hit");
            ctx.getTraces().add(trace);
            return;
        }

        summarize(ctx, trace, config, hits);
        ctx.getNodeResults().put(node.getNodeCode(), toResults(hits));
        ctx.getTraces().add(trace);
    }

    /**
     * Whether a single rule hits.
     *
     * <p>A null condition is treated as always true (unconditional rule), but rule loading
     * already filtered out unconditional rules, so this path only appears on exception paths.</p>
     */
    private boolean matches(EngineSnapshot.RulePlan plan, Map<String, Object> variables) {
        Condition cond = plan.getCondition();
        if (cond == null) {
            return true;
        }
        try {
            return ConditionEvaluator.match(cond, variables);
        } catch (Exception e) {
            // A single rule evaluation error is treated as no-hit, not affecting other rules
            log.warn("Rule {} evaluation error: {}", plan.getCode(), e.getMessage());
            return false;
        }
    }

    /**
     * Summarize by strongly typed results and write into the decision context.
     */
    private void summarize(DecisionContext ctx, DecisionContext.NodeTrace trace,
                           EngineSnapshot.PolicyConfig config,
                           List<EngineSnapshot.RulePlan> hits) {
        List<EngineSnapshot.RulePlan> denyHits = new ArrayList<>();
        List<EngineSnapshot.RulePlan> manualHits = new ArrayList<>();
        List<EngineSnapshot.RulePlan> scoreHits = new ArrayList<>();

        for (EngineSnapshot.RulePlan plan : hits) {
            trace.getHitDetails().add(plan.getName() + "(" + plan.getCode() + ")");
            trace.setHit(true);
            ResultType rt = plan.getResultType();
            if (rt == ResultType.DENY) {
                denyHits.add(plan);
            } else if (rt == ResultType.MANUAL) {
                manualHits.add(plan);
            } else if (rt.isScore()) {
                scoreHits.add(plan);
            }
        }

        // ---------- Reject rule aggregation ----------
        if (!denyHits.isEmpty()) {
            int hitCount = denyHits.size();
            int totalCount = config.getDenyTotal();
            if (config.isDenySerial()) {
                // Serial mode: the hit count must reach "the total number of actually loaded reject rules" to reject.
                //
                // denyTotal counts the real DENY rules present in the rule set;
                // as long as we reach here (denyHits non-empty), totalCount >= 1 is guaranteed.
                trace.setMessage("Reject rules serial hit " + hitCount + "/" + totalCount);
                if (hitCount >= totalCount) {
                    ctx.setRejected(true);
                    ctx.setResultType(String.valueOf(ResultType.DENY.legacyCode()));
                }
            } else {
                trace.setMessage("Hit reject rule(s): " + hitCount);
                ctx.setRejected(true);
                ctx.setResultType(String.valueOf(ResultType.DENY.legacyCode()));
            }
        }

        // ---------- Manual review ----------
        if (!manualHits.isEmpty()) {
            trace.setMessage(append(trace.getMessage(), "Hit manual review rule(s): " + manualHits.size()));
            // Promote to manual only when not rejected; rejection has higher priority
            if (!ctx.isRejected()) {
                ctx.setResultType(String.valueOf(ResultType.MANUAL.legacyCode()));
                // Set isManualReview in sync, keeping state consistent with the conclusion
                ctx.setManualReview(true);
            }
        }

        // ---------- Score rule aggregation ----------
        if (!scoreHits.isEmpty()) {
            int delta = 0;
            for (EngineSnapshot.RulePlan plan : scoreHits) {
                Integer v = plan.getScoreValue();
                if (v == null) {
                    continue;
                }
                // Add/subtract type decides the sign, tolerating values that already carry a sign
                delta += (plan.getResultType() == ResultType.SUB_SCORE) ? -Math.abs(v) : v;
            }
            double threshold = config.getAddSubThreshold();
            if (Math.abs(delta) >= threshold) {
                ctx.addScore(delta);
                trace.setScoreDelta(delta);
                trace.setMessage(append(trace.getMessage(),
                        "Score rule(s) hit: " + scoreHits.size() + ", accumulated " + delta));
            } else {
                trace.setMessage(append(trace.getMessage(),
                        "Score accumulated " + delta + " below threshold " + threshold + ", not counted"));
            }
        }
    }

    /**
     * Convert hit rules into result objects.
     *
     * <p>This structure is kept so that {@code DecisionContext.nodeResults}'s external
     * shape is unchanged, minimizing coupled changes for callers (result endpoint, frontend).</p>
     */
    private List<com.helix.engine.entity.engine.model.Result> toResults(
            List<EngineSnapshot.RulePlan> hits) {
        List<com.helix.engine.entity.engine.model.Result> list = new ArrayList<>(hits.size());
        for (EngineSnapshot.RulePlan plan : hits) {
            com.helix.engine.entity.engine.model.Result r =
                    new com.helix.engine.entity.engine.model.Result();
            r.setId(plan.getRuleId());
            r.setCode(plan.getCode());
            r.setName(plan.getName());
            r.setResultType(String.valueOf(plan.getResultType().legacyCode()));
            // Expression snapshot: freezes the condition at hit time for decision log (hits_json) audit
            r.setExpression(com.helix.engine.rule.ast.ConditionExpressions.render(plan.getCondition()));
            if (plan.getScoreValue() != null) {
                // Score carried as string, decoupling from downstream parse changes
                r.setValue(String.valueOf(plan.getScoreValue()));
            }
            list.add(r);
        }
        return list;
    }

    /** Name fallback: rule name first, then code (name comes from the plan directly) */
    @SuppressWarnings("unused")
    private String resolveName(EngineSnapshot.RulePlan plan,
                               Map<Integer, RuleEntity> ruleById) {
        if (StringUtils.isNotBlank(plan.getName())) {
            return plan.getName();
        }
        RuleEntity rule = plan.getRuleId() == null ? null : ruleById.get(plan.getRuleId());
        if (rule != null && StringUtils.isNotBlank(rule.getName())) {
            return rule.getName();
        }
        return plan.getCode() == null ? "Unknown rule" : plan.getCode();
    }

    private String append(String base, String extra) {
        return base == null ? extra : base + "; " + extra;
    }
}
