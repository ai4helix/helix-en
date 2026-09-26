package com.helix.engine.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Decision response data. The payload of {@code Result.data}.
 */
@Data
public class EngineApiRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Trace id */
    private String traceId;

    /** Engine code */
    private String engineCode;

    private String engineName;

    private Integer versionId;

    /** The publish artifact id hit (gray channel; null for the live-DB self-heal channel) */
    private Long publishId;

    /** The publish sequence hit (gray release) */
    private Integer publishSeq;

    /** Decision conclusion: 1 Pass 2 Reject 3 Manual Review */
    private String resultType;

    /** Whether passed */
    private boolean pass;

    /** Whether manual review is required */
    private boolean manualReview;

    /** Accumulated score */
    private int score;

    /** Decision option id */
    private Integer decisionId;

    /** Cost (milliseconds) */
    private long costMs;

    /** Hit rule details */
    private List<HitRule> hitRules = new ArrayList<>();

    /** Node execution trace */
    private List<com.helix.engine.core.DecisionContext.NodeTrace> traces = new ArrayList<>();

    /**
     * Hit rule detail.
     */
    @Data
    public static class HitRule implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Rule code */
        private String code;

        private String name;

        /** 1 add/subtract score 2 reject */
        private String resultType;

        /** Add/subtract score value */
        private Object value;

        /** Owning node */
        private String nodeName;
    }
}
