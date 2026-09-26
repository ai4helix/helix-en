package com.helix.facade.engine;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Decision engine invocation response (full wire contract of {@code POST /engineApi/decision}).
 *
 * <p>The outer layer is the {@code Result<T>} envelope ({@code code/message/success/data});
 * {@link EngineData} is the decision result payload, structured identically to the real wire response.</p>
 */
@Data
public class EngineApiRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0 = success / non-zero = failure */
    private int code;

    private String message;

    /** Whether the call succeeded (engine-side Result.isSuccess()) */
    private boolean success;

    /** Decision result body */
    private EngineData data;

    /**
     * Decision result body (payload of the engine's {@code EngineApiRsp}).
     *
     * <p>Naming note: it must not be named {@code Data} — the nested class name would shadow the
     * {@code lombok.Data} annotation import, making every {@code @Data} in this class fail to
     * compile with "cannot be converted to an annotation".</p>
     */
    @Data
    public static class EngineData implements Serializable {

        private static final long serialVersionUID = 1L;

        private String traceId;

        private String engineCode;

        private String engineName;

        private Integer versionId;

        /** ID of the publish artifact that was hit (gray-release channel; null on the live snapshot channel) */
        private Long publishId;

        /** Sequence number of the publish that was hit (gray release) */
        private Integer publishSeq;

        /** Decision conclusion: 1 Pass, 2 Reject, 3 Manual Review */
        private String resultType;

        private boolean pass;

        private boolean manualReview;

        private int score;

        /** Decision option ID */
        private Integer decisionId;

        /** Elapsed time (ms) */
        private long costMs;

        /** Hit rule details */
        private List<HitRule> hitRules = new ArrayList<>();

        /** Node execution traces */
        private List<Trace> traces = new ArrayList<>();
    }

    /**
     * Hit rule details.
     */
    @Data
    public static class HitRule implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Rule code */
        private String code;

        private String name;

        /** 1 = score adjust, 2 = reject */
        private String resultType;

        /** Score adjustment value */
        private Object value;

        /** Node where the rule resides */
        private String nodeName;
    }

    /**
     * Node execution trace.
     *
     * <p>Note: this structure is also the persistence contract of the decision log's
     * {@code traces_json} column; field names must not change anymore.</p>
     */
    @Data
    public static class Trace implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer nodeId;

        private String nodeCode;

        private String nodeName;

        private Integer nodeType;

        private boolean hit;

        private int scoreDelta;

        private String message;

        private List<String> hitDetails = new ArrayList<>();

        private long costMs;
    }
}
