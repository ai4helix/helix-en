package com.helix.console.engine.enums;

import lombok.Getter;

/**
 * Knowledge type referenced by a node. Corresponds to {@code t_node_knowledge_rel.knowledge_type}.
 */
@Getter
public enum KnowledgeType {

    RULE(1, "Rule"),
    SCORECARD(2, "Scorecard"),
    DECISION_OPTION(3, "Decision Option"),
    COMPLEX_RULE(4, "Complex Rule");

    private final int code;
    private final String desc;

    KnowledgeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static KnowledgeType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (KnowledgeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
