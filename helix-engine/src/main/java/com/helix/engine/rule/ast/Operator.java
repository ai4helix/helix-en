package com.helix.engine.rule.ast;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Condition operators.
 *
 * <p>Condition operators come as strings from configuration JSON; a typo can only manifest
 * at runtime as "condition never hits", which is extremely costly to troubleshoot --
 * hence parsing is centralized into this enum.</p>
 *
 * <p>Each operator carries its own evaluation function, avoiding a long switch in the evaluator.</p>
 */
public enum Operator {

    /** Equals */
    EQ("Equals", false, (a, b) -> compare(a, b) == 0),

    /** Not equals (true when variable is missing) */
    NE("Not equals", false, (a, b) -> compare(a, b) != 0),

    /** Greater than */
    GT("Greater than", false, (a, b) -> compare(a, b) > 0),

    /** Greater than or equal */
    GE("Greater than or equal", false, (a, b) -> compare(a, b) >= 0),

    /** Less than */
    LT("Less than", false, (a, b) -> compare(a, b) < 0),

    /** Less than or equal */
    LE("Less than or equal", false, (a, b) -> compare(a, b) <= 0),

    /** In (multi-value, true when any value hits) */
    IN("In", true, (a, b) -> false),

    /** Not in (multi-value) */
    NOT_IN("Not in", true, (a, b) -> false),

    /** String contains */
    CONTAINS("Contains", false, (a, b) -> str(a).contains(str(b))),

    /** String not contains */
    NOT_CONTAINS("Not contains", false, (a, b) -> !str(a).contains(str(b))),

    /** Starts with */
    STARTS_WITH("Starts with", false, (a, b) -> str(a).startsWith(str(b))),

    /** Ends with */
    ENDS_WITH("Ends with", false, (a, b) -> str(a).endsWith(str(b))),

    /** Range [min, max] closed interval */
    BETWEEN("Between", true, (a, b) -> false),

    /** Is null (null or empty string) */
    IS_NULL("Is null", false, (a, b) -> a == null || str(a).isEmpty()),

    /** Not null */
    NOT_NULL("Not null", false, (a, b) -> a != null && !str(a).isEmpty());

    private final String label;
    /** Whether multiple comparison values are required (IN/NOT_IN/BETWEEN) */
    private final boolean multiValue;
    /** Single-value binary predicate; not effective for multi-value operators */
    private final BiPredicate<Object, Object> single;

    Operator(String label, boolean multiValue, BiPredicate<Object, Object> single) {
        this.label = label;
        this.multiValue = multiValue;
        this.single = single;
    }

    public String getLabel() {
        return label;
    }

    public boolean isMultiValue() {
        return multiValue;
    }

    public BiPredicate<Object, Object> getSingle() {
        return single;
    }

    /**
     * Parse an operator by name (case-insensitive, tolerant of underscore/hyphen spellings).
     *
     * @return null when unrecognized; the caller decides whether to error or skip
     */
    public static Operator parse(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String raw = name.trim();
        // Symbolic shorthands from legacy configs and the frontend
        switch (raw) {
            case "=": case "==": return EQ;
            case "!=": case "<>": return NE;
            case ">": return GT;
            case ">=": return GE;
            case "<": return LT;
            case "<=": return LE;
            default: break;
        }
        // Normalize to "uppercase without separators" before matching, tolerating
        // camelCase / snake_case / kebab-case: startsWith, STARTS_WITH and starts-with
        // must all resolve to STARTS_WITH.
        String key = normalize(raw);
        for (Operator op : values()) {
            if (normalize(op.name()).equals(key)) {
                return op;
            }
        }
        return null;
    }

    /** Strip underscores/hyphens and uppercase, used for lenient operator-name matching */
    private static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                continue;
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    /**
     * Compare two values.
     *
     * <p>Numeric values are compared as double first; if either side is non-numeric,
     * fall back to string comparison. When null participates: null equals null,
     * null is never equal to any other value.</p>
     */
    static int compare(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null || b == null) {
            return 1;
        }
        Double da = toDouble(a);
        Double db = toDouble(b);
        if (da != null && db != null) {
            return Double.compare(da, db);
        }
        return str(a).compareTo(str(b));
    }

    /**
     * Lenient double conversion: supports Number and numeric strings.
     *
     * <p>Blank strings return null (not comparable), preventing empty values from
     * being judged as 0 and altering risk-control conclusions.</p>
     */
    static Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** Export all operators for external consumers (e.g. frontend option endpoints) */
    public static Map<String, String> labels() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (Operator op : values()) {
            m.put(op.name(), op.label);
        }
        return m;
    }
}
