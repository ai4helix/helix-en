package com.helix.console.engine.enums;

import lombok.Getter;

/**
 * Decision flow node types.
 *
 * <p>Values are fully consistent with the legacy helix-rules {@code tbl_engine_node.node_type},
 * ensuring existing data can be migrated directly.</p>
 */
@Getter
public enum NodeType {

    /** Start node, exactly one per version */
    START(1, "Start"),
    /** Policy rule node, references t_rule */
    POLICY(2, "Policy Rule"),
    /** Customer segmentation node */
    CLASSIFY(3, "Customer Segmentation"),
    /** Scorecard node, references t_scorecard */
    SCORECARD(4, "Scorecard"),
    /** Blacklist node */
    BLACKLIST(5, "Blacklist"),
    /** Whitelist node */
    WHITELIST(6, "Whitelist"),
    /** Sandbox ratio node, splits traffic by ratio */
    SANDBOX(7, "Sandbox Ratio"),
    /** Credit rating node */
    HELIX_LEVEL(8, "Credit Rating"),
    /** Decision option node, produces the final decision */
    DECISION(9, "Decision Option"),
    /** Quota calculation node */
    QUOTA_CALC(10, "Quota Calculation"),
    /** Report analysis node */
    REPORT(11, "Report Analysis"),
    /** Custom node */
    CUSTOMIZE(12, "Custom Button"),
    /** Complex rule node */
    COMPLEX_RULE(13, "Complex Rule");

    private final int code;
    private final String desc;

    NodeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static NodeType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (NodeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
