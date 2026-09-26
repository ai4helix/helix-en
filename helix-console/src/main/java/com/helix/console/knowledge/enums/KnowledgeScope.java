package com.helix.console.knowledge.enums;

import lombok.Getter;

/**
 * Knowledge ownership scope. Corresponds to {@code t_rule.type} / {@code t_scorecard.type}
 * / {@code t_knowledge_tree.type}.
 */
@Getter
public enum KnowledgeScope {

    SYSTEM(0, "System"),
    ORGANIZATION(1, "Organization"),
    ENGINE(2, "Engine");

    private final int code;
    private final String desc;

    KnowledgeScope(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static KnowledgeScope of(Integer code) {
        if (code == null) {
            return ORGANIZATION;
        }
        for (KnowledgeScope s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return ORGANIZATION;
    }
}
