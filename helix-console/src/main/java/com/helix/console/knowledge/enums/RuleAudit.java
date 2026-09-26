package com.helix.console.knowledge.enums;

import lombok.Getter;

/**
 * Audit action after a rule hits. Corresponds to {@code t_rule.rule_audit}.
 *
 * <p>Legacy semantics: when {@code ruleAudit == 2}, {@code ruleType} is treated as 0 (hard reject);
 * otherwise it is treated as 1 (score adjust). The value meanings are preserved here and
 * converted uniformly by the Service layer.</p>
 */
@Getter
public enum RuleAudit {

    REJECT(2, "Reject"),
    MANUAL(3, "Manual Review"),
    SIMPLIFY(4, "Simplify Process"),
    PASS(5, "Pass");

    private final int code;
    private final String desc;

    RuleAudit(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RuleAudit of(Integer code) {
        if (code == null) {
            return null;
        }
        for (RuleAudit a : values()) {
            if (a.code == code) {
                return a;
            }
        }
        return null;
    }
}
