package com.helix.engine.rule.ast;

import com.helix.engine.entity.RuleConditionEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Condition AST assembler: rebuilds the adjacency list of {@code t_rule_condition}
 * into a {@link Condition} tree.
 *
 * <p>Executed once at snapshot load time; the in-memory tree is used at execution time.</p>
 *
 * <h3>Robustness strategy</h3>
 * <p>The overarching principle is to <b>never produce a condition wider (easier to hit)
 * than configured</b>: widening makes reject rules pass wrongly and scoring rules
 * over-score -- a silent behavioral error; narrowing only manifests as a rule not
 * hitting (missed rejection), a controllable degradation direction.</p>
 * <ul>
 *   <li>Illegal node type -> give up entirely, return null (rather the rule not hit than
 *       produce a wrong condition);</li>
 *   <li>A child condition fails to compile (illegal operator/field) -> use <b>polarity</b>
 *       to decide whether dropping the child widens the condition:
 *       if it widens (positive AND / negative OR) -> give up the whole rule condition;
 *       if it only narrows (positive OR / negative AND) -> keep the valid children and warn.
 *       See {@link #build};</li>
 *   <li>Depth exceeded -> give up, preventing malicious/misconfigured deep nesting
 *       from overflowing the evaluation stack;</li>
 *   <li>Cycle detection -> access markers prevent infinite recursion;</li>
 *   <li>Orphan nodes (missing parent) -> become root candidates, never lost.</li>
 * </ul>
 */
@Slf4j
public final class AstAssembler {

    /** Maximum condition-tree depth; beyond it the rule condition is abandoned */
    public static final int MAX_DEPTH = 10;

    private static final int NODE_LEAF = 1;
    private static final int NODE_AND = 2;
    private static final int NODE_OR = 3;
    private static final int NODE_NOT = 4;

    private AstAssembler() {
    }

    /**
     * Assemble the condition tree of a single rule.
     *
     * @param rows all condition rows of the rule (any order)
     * @return AST root node; null when no valid conditions exist
     */
    public static Condition assemble(List<RuleConditionEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        // Group by parent; siblings sorted by sortNo
        Map<Long, List<RuleConditionEntity>> byParent = new HashMap<Long, List<RuleConditionEntity>>();
        for (RuleConditionEntity r : rows) {
            Long p = r.getParentId();
            List<RuleConditionEntity> list = byParent.get(p);
            if (list == null) {
                list = new ArrayList<RuleConditionEntity>();
                byParent.put(p, list);
            }
            list.add(r);
        }
        Comparator<RuleConditionEntity> cmp = new Comparator<RuleConditionEntity>() {
            @Override
            public int compare(RuleConditionEntity a, RuleConditionEntity b) {
                int sa = a.getSortNo() == null ? 0 : a.getSortNo();
                int sb = b.getSortNo() == null ? 0 : b.getSortNo();
                return Integer.compare(sa, sb);
            }
        };
        for (List<RuleConditionEntity> list : byParent.values()) {
            Collections.sort(list, cmp);
        }

        List<RuleConditionEntity> roots = byParent.get(null);
        if (roots == null || roots.isEmpty()) {
            // No explicit root: treat rows whose parent is absent from this group as roots, avoiding total loss
            roots = findImplicitRoots(rows, byParent);
            if (roots.isEmpty()) {
                return null;
            }
        }
        if (roots.size() > 1) {
            // Multiple roots: treated as an implicit AND (fault tolerance; normal configs never do this).
            // Implicit AND is positive polarity; dropping a root widens the condition
            // -> any uncompilable root gives up the whole condition.
            List<Condition> children = new ArrayList<Condition>();
            for (RuleConditionEntity root : roots) {
                Condition c = build(root, byParent, 0, false);
                if (c == null) {
                    log.warn("Rule {} has multiple root conditions and one failed to compile; abandoning the whole rule condition",
                            root.getRuleId());
                    return null;
                }
                children.add(c);
            }
            return children.size() == 1 ? children.get(0) : new Condition.Group(Logic.AND, children);
        }
        return build(roots.get(0), byParent, 0, false);
    }

    /** Rows whose parent is not within this rule become implicit roots */
    private static List<RuleConditionEntity> findImplicitRoots(
            List<RuleConditionEntity> rows, Map<Long, List<RuleConditionEntity>> byParent) {
        List<RuleConditionEntity> roots = new ArrayList<RuleConditionEntity>();
        for (RuleConditionEntity r : rows) {
            if (r.getParentId() != null && !byParent.containsKey(r.getParentId())) {
                roots.add(r);
            }
        }
        return roots;
    }

    /**
     * Recursively build a subtree.
     *
     * @param negated whether the current node sits under an <b>odd number of NOTs</b> (negative polarity).
     *                Polarity decides whether "dropping an uncompilable child condition" widens or
     *                narrows the whole condition tree; see the class javadoc robustness strategy.
     */
    private static Condition build(RuleConditionEntity row,
                                   Map<Long, List<RuleConditionEntity>> byParent,
                                   int depth,
                                   boolean negated) {
        if (row == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            log.warn("Rule {} condition depth exceeds {}, abandoning the condition", row.getRuleId(), MAX_DEPTH);
            return null;
        }
        Integer type = row.getNodeType();
        if (type == null) {
            return null;
        }
        switch (type) {
            case NODE_LEAF: {
                if (row.getFieldCode() == null || row.getFieldCode().trim().isEmpty()) {
                    return null;
                }
                Operator op = Operator.parse(row.getOperator());
                if (op == null) {
                    log.warn("Rule {} condition {} has an unrecognized operator: {}", row.getRuleId(), row.getId(), row.getOperator());
                    return null;
                }
                return new Condition.Leaf(row.getFieldCode(), op, splitValues(op, row.getValue(),
                        row.getRuleId(), row.getId()));
            }
            case NODE_NOT: {
                List<RuleConditionEntity> kids = byParent.get(row.getId());
                if (kids == null || kids.isEmpty()) {
                    return null;
                }
                // NOT flips polarity: the widen/narrow direction of its subtree is inverted
                Condition child = build(kids.get(0), byParent, depth + 1, !negated);
                return child == null ? null : new Condition.Not(child);
            }
            case NODE_AND:
            case NODE_OR: {
                List<RuleConditionEntity> kids = byParent.get(row.getId());
                if (kids == null || kids.isEmpty()) {
                    return null;
                }
                // Would dropping an uncompilable child condition widen or narrow the tree?
                //
                // Polarity criteria (keep only when certain it does not widen):
                //   positive OR   -> dropping a child only narrows (removing B from A∨B is a subset) -> keep;
                //   negative AND  -> direction uncertain (e.g. NOT(A∧B) with a broken B becomes NOT(A);
                //                    with A=false, B=true a no-hit turns into a hit) -> give up;
                //   positive AND / negative OR -> widens -> give up.
                // I.e. only "positive OR" may keep children; everything else gives up entirely --
                // rather the rule not hit than silently widen and pass wrongly.
                boolean mayKeepChildren = !negated && type == NODE_OR;
                boolean droppingWidens = !mayKeepChildren;
                List<Condition> children = new ArrayList<Condition>();
                for (RuleConditionEntity kid : kids) {
                    Condition c = build(kid, byParent, depth + 1, negated);
                    if (c == null) {
                        if (droppingWidens) {
                            log.warn("Rule {} condition {} failed to compile (parent {}, {} group{}); "
                                            + "dropping it may widen the condition, abandoning the whole rule condition",
                                    row.getRuleId(), kid.getId(), kid.getParentId(),
                                    type == NODE_OR ? "OR" : "AND",
                                    negated ? ", under NOT" : "");
                            return null;
                        }
                        log.warn("Rule {} condition {} failed to compile (parent {}, {} group{}); child ignored; "
                                        + "under positive OR, ignoring only narrows the condition, never widens it",
                                row.getRuleId(), kid.getId(), kid.getParentId(),
                                type == NODE_OR ? "OR" : "AND",
                                negated ? ", under NOT" : "");
                        continue;
                    }
                    children.add(c);
                }
                if (children.isEmpty()) {
                    return null;
                }
                return new Condition.Group(
                        type == NODE_OR ? Logic.OR : Logic.AND, children);
            }
            default:
                log.warn("Rule {} condition {} has an illegal node type: {}", row.getRuleId(), row.getId(), type);
                return null;
        }
    }

    /**
     * Split multi-values.
     *
     * <p>Multi-value operators ({@link Operator#isMultiValue()}: IN / NOT_IN / BETWEEN) split
     * their value by comma; single-value operators keep the value whole -- commas inside their
     * value are literal content (e.g. {@code CONTAINS "a,b"}), and splitting would change semantics.</p>
     *
     * <p>Multi-values must be split uniformly at load time: {@code ConditionEvaluator.inRange}
     * requires {@code values.size() >= 2}; if the value passed as a single whole, a BETWEEN
     * condition would never hold (a missed rejection when attached to a reject rule).
     * Uniform splitting keeps both paths semantically consistent.</p>
     */
    private static List<String> splitValues(Operator op, String raw, Integer ruleId, Long condId) {
        if (raw == null) {
            return Collections.singletonList(null);
        }
        if (op == null || !op.isMultiValue()) {
            return Collections.singletonList(raw);
        }
        List<String> values = new ArrayList<String>(2);
        for (String part : raw.split(",")) {
            values.add(part.trim());
        }
        if (op == Operator.BETWEEN && values.size() < 2) {
            // BETWEEN needs both bounds: warn explicitly when only one value is configured, avoiding silent failure
            log.warn("Rule {} condition {} uses BETWEEN without two boundary values (value={}); the condition will never hold",
                    ruleId, condId, raw);
        }
        return values;
    }
}
