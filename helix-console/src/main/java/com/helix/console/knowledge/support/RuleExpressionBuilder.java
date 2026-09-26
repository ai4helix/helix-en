package com.helix.console.knowledge.support;

import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.knowledge.dto.RuleConditionDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule expression builder.
 *
 * <p>Assembles a structured condition list into readable, echo-able expression
 * text. This is the single conversion point between "structured conditions" and
 * "executable expressions", keeping the two consistent.</p>
 *
 * <p>Generation rules:
 * <ul>
 *   <li>between condition i and condition i+1, use condition i's {@code logical} (&& / ||);</li>
 *   <li>values are quoted automatically by operator semantics: numbers unquoted, strings single-quoted;</li>
 *   <li>{@code in} / {@code notIn} expand to literal collections;</li>
 *   <li>overall negation wraps the outermost layer in {@code !(...)}.</li>
 * </ul>
 * </p>
 */
@Component
public class RuleExpressionBuilder {

    /** Detects "is a numeric literal" */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    /** Operators that support multi-value comparison */
    private static final List<String> MULTI_VALUE_OPS = Arrays.asList("in", "notIn", "not_in");
    /** Operators requiring string quotes (value treated as string) */
    private static final List<String> STRING_OPS = Arrays.asList("contains", "notContains", "startsWith", "endsWith");

    /**
     * Build an expression from a condition list.
     *
     * <p><b>Bracket grouping</b>: when both {@code &&} and {@code ||} appear
     * between conditions, split the conditions by {@code ||} into "AND groups",
     * each joined by {@code &&} and wrapped in parentheses, with groups joined
     * by {@code ||}. This keeps evaluation order aligned with how the user reads
     * top-down, avoiding the semantic drift caused by JEXL giving {@code &&}
     * higher precedence than {@code ||}.</p>
     *
     * <p>Example: conditions A && B || C produce {@code (A && B) || C},
     * not the bare concatenation {@code A && B || C}.</p>
     *
     * @param conditions condition list
     * @param isNon      whether the whole expression is negated
     * @return JEXL expression; null when there are no conditions
     */
    public String build(List<RuleConditionDTO> conditions, Integer isNon) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (RuleConditionDTO c : conditions) {
            parts.add(buildOne(c));
        }

        // Connector count = condition count - 1 (the last condition's logical is always -1)
        boolean hasOr = false;
        boolean hasAnd = false;
        for (int i = 0; i < conditions.size() - 1; i++) {
            String lg = normalizeLogical(conditions.get(i).getLogical());
            if ("||".equals(lg)) {
                hasOr = true;
            } else {
                hasAnd = true;
            }
        }

        String expr;
        if (hasOr && hasAnd) {
            // Split into "AND groups" by ||; && inside a group, wrapped in parentheses
            List<String> orGroups = new ArrayList<>();
            StringBuilder andGroup = new StringBuilder(parts.get(0));
            for (int i = 0; i < conditions.size() - 1; i++) {
                String lg = normalizeLogical(conditions.get(i).getLogical());
                if ("||".equals(lg)) {
                    orGroups.add(andGroup.toString());
                    andGroup = new StringBuilder(parts.get(i + 1));
                } else {
                    andGroup.append(" && ").append(parts.get(i + 1));
                }
            }
            orGroups.add(andGroup.toString());

            List<String> wrapped = new ArrayList<>();
            for (String g : orGroups) {
                // No parentheses when a group has a single condition
                wrapped.add(g.contains(" && ") ? "(" + g + ")" : g);
            }
            expr = String.join(" || ", wrapped);
        } else {
            // Only a single connector kind: plain concatenation, no parentheses needed
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                sb.append(parts.get(i));
                if (i < parts.size() - 1) {
                    sb.append(' ').append(normalizeLogical(conditions.get(i).getLogical())).append(' ');
                }
            }
            expr = sb.toString();
        }

        if (isNon != null && isNon == 1) {
            expr = "!(" + expr + ")";
        }
        return expr;
    }

    /** Build a single condition fragment */
    private String buildOne(RuleConditionDTO c) {
        String var = resolveVariable(c);
        String op = StringUtils.trimToEmpty(c.getOperator());
        if (op.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Condition operator must not be blank");
        }
        String rawValue = StringUtils.trimToEmpty(c.getFieldValue());

        // Multi-value operators: in / notIn
        if (MULTI_VALUE_OPS.contains(op)) {
            List<String> items = new ArrayList<>();
            for (String v : rawValue.split(",")) {
                String t = v.trim();
                if (!t.isEmpty()) {
                    items.add(literal(t));
                }
            }
            if (items.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Value list of field " + var + " is empty");
            }
            // JEXL expresses set membership with .contains()
            String joined = "[" + String.join(", ", items) + "]";
            return "notIn".equals(op) || "not_in".equals(op)
                    ? "!(" + joined + ".contains(" + var + "))"
                    : joined + ".contains(" + var + ")";
        }

        // String function operators
        if (STRING_OPS.contains(op)) {
            String v = "'" + escape(rawValue) + "'";
            switch (op) {
                case "contains":
                    return var + " =~ '.*" + escape(rawValue) + ".*'";
                case "notContains":
                    return "!(" + var + " =~ '.*" + escape(rawValue) + ".*')";
                case "startsWith":
                    return var + " =~ '^" + escape(rawValue) + ".*'";
                case "endsWith":
                    return var + " =~ '.*" + escape(rawValue) + "$'";
                default:
                    return var + " == " + v;
            }
        }

        // Null checks
        if ("isNull".equals(op)) {
            return var + " == null";
        }
        if ("notNull".equals(op)) {
            return var + " != null";
        }

        String value = literal(rawValue);
        switch (op) {
            case "==":
            case "!=":
            case ">":
            case ">=":
            case "<":
            case "<=":
                return var + " " + op + " " + value;
            default:
                // Unknown operators pass through, leaving room for extension
                return var + " " + op + " " + value;
        }
    }

    /**
     * Resolve the expression variable name from fieldId.
     * fieldId convention: {@code "{fieldId}|{fieldEnName}"}; without a pipe it is used as the variable name as-is.
     */
    private String resolveVariable(RuleConditionDTO c) {
        if (StringUtils.isNotBlank(c.getFieldEn())) {
            return c.getFieldEn().trim();
        }
        String fieldId = StringUtils.trimToEmpty(c.getFieldId());
        if (fieldId.contains("|")) {
            String[] parts = fieldId.split("\\|", 2);
            if (StringUtils.isNotBlank(parts[1])) {
                return parts[1].trim();
            }
        }
        if (StringUtils.isBlank(fieldId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Condition is missing a field identifier");
        }
        return fieldId;
    }

    /** Convert a value to a JEXL literal: numbers bare, everything else single-quoted */
    private String literal(String value) {
        if (NUMBER_PATTERN.matcher(value).matches()) {
            return value;
        }
        if ("true".equals(value) || "false".equals(value) || "null".equals(value)) {
            return value;
        }
        return "'" + escape(value) + "'";
    }

    /** Escape single quotes to prevent expression injection */
    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** Normalize logical connectors */
    private String normalizeLogical(String logical) {
        if (StringUtils.isBlank(logical)) {
            return "&&";
        }
        String l = logical.trim();
        if ("and".equalsIgnoreCase(l) || "&&".equals(l)) {
            return "&&";
        }
        if ("or".equalsIgnoreCase(l) || "||".equals(l)) {
            return "||";
        }
        // The last condition may be marked -1; treat as && (never appended anyway)
        return "&&";
    }
}
