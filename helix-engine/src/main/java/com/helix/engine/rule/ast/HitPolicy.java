package com.helix.engine.rule.ast;

/**
 * Rule set hit policy.
 *
 * <p>Hit policy is modeled explicitly; "ALL" is compared against the actual rule count
 * of the rule set, avoiding inconsistency caused by relying on externally supplied totals.</p>
 */
public enum HitPolicy {

    /**
     * Takes effect when any rule hits (default).
     * Parallel mode: any hit reject rule causes rejection.
     */
    ANY("Any hit"),

    /**
     * Takes effect only when all rules hit.
     * Serial mode (isSerial=1): compared against the actual rule count of this rule set,
     * not relying on externally supplied total rule count.
     */
    ALL("All hit"),

    /**
     * Takes effect when hit count reaches the threshold.
     * Used for scorecard-style judgment like "hit M out of N rules".
     */
    THRESHOLD("Threshold reached");

    private final String label;

    HitPolicy(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Whether the hit count satisfies the policy.
     *
     * @param hitCount  actual hit count
     * @param total     total rule count within the rule set
     * @param threshold threshold for the THRESHOLD policy; ignored by others
     * @return whether it takes effect
     */
    public boolean satisfied(int hitCount, int total, Integer threshold) {
        if (hitCount <= 0) {
            return false;
        }
        switch (this) {
            case ALL:
                // Compare against the actual rule count of the rule set, not the externally supplied total
                return total > 0 && hitCount >= total;
            case THRESHOLD:
                return threshold != null && hitCount >= threshold;
            case ANY:
            default:
                return true;
        }
    }

    public static HitPolicy parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ANY;
        }
        String key = raw.trim().toUpperCase(java.util.Locale.ROOT);
        switch (key) {
            case "SERIAL": case "AND": case "ALL": return ALL;
            case "THRESHOLD": case "COUNT": return THRESHOLD;
            default: return ANY;
        }
    }
}
