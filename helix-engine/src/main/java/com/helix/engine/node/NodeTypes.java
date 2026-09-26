package com.helix.engine.node;

/**
 * Node types. Values are exactly identical to {@code t_engine_node.node_type}.
 */
public final class NodeTypes {

    private NodeTypes() {
    }

    /** Start node */
    public static final int START = 1;
    /** Policy rule node */
    public static final int POLICY = 2;
    /** Customer segmentation */
    public static final int CLASSIFY = 3;
    /** Scorecard */
    public static final int SCORECARD = 4;
    /** Blacklist */
    public static final int BLACKLIST = 5;
    /** Whitelist */
    public static final int WHITELIST = 6;
    /** Sandbox ratio */
    public static final int SANDBOX = 7;
    /** Credit rating */
    public static final int HELIX_LEVEL = 8;
    /** Decision option */
    public static final int DECISION = 9;
    /** Quota calculation */
    public static final int QUOTA_CALC = 10;
    /** Report analysis */
    public static final int REPORT = 11;
    /** Custom node */
    public static final int CUSTOMIZE = 12;
    /** Complex rule */
    public static final int COMPLEX_RULE = 13;

    public static String name(Integer type) {
        if (type == null) {
            return "Unknown";
        }
        switch (type) {
            case START: return "Start";
            case POLICY: return "Policy Rule";
            case CLASSIFY: return "Customer Segmentation";
            case SCORECARD: return "Scorecard";
            case BLACKLIST: return "Blacklist";
            case WHITELIST: return "Whitelist";
            case SANDBOX: return "Sandbox Ratio";
            case HELIX_LEVEL: return "Credit Rating";
            case DECISION: return "Decision Option";
            case QUOTA_CALC: return "Quota Calculation";
            case REPORT: return "Report Analysis";
            case CUSTOMIZE: return "Custom Node";
            case COMPLEX_RULE: return "Complex Rule";
            default: return "Unknown(" + type + ")";
        }
    }
}
