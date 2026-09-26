package com.helix.engine.core;

import com.helix.engine.config.EngineSnapshot;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decision context: spans one complete decision, accumulating variables, hit results, and flow state.
 *
 * <p>Only keeps state genuinely needed during execution; configuration-type data (node graph/rule plans/rule names)
 * always goes through {@code EngineSnapshot}.</p>
 */
@Data
public class DecisionContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Trace id, linking logs and result sets */
    private String traceId;

    /** Request-level inputs */
    private String uid;
    private String pid;

    private String engineCode;
    private Integer engineId;
    private Integer versionId;

    /** Engine's owning organization (tenant redundancy in decision logs; 0 = platform public) */
    private Long organId;

    /** Raw business inputs */
    private Map<String, Object> inputParam = new HashMap<>();

    /** Runtime variable table: inputs + node outputs; all node condition evaluation reads from here */
    private Map<String, Object> variables = new HashMap<>();

    /** Node outputs: nodeCode -> hit rule results */
    private Map<String, List<com.helix.engine.entity.engine.model.Result>> nodeResults = new HashMap<>();

    /** Accumulated score (scorecard + add/subtract rules) */
    private int score;

    /** Hit rejection */
    private boolean rejected;

    /** Hit whitelist */
    private boolean whitelisted;

    /** Manual review required */
    private boolean manualReview;

    /** Final decision option id */
    private Integer decisionId;

    /** Decision conclusion: 1 Pass 2 Reject 3 Manual Review */
    private String resultType;

    /**
     * The publish channel hit by this execution: all configuration lookups go through the channel view,
     * ensuring gray/shadow tracks each use the configuration frozen in their own publish artifact.
     */
    private transient EngineSnapshot.Channel channel;

    /**
     * The snapshot generation fixed for this decision.
     *
     * <p>The orchestrator takes the snapshot once at flow start and attaches it to the context; node executors
     * always take it from here and <b>never call {@code holder.get()} themselves</b> — otherwise, if a reload
     * happens mid-decision, the node graph could come from gen A while list data comes from gen B
     * (cross-generation mixing), completely undetectable in the trace.</p>
     */
    private transient EngineSnapshot snapshot;

    /** The main decision traceId corresponding to a shadow evaluation (present only for shadow channel evaluation) */
    private String shadowOf;

    /** Node execution trace */
    private List<NodeTrace> traces = new ArrayList<>();

    /**
     * Trace of a single node execution.
     */
    @Data
    public static class NodeTrace implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer nodeId;
        private String nodeCode;
        private String nodeName;
        private Integer nodeType;
        private boolean hit;
        private int scoreDelta;
        private String message;
        private List<String> hitDetails = new ArrayList<>();

        /** Node cost (milliseconds) */
        private long costMs;
    }

    public void putVar(String key, Object value) {
        variables.put(key, value);
    }

    public Object getVar(String key) {
        return variables.get(key);
    }

    public void addScore(int delta) {
        this.score += delta;
    }

    /** The publish artifact id hit (null for the live-DB self-heal channel) */
    public Long getPublishId() {
        return channel == null ? null : channel.getPublishId();
    }

    /** Whether this is a shadow track evaluation (record only, never returned) */
    public boolean isShadowRun() {
        return channel != null && channel.isShadow();
    }

    /**
     * Create an empty trace with the node's basic info.
     */
    public static NodeTrace newTrace(com.helix.engine.entity.engine.model.EngineNode node) {
        NodeTrace t = new NodeTrace();
        t.setNodeId(node.getNodeId());
        t.setNodeCode(node.getNodeCode());
        t.setNodeName(node.getNodeName());
        t.setNodeType(node.getNodeType());
        return t;
    }
}
