package com.helix.console.engine.enums;

import lombok.Getter;

/**
 * Engine decision result. Corresponds to the values of {@code t_resultset.result}.
 */
@Getter
public enum DecisionResult {

    PASS("1", "Pass"),
    REJECT("2", "Reject");

    private final String code;
    private final String desc;

    DecisionResult(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static DecisionResult of(String code) {
        if (code == null) {
            return null;
        }
        for (DecisionResult r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        return null;
    }
}
