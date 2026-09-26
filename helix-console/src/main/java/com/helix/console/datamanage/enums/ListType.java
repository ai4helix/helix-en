package com.helix.console.datamanage.enums;

import lombok.Getter;

/**
 * List DB type. Corresponds to {@code t_list_db.list_type}.
 */
@Getter
public enum ListType {

    BLACK("b", "Blacklist"),
    WHITE("w", "Whitelist");

    private final String code;
    private final String desc;

    ListType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ListType of(String code) {
        if (code == null) {
            return BLACK;
        }
        for (ListType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return BLACK;
    }

    public boolean isWhite() {
        return this == WHITE;
    }
}
