package com.helix.engine.rule.ast;

/**
 * Rule result type (strongly typed).
 *
 * <p>Also compatible with legacy numeric codes {@code 1/2/3}: add/subtract score share
 * legacy code {@code 1} (distinguished by the sign of the value), kept compatible
 * through {@link #legacyCode()}.</p>
 */
public enum ResultType {

    /** Pass */
    PASS(1, "Pass"),

    /** Reject */
    DENY(2, "Reject"),

    /** Route to manual review */
    MANUAL(3, "Manual Review"),

    /** Add score (positive side of legacy code 1) */
    ADD_SCORE(4, "Add Score"),

    /** Subtract score (negative side of legacy code 1) */
    SUB_SCORE(5, "Subtract Score");

    private final int legacyCode;
    private final String label;

    ResultType(int legacyCode, String label) {
        this.legacyCode = legacyCode;
        this.label = label;
    }

    /** Compatible with legacy 1/2/3 codes */
    public int legacyCode() {
        switch (this) {
            case ADD_SCORE:
            case SUB_SCORE:
                return 1;
            default:
                return legacyCode;
        }
    }

    public String getLabel() {
        return label;
    }

    /** Whether this is a scoring type (add/subtract score) */
    public boolean isScore() {
        return this == ADD_SCORE || this == SUB_SCORE;
    }

    /** Whether this is blocking (reject), used to decide whether to terminate the flow */
    public boolean isBlocking() {
        return this == DENY;
    }

    /**
     * The ambiguous legacy code: {@code "1"}.
     *
     * <p>{@link #legacyCode()} maps {@link #PASS}/{@link #ADD_SCORE}/{@link #SUB_SCORE}
     * all to {@code 1}, so reverse-parsing {@code "1"} is inherently non-unique --
     * the caller must decide using {@code rule_type} plus the score sign;
     * it must not be guessed here.</p>
     */
    private static final String LEGACY_AMBIGUOUS = "1";

    /**
     * Parse from a legacy string code.
     *
     * <p>Only handles <b>unambiguous</b> codes (2/3/4/5). {@code "1"} is ambiguous
     * (could be PASS, or add/subtract score), so this <b>returns {@code null}</b>
     * for the caller to decide with the score; it must not silently assume
     * {@link #PASS} -- otherwise the score of a legacy scoring rule would be
     * silently dropped.</p>
     */
    public static ResultType fromLegacy(String code) {
        if (code == null) {
            return null;
        }
        switch (code.trim()) {
            case "2": return DENY;
            case "3": return MANUAL;
            case "4": return ADD_SCORE;
            case "5": return SUB_SCORE;
            case LEGACY_AMBIGUOUS:
            default: return null;
        }
    }

    /**
     * Whether the value is in legacy-code form: fallback inference is needed.
     *
     * <p>Covers three cases: {@code null}, blank strings, and the ambiguous legacy
     * code {@code "1"}. Note {@code "1"} is not null and cannot be detected with
     * {@code == null} alone; otherwise {@code "1"} would fall into lenient parsing ->
     * PASS -> {@code isScore()} false, silently dropping the legacy rule's score.</p>
     */
    public static boolean isNotMigrated(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return true;
        }
        return LEGACY_AMBIGUOUS.equals(raw.trim());
    }

    /**
     * Strict parsing: by enum name or an <b>unambiguous</b> legacy code.
     *
     * <p>Returns {@code null} when unrecognized (no downgrade); the caller then skips
     * the rule and raises an alert -- an unrecognized value must never be silently
     * treated as {@link #PASS}, otherwise a reject rule with a mistyped result type
     * would become a pass rule.</p>
     */
    public static ResultType tryParse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String key = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (key.matches("\\d+")) {
            // Code form: only accept unambiguous 2/3/4/5; "1" requires the caller to decide with the score
            return fromLegacy(key);
        }
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
