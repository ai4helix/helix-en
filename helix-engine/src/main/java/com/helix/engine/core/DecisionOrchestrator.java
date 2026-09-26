package com.helix.engine.core;

import com.helix.engine.common.BizException;
import com.helix.engine.common.ResultCode;
import com.helix.engine.config.EngineSnapshot;
import com.helix.engine.config.EngineSnapshotHolder;
import com.helix.engine.entity.EngineApiReq;
import com.helix.engine.entity.EngineApiRsp;
import com.helix.engine.entity.engine.model.Engine;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.entity.engine.model.EngineVersion;
import com.helix.engine.node.NodeExecutor;
import com.helix.engine.node.NodeTypes;
import com.helix.engine.result.DecisionLogWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decision orchestrator: locate engine version -&gt; <b>route publish channel by
 * weight</b> -&gt; BFS traversal -&gt; aggregate conclusion.
 *
 * <p>The decision path is <b>DB-free</b> (config comes from the immutable
 * snapshot); traversal runs nodes in topological order with a MAX_STEPS
 * cycle guard; reject/whitelist terminates immediately.</p>
 *
 * <h3>Gray release and shadow</h3>
 * <ul>
 *   <li><b>Weighted routing</b>: each decision is randomly routed by the
 *       normalized {@code traffic_weight} of the version's "routable"
 *       channels, so primary and gray versions coexist;</li>
 *   <li><b>Shadow evaluation</b>: channels with shadow=1 asynchronously replay
 *       the whole flow for every decision, writing shadow decision logs only
 *       (t_decision_log.shadow=1, shadow_of=primary traceId), never affecting
 *       response content or latency;</li>
 *   <li><b>Provenance</b>: responses and decision logs carry the hit
 *       publishId/publishSeq, enabling per-artifact outcome comparison
 *       (gray-release validation and incident forensics).</li>
 * </ul>
 */
@Slf4j
@Component
public class DecisionOrchestrator {

    /** Max nodes per execution, guards against infinite loops from bad config */
    private static final int MAX_STEPS = 200;

    private final EngineSnapshotHolder snapshotHolder;
    private final java.util.List<NodeExecutor> executors;
    private final DecisionLogWriter decisionLogWriter;

    /** Executor routing map (cached at construction: supportType -&gt; executor, zero build cost at runtime) */
    private final Map<Integer, NodeExecutor> executorMap;

    /** Decision log async queue capacity: overflow is dropped and counted (logs are observability data; drop rather than block) */
    private static final int LOG_QUEUE_CAPACITY = 10_000;

