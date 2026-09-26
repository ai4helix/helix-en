package com.helix.engine.rule.ast;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Condition AST evaluator (the single source of truth for condition semantics).
 *
 * <p>Responsible for condition evaluation with parenthesized group support;
 * changes to evaluation semantics must never alter any risk-control conclusion.</p>
 *
 * <h3>Semantic contract</h3>
 * <table border="1">
 *   <tr><th>Scenario</th><th>Behavior</th></tr>
 *   <tr><td>Missing variable (null)</td><td>{@code !=} / {@code notIn} are true; {@code isNull} is true;
 *       the rest (including comparisons, in, contains) are false</td></tr>
 *   <tr><td>Numeric comparison</td><td>If both sides convert to double, compare numerically;
 *       otherwise fall back to string comparison</td></tr>
 *   <tr><td>Empty string</td><td>Does not participate in numeric comparison (toDouble returns null)</td></tr>
 *   <tr><td>in / notIn</td><td>Values are tokenized by comma, each token compared with lenient numeric equality</td></tr>
 *   <tr><td>Evaluation error</td><td>Caught and returns false, never propagated upward</td></tr>
 * </table>
 *
 * <p>This class is stateless and safe for concurrent use.</p>
 */
@Slf4j
public final class ConditionEvaluator {

    /** Empty condition is treated as no-hit */
    private static final boolean EMPTY_LEAF_RESULT = false;

    /**
     * Cumulative count of evaluation errors.
     *
     * <p>Treating exceptions as no-hit is equivalent to <b>fail-open</b> for reject rules:
     * an occasional NPE/type-cast exception, if unobserved, manifests as
     * "systematic missed rejections with no visibility".
     * This provides a process-level cumulative counter, exposed via the status endpoint
     * for monitoring/alerting -- it should always be 0 in the normal state;
     * non-zero indicates a configuration or data issue requiring investigation.</p>
     */
    private static final java.util.concurrent.atomic.AtomicLong EVAL_ERRORS =
            new java.util.concurrent.atomic.AtomicLong(0);

    /** Cumulative evaluation error count (monitoring metric, should always be 0 in the normal state) */
    public static long getEvalErrorCount() {
        return EVAL_ERRORS.get();
    }

    private ConditionEvaluator() {
    }

    /**
     * Evaluate a condition tree.
     *
     * @param condition condition root node; when null returns {@link #EMPTY_LEAF_RESULT}
     * @param variables business variable table
     * @return whether it hits
     */
    public static boolean match(Condition condition, Map<String, Object> variables) {
        if (condition == null) {
            return EMPTY_LEAF_RESULT;
        }
        try {
            return eval(condition, variables);
        } catch (Exception e) {
            // Exceptions are treated as no-hit and do not break the whole decision flow.
            // For reject rules this is fail-open (exception = no rejection = pass), so it must be observable:
            // count it as a metric and alert when non-zero.
            EVAL_ERRORS.incrementAndGet();
            log.warn("Condition evaluation error, treated as no-hit: {}", e.getMessage());
            return false;
        }
    }

    /** Recursive evaluation */
    static boolean eval(Condition node, Map<String, Object> variables) {
        if (node == null) {
            return false;
        }
        switch (node.getKind()) {
            case LEAF:
                return matchLeaf((Condition.Leaf) node, variables);
            case NOT: {
                Condition child = ((Condition.Not) node).getChild();
                return !eval(child, variables);
            }
            case GROUP: {
                Condition.Group g = (Condition.Group) node;
                List<Condition> children = g.getChildren();
                if (children.isEmpty()) {
                    // Empty group never holds, to avoid accidental pass-through
                    return false;
                }
                if (g.getLogic() == Logic.OR) {
                    for (Condition c : children) {
                        if (eval(c, variables)) {
                            return true;
                        }
                    }
                    return false;
                }
                for (Condition c : children) {
                    if (!eval(c, variables)) {
                        return false;
                    }
                }
                return true;
            }
            default:
                return false;
        }
    }

