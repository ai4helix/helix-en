package com.helix.console.knowledge.enums;

import lombok.Getter;

/**
 * Rule type. Corresponds to {@code t_rule.rule_type}.
 */
@Getter
public enum RuleType {

    /** Hard reject: terminates the flow once hit */
    REJECT(0, "Hard Reject"),
    /** Score adjust: adds/subtracts score on hit without terminating */
    SCORE(1, "Score Adjust");

    private final int code;
    private final String desc;

    RuleType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RuleType of(Integer code) {
        if (code == null) {
            return SCORE;
        }
        for (RuleType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return SCORE;
    }
}
