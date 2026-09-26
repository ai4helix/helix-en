package com.helix.engine.rule.ast;

import java.util.Locale;

public enum Logic {

    AND,

    OR;

    public static Logic parse(String name) {
        if (name == null || name.trim().isEmpty()) {
            return AND;
        }
        String key = name.trim().toUpperCase(Locale.ROOT);
        switch (key) {
            case "OR": case "||": case "ANY": return OR;
            case "AND": case "&&": case "ALL": return AND;
            default: return AND;
        }
    }
}