    /**
     * Evaluate a single leaf condition.
     *
     * <p>Order of execution: handle isNull/notNull special cases first,
     * then missing variables, and only then dispatch by operator --
     * a wrong order would change the conclusion.</p>
     */
    static boolean matchLeaf(Condition.Leaf leaf, Map<String, Object> variables) {
        Operator op = leaf.getOperator();
        if (op == null) {
            // Missing operator is equivalent to "equals" (default branch)
            op = Operator.EQ;
        }

        Object raw = leaf.getField() == null ? null : variables.get(leaf.getField());

        // ---------- 1. Null-check special cases ----------
        if (op == Operator.IS_NULL) {
            return raw == null || String.valueOf(raw).isEmpty();
        }
        if (op == Operator.NOT_NULL) {
            return raw != null && !String.valueOf(raw).isEmpty();
        }

        // ---------- 2. Missing-variable semantics ----------
        // A key absent from the map reads as null: null != x is always true, null == x always false,
        // null belongs to no set (notIn hits), comparisons never hold.
        if (raw == null) {
            return op == Operator.NE || op == Operator.NOT_IN;
        }

        // ---------- 3. Multi-value operators ----------
        if (op == Operator.IN || op == Operator.NOT_IN) {
            boolean contains = containsAny(raw, leaf.getValues());
            return op == Operator.IN ? contains : !contains;
        }
        if (op == Operator.BETWEEN) {
            return inRange(raw, leaf.getValues());
        }

        // ---------- 4. Single-value operators ----------
        String expect = leaf.firstValue() == null ? "" : leaf.firstValue().trim();

        // String-type operators always compare with string semantics (never through numeric dispatch) --
        // if numeric dispatch were used, String.valueOf(double) would produce scientific notation
        // and ".0" suffixes, breaking CONTAINS/STARTS_WITH/ENDS_WITH for "numeric text" values
        // such as phone numbers / ID numbers / card numbers, and inverting NOT_CONTAINS
        // (should-reject-but-passed = missed rejection); these are exactly the fields
        // risk control uses most.
        //
        // Comparison and equality keep numeric-first ("30" and 30 should be equal; magnitudes
        // compare numerically), so only the four string operators are pulled out to compareText.
        if (isStringOperator(op)) {
            return compareText(op, String.valueOf(raw), expect);
        }

        // Numeric first: if both sides convert to numbers, compare numerically
        Double left = Operator.toDouble(raw);
        Double right = Operator.toDouble(expect);
        if (left != null && right != null) {
            return compareNumeric(op, left, right);
        }
        return compareText(op, String.valueOf(raw), expect);
    }

    /** Operators evaluated with string semantics (substring/prefix/suffix matching) */
    private static boolean isStringOperator(Operator op) {
        return op == Operator.CONTAINS || op == Operator.NOT_CONTAINS
                || op == Operator.STARTS_WITH || op == Operator.ENDS_WITH;
    }

    /**
     * Numeric comparison.
     *
     * <p>Only handles magnitude relations (EQ/NE/GT/GE/LT/LE). String-type operators were
     * dispatched early in {@link #matchLeaf} to {@link #compareText} and never reach here --
     * if branches like CONTAINS reappeared here, the
     * {@code String.valueOf(double)} scientific-notation comparison problem would return.</p>
     */
    private static boolean compareNumeric(Operator op, double l, double r) {
        switch (op) {
            case EQ: return l == r;
            case NE: return l != r;
            case GT: return l > r;
            case GE: return l >= r;
            case LT: return l < r;
            case LE: return l <= r;
            default: return false;
        }
    }

    /** String comparison */
    private static boolean compareText(Operator op, String l, String expect) {
        switch (op) {
            case EQ: return l.equals(expect);
            case NE: return !l.equals(expect);
            case GT: return l.compareTo(expect) > 0;
            case GE: return l.compareTo(expect) >= 0;
            case LT: return l.compareTo(expect) < 0;
            case LE: return l.compareTo(expect) <= 0;
            case CONTAINS: return l.contains(expect);
            case NOT_CONTAINS: return !l.contains(expect);
            case STARTS_WITH: return l.startsWith(expect);
            case ENDS_WITH: return l.endsWith(expect);
            default: return false;
        }
    }

    /**
     * Multi-value hit: any value that loosely equals is enough.
     *
     * <p>Loose equality = if both sides convert to numbers, compare numerically;
     * otherwise compare as equal strings.</p>
     */
    private static boolean containsAny(Object raw, List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String v : values) {
            if (v == null) {
                continue;
            }
            // Compatibility: a single value may also be written comma-separated
            for (String part : v.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (looseEquals(raw, t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Range check: values are [min, max], closed interval */
    private static boolean inRange(Object raw, List<String> values) {
        if (values == null || values.size() < 2) {
            return false;
        }
        Double v = Operator.toDouble(raw);
        Double min = Operator.toDouble(values.get(0));
        Double max = Operator.toDouble(values.get(1));
        if (v == null || min == null || max == null) {
            return false;
        }
        // Tolerate reversed configuration
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        return v >= lo && v <= hi;
    }

    /** Lenient numeric equality */
    private static boolean looseEquals(Object raw, String expect) {
        Double l = Operator.toDouble(raw);
        Double r = Operator.toDouble(expect);
        if (l != null && r != null) {
            return l.doubleValue() == r.doubleValue();
        }
        return String.valueOf(raw).equals(expect);
    }

    /**
     * Whether ALL conditions in a set hit (used by the rule-set layer).
     *
     * <p>An empty set counts as a hit -- a rule with no conditions applies unconditionally.</p>
     */
    public static boolean matchAll(Collection<Condition> conditions, Map<String, Object> variables) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (Condition c : conditions) {
            if (!match(c, variables)) {
                return false;
            }
        }
        return true;
    }
}
