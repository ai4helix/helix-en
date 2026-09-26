package com.helix.console.knowledge.enums;

import lombok.Getter;

/**
 * Knowledge tree type. Corresponds to {@code t_knowledge_tree.tree_type}.
 */
@Getter
public enum TreeType {

    RULE(0, "Rule Tree"),
    SCORECARD(1, "Scorecard Tree"),
    RULE_RECYCLE(2, "Rule Recycle Bin"),
    SCORECARD_RECYCLE(3, "Scorecard Recycle Bin"),
    /** Field/indicator management category (t_field.catalog_id points to a node of this tree) */
    FIELD(4, "Field Category Tree");

    private final int code;
    private final String desc;

    TreeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TreeType of(Integer code) {
        if (code == null) {
            return RULE;
        }
        for (TreeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return RULE;
    }

    /** Whether a recycle bin */
    public boolean isRecycle() {
        return this == RULE_RECYCLE || this == SCORECARD_RECYCLE;
    }
}