    /** Cumulative decision-log drop count (observability metric) */
    private final java.util.concurrent.atomic.AtomicLong logDropped =
            new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * Decision log async writer thread: single thread + bounded queue.
     *
     * <p>Logs are observability data and not an availability dependency of the
     * decision path: the response returns first, log writes are best-effort;
     * on queue full/shutdown entries are dropped and counted.</p>
     */
    private final ExecutorService logPool = new ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(LOG_QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "engine-decision-log");
                t.setDaemon(true);
                return t;
            },
            (r, executor) -> {
                long dropped = logDropped.incrementAndGet();
                if (dropped == 1 || dropped % 1000 == 0) {
                    log.warn("Decision log queue full (capacity {}), dropped {} entries; check database write throughput",
                            Integer.valueOf(LOG_QUEUE_CAPACITY), Long.valueOf(dropped));
                }
            });

    /** Dropped decision-log entries (observability metric, normally always 0) */
    public long getLogDropped() {
        return logDropped.get();
    }

    /** Async log write: drop on queue full/shutdown (observability data; drop rather than block) */
    private void submitLogWrite(DecisionContext ctx, String resultText, long costMs) {
        try {
            logPool.execute(() -> decisionLogWriter.write(ctx, resultText, costMs));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            logDropped.incrementAndGet();
        }
    }

    /** Shadow replay queue capacity: overflow is dropped (shadow is observability only; never let it hurt the main flow) */
    private static final int SHADOW_QUEUE_CAPACITY = 2000;

    /** Cumulative shadow-replay drop count (observability; surfaces shadow channels flooding the queue) */
    private final java.util.concurrent.atomic.AtomicLong shadowDropped =
            new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * Shadow evaluation thread: single-threaded serial replay to avoid shadow
     * traffic amplifying CPU contention.
     *
     * <p><b>Bounded queue + drop policy</b>: capacity {@value #SHADOW_QUEUE_CAPACITY};
     * when full, drop and count; submissions after shutdown are silently
     * ignored and never throw into the main flow. Shadow logs themselves are
     * droppable and never affect main-decision correctness.</p>
     */
    private final ExecutorService shadowPool = new ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(SHADOW_QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "engine-shadow-eval");
                t.setDaemon(true);
                return t;
            },
            (r, executor) -> {
                long dropped = shadowDropped.incrementAndGet();
                if (dropped == 1 || dropped % 1000 == 0) {
                    log.warn("Shadow replay queue full (capacity {}), dropped {} sample batches; "
                                    + "check shadow channel count vs decision volume",
                            Integer.valueOf(SHADOW_QUEUE_CAPACITY), Long.valueOf(dropped));
                }
            });

    /** Dropped shadow-replay count (observability metric) */
    public long getShadowDropped() {
        return shadowDropped.get();
    }

    /** Safe shadow submission: silently skipped on shutdown or queue full; never throws into the main flow */
    private void submitShadow(Runnable task) {
        try {
            shadowPool.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            shadowDropped.incrementAndGet();
            log.warn("Shadow replay submission rejected (shutting down or queue full), skipped");
        }
    }

    public DecisionOrchestrator(EngineSnapshotHolder snapshotHolder,
                                java.util.List<NodeExecutor> executors,
                                DecisionLogWriter decisionLogWriter) {
        this.snapshotHolder = snapshotHolder;
        this.executors = executors;
        this.decisionLogWriter = decisionLogWriter;
        // Cache executor routing map at construction (executors is an immutable Spring-injected collection)
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(NodeExecutor::supportType, Function.identity(), (a, b) -> a));
    }

    @PreDestroy
    public void shutdownShadowPool() {
        shadowPool.shutdownNow();
        logPool.shutdownNow();
    }

    /** Execute a decision */
    public EngineApiRsp execute(EngineApiReq req) {
        long start = System.currentTimeMillis();
        EngineSnapshot snapshot = snapshotHolder.get();

        Engine engine = snapshot.getEngine(req.getCode());
        if (engine == null) {
            throw BizException.of(ResultCode.ENGINE_NOT_FOUND, "Engine not found or disabled: " + req.getCode());
        }
        EngineVersion version = resolveVersion(req, engine, snapshot);

        // Route the publish channel hit by this request, weighted by traffic
        EngineSnapshot.Channel channel = snapshot.route(version.getId(), ThreadLocalRandom.current().nextDouble());
        if (channel == null) {
            throw BizException.of(ResultCode.FLOW_INVALID, "Version has no available channel: " + version.getId());
        }

        Map<Integer, NodeExecutor> executorMap = this.executorMap;

        DecisionContext ctx = newContext(engine, version, req);
        ctx.setChannel(channel);
        // Pin this snapshot to the context; all node executors read from it,
        // avoiding cross-generation mixing ("node graph gen A + list gen B") if a reload happens mid-run
        ctx.setSnapshot(snapshot);

        Map<String, EngineNode> nodeMap = channel.getNodes();
        if (nodeMap.isEmpty()) {
            throw BizException.of(ResultCode.FLOW_INVALID, "Version has no available nodes: " + version.getId());
        }
        EngineNode startNode = startNodeOf(nodeMap);
        runFlow(ctx, nodeMap, startNode, executorMap);

        EngineApiRsp rsp = buildResponse(ctx, channel, engine, version);
        rsp.setCostMs(System.currentTimeMillis() - start);
        rsp.setPublishId(channel.getPublishId());
        rsp.setPublishSeq(channel.getPublishSeq());
        // Persist decision observability into the three normalized tables (log/hits/traces); async (observability data, never blocks the response).
        submitLogWrite(ctx, resultTextOf(normalizeResultType(ctx)), rsp.getCostMs());

        // Shadow channels replay asynchronously (log only, never returned)
        submitShadowEvaluations(snapshot, engine, version, req, channel, ctx.getTraceId(), executorMap);
        return rsp;
    }

    // ===== Internal methods =====

    /** Build a clean decision context (shared by primary decisions and shadow replay) */
    private DecisionContext newContext(Engine engine, EngineVersion version, EngineApiReq req) {
        DecisionContext ctx = new DecisionContext();
        ctx.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        ctx.setEngineCode(engine.getCode());
        ctx.setEngineId(engine.getId());
        ctx.setVersionId(version.getId());
        // Decision log carries the engine's owning organization
        ctx.setOrganId(engine.getOrganId() == null ? 0L : engine.getOrganId().longValue());
        ctx.setUid(req.getUid());
        ctx.setPid(req.getPid());
        Map<String, Object> input = req.getData() == null ? new HashMap<>() : new HashMap<>(req.getData());
        ctx.setInputParam(input);
        ctx.getVariables().putAll(input);
        return ctx;
    }

    private EngineNode startNodeOf(Map<String, EngineNode> nodeMap) {
        return nodeMap.values().stream()
                .filter(n -> Objects.equals(n.getNodeType(), NodeTypes.START))
                .findFirst()
                .orElseThrow(() -> BizException.of(ResultCode.FLOW_INVALID, "Decision flow missing start node"));
    }

    /**
     * Submit shadow channel evaluations: same input as the primary decision,
     * independent traceId, async, writes logs only. Any exception is logged
     * only - shadow is an observability enhancement and never affects the
     * primary decision.
     */
    private void submitShadowEvaluations(EngineSnapshot snapshot, Engine engine, EngineVersion version,
                                         EngineApiReq req, EngineSnapshot.Channel chosen,
                                         String primaryTraceId,
                                         Map<Integer, NodeExecutor> executorMap) {
        for (EngineSnapshot.Channel c : snapshot.getChannels(version.getId())) {
            if (!c.isShadow() || c == chosen) {
                continue;
            }
            submitShadow(() -> {
                long start = System.currentTimeMillis();
                try {
                    DecisionContext sctx = newContext(engine, version, req);
                    sctx.setChannel(c);
                    // Pin the same snapshot for shadow replay, keeping it generation-aligned with the primary decision
                    sctx.setSnapshot(snapshot);
                    sctx.setShadowOf(primaryTraceId);
                    runFlow(sctx, c.getNodes(), startNodeOf(c.getNodes()), executorMap);
                    decisionLogWriter.write(sctx, resultTextOf(normalizeResultType(sctx)),
                            System.currentTimeMillis() - start);
                } catch (Exception e) {
                    log.warn("Shadow evaluation failed version={} publish={}: {}",
                            version.getId(), c.getPublishId(), e.getMessage());
                }
            });
        }
    }

    /** Result text (1 Pass / 3 Manual Review / otherwise Reject) */
    private String resultTextOf(String resultType) {
        if ("1".equals(resultType)) {
            return "Pass";
        }
        if ("3".equals(resultType)) {
            return "Manual Review";
        }
        return "Reject";
    }

    /** Result normalization: whitelist &gt; reject &gt; default Pass (same adjudication as buildResponse) */
    private String normalizeResultType(DecisionContext ctx) {
        String resultType = ctx.getResultType();
        if (!"1".equals(resultType) && !"2".equals(resultType) && !"3".equals(resultType)) {
            resultType = ctx.isWhitelisted() ? "1" : (ctx.isRejected() ? "2" : "1");
        }
        return resultType;
    }

    /**
     * Node traversal: executes nodes in topological order, each exactly once.
     *
     * <p>Traversal topology comes from the channel (t_flow_edge persisted,
     * nextNodes inference as fallback).</p>
     *
     * <p>Kahn topological order guarantees "every predecessor runs before its
     * successors": for unequal-length convergence (A-&gt;B-&gt;X vs A-&gt;C-&gt;D-&gt;X where X
     * depends on variables written by D), a FIFO order A-&gt;B-&gt;C-&gt;X-&gt;D would run X
     * before D, read empty variables, and never re-run after de-duplication,
     * <b>permanently polluting the result</b>; with topological order X runs
     * only after all its predecessors (B and D) complete.</p>
     *
     * <p>Termination: once a node marks the decision rejected/whitelisted,
     * no subsequent node executes.</p>
     */
    private void runFlow(DecisionContext ctx,
                         Map<String, EngineNode> nodeMap, EngineNode startNode,
                         Map<Integer, NodeExecutor> executorMap) {
        EngineSnapshot.Channel channel = ctx.getChannel();

        executeNode(ctx, startNode, executorMap);
        // Terminate: reject / whitelist short-circuits
        if (ctx.isRejected() || ctx.isWhitelisted()) {
            log.debug("Decision terminated at start node {} (rejected={}, whitelisted={})",
                    startNode.getNodeCode(), ctx.isRejected(), ctx.isWhitelisted());
            return;
        }

        Map<String, List<String>> adjacency = channel == null
                ? Collections.<String, List<String>>emptyMap() : channel.getNextByCode();
        List<String> order = topologicalOrder(adjacency, startNode.getNodeCode());

        int steps = 0;
        for (String code : order) {
            if (startNode.getNodeCode().equals(code)) {
                continue; // start node already executed
            }
            if (steps++ >= MAX_STEPS) {
                log.warn("Decision flow exceeds node limit {}, possible cycle: versionId={}", MAX_STEPS, ctx.getVersionId());
                break;
            }
            EngineNode node = nodeMap.get(code);
            if (node == null) {
                log.warn("Node {} not found in version {}, skipping", code, ctx.getVersionId());
                continue;
            }
            executeNode(ctx, node, executorMap);
            // Terminate: reject / whitelist short-circuits (no later node executes)
            if (ctx.isRejected() || ctx.isWhitelisted()) {
                log.debug("Decision terminated at node {} (rejected={}, whitelisted={})",
                        node.getNodeCode(), ctx.isRejected(), ctx.isWhitelisted());
                break;
            }
        }
    }

    /**
     * Compute the execution order reachable from {@code startCode} (Kahn
     * topological sort).
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Only nodes reachable from start are included (unreachable nodes
     *       do not execute);</li>
     *   <li>In-degree counts distinct predecessors within the reachable set:
     *       self-loops are not counted (a self-looping node still runs once)
     *       and edges pointing back to start are not counted (guards against
     *       dirty back-edges causing deadlock);</li>
     *   <li><b>Fallback</b>: if dirty data forms a cycle so some nodes never
     *       reach in-degree zero, append them in BFS order - keeping "every
     *       reachable node runs once", only losing ordering guarantees among
     *       those nodes (a cyclic graph has no valid topological order
     *       anyway).</li>
     * </ul>
     *
     * <p>Visibility: package-private so unit tests can verify ordering properties directly.</p>
     */
    static List<String> topologicalOrder(Map<String, List<String>> nextByCode, String startCode) {
        // 1. BFS to collect the reachable set
        Set<String> reachable = new LinkedHashSet<>();
        Deque<String> bfs = new ArrayDeque<>();
        reachable.add(startCode);
        bfs.add(startCode);
        while (!bfs.isEmpty()) {
            String cur = bfs.poll();
            for (String nxt : nextByCode.getOrDefault(cur, Collections.<String>emptyList())) {
                if (StringUtils.isNotBlank(nxt) && !nxt.equals(cur) && reachable.add(nxt)) {
                    bfs.add(nxt);
                }
            }
        }

        // 2. In-degree: distinct predecessors within the reachable set (self-loops and edges to start excluded)
        Map<String, Integer> indeg = new HashMap<String, Integer>();
        for (String node : reachable) {
            indeg.put(node, 0);
        }
        for (String from : reachable) {
            Set<String> counted = new HashSet<>();
            for (String to : nextByCode.getOrDefault(from, Collections.<String>emptyList())) {
                if (StringUtils.isBlank(to) || to.equals(from) || to.equals(startCode)
                        || !reachable.contains(to) || !counted.add(to)) {
                    continue;
                }
                indeg.put(to, indeg.get(to) + 1);
            }
        }

        // 3. Kahn: start first; after each node runs, decrement successor in-degrees; ready at zero
        List<String> order = new ArrayList<String>(reachable.size());
        Set<String> ordered = new HashSet<>();
        Deque<String> ready = new ArrayDeque<>();
        ready.add(startCode);
        while (!ready.isEmpty()) {
            String cur = ready.poll();
            if (!ordered.add(cur)) {
                continue;
            }
            order.add(cur);
            Set<String> decremented = new HashSet<>();
            for (String to : nextByCode.getOrDefault(cur, Collections.<String>emptyList())) {
                if (StringUtils.isBlank(to) || to.equals(cur) || to.equals(startCode)
                        || !reachable.contains(to) || !decremented.add(to)) {
                    continue;
                }
                int left = indeg.get(to) - 1;
                indeg.put(to, left);
                if (left == 0) {
                    ready.add(to);
                }
            }
        }

        // 4. Fallback: cyclic dirty-data nodes appended in BFS order (no node lost)
        for (String node : reachable) {
            if (ordered.add(node)) {
                order.add(node);
            }
        }
        return order;
    }

    /** Execute a single node: exceptions never abort the decision; record a trace for diagnostics */
    private void executeNode(DecisionContext ctx, EngineNode node,
                             Map<Integer, NodeExecutor> executorMap) {
        NodeExecutor executor = executorMap.get(node.getNodeType());
        long t0 = System.currentTimeMillis();
        try {
            if (executor == null) {
                DecisionContext.NodeTrace t = DecisionContext.newTrace(node);
                t.setMessage("Node type " + NodeTypes.name(node.getNodeType()) + " has no executor, skipped");
                ctx.getTraces().add(t);
            } else {
                executor.execute(ctx, node);
            }
        } catch (Exception e) {
            log.error("Node {} execution error", node.getNodeCode(), e);
            DecisionContext.NodeTrace t = DecisionContext.newTrace(node);
            t.setMessage("Execution error: " + e.getMessage());
            ctx.getTraces().add(t);
        }
        List<DecisionContext.NodeTrace> traces = ctx.getTraces();
        if (!traces.isEmpty()) {
            traces.get(traces.size() - 1).setCostMs(System.currentTimeMillis() - t0);
        }
    }

    /** Resolve the version to execute: request-specified wins, else the deployed version */
    private EngineVersion resolveVersion(EngineApiReq req, Engine engine, EngineSnapshot snapshot) {
        if (req.getVersionId() != null) {
            if (snapshot.getNodes(req.getVersionId()).isEmpty()) {
                throw BizException.of(ResultCode.NOT_FOUND, "Specified version not found or not deployed: " + req.getVersionId());
            }
            // Ownership check: the version must belong to the requested engine, preventing
            // engine A's code from executing engine B's version id (the log would record
            // A's engineCode/organId, corrupting audit and enabling cross-engine execution).
            // Ownership is taken from any channel of the version (channels of one version
            // always belong to the same engine); when unknown (legacy/constructed data),
            // allow it through to preserve existing behavior.
            List<EngineSnapshot.Channel> chans = snapshot.getChannels(req.getVersionId());
            Integer owner = chans.isEmpty() ? null : chans.get(0).getEngineId();
            if (owner != null && !owner.equals(engine.getId())) {
                throw BizException.of(ResultCode.NOT_FOUND,
                        "Version " + req.getVersionId() + " does not belong to engine " + engine.getCode());
            }
            EngineVersion v = new EngineVersion();
            v.setId(req.getVersionId());
            v.setEngineId(engine.getId());
            return v;
        }
        EngineVersion deployed = snapshot.getDeployedVersion(engine.getId());
        if (deployed == null) {
            throw BizException.of(ResultCode.ENGINE_NOT_FOUND, "Engine has no deployed version: " + engine.getCode());
        }
        return deployed;
    }

    /**
     * Aggregate the execution result. Conclusion priority: explicit
     * black/whitelist conclusion &gt; default Pass. Business codes output by
     * decision nodes (e.g. "1000,13") are approval codes, not conclusions,
     * and do not participate in adjudication.
     */
    private EngineApiRsp buildResponse(DecisionContext ctx, EngineSnapshot.Channel channel,
                                       Engine engine, EngineVersion version) {
        EngineApiRsp rsp = new EngineApiRsp();
        rsp.setTraceId(ctx.getTraceId());
        rsp.setEngineCode(engine.getCode());
        rsp.setEngineName(engine.getName());
        rsp.setVersionId(version.getId());
        rsp.setPublishId(channel.getPublishId());
        rsp.setPublishSeq(channel.getPublishSeq());
        rsp.setScore(ctx.getScore());
        rsp.setManualReview(ctx.isManualReview());
        rsp.setDecisionId(ctx.getDecisionId());
        rsp.setTraces(ctx.getTraces());

        String resultType = normalizeResultType(ctx);
        rsp.setResultType(resultType);
        rsp.setPass("1".equals(resultType));
        if ("3".equals(resultType)) {
            rsp.setManualReview(true);
        }

        // Hit rule details: names come from the channel's prebuilt map (both r_/r prefixed forms)
        Map<String, String> nameByCode = channel.getRuleNames();
        ctx.getNodeResults().forEach((nodeCode, results) -> results.forEach(r -> {
            EngineApiRsp.HitRule hr = new EngineApiRsp.HitRule();
            hr.setCode(r.getCode());
            hr.setName(r.getName() != null ? r.getName() : nameByCode.get(r.getCode()));
            hr.setResultType(r.getResultType());
            hr.setValue(r.getValue());
            ctx.getTraces().stream()
                    .filter(t -> nodeCode.equals(t.getNodeCode()))
                    .findFirst()
                    .ifPresent(t -> hr.setNodeName(t.getNodeName()));
            rsp.getHitRules().add(hr);
        }));
        return rsp;
    }

}
