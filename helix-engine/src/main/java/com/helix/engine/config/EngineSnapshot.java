package com.helix.engine.config;

import com.helix.engine.entity.RuleEntity;
import com.helix.engine.entity.ScorecardEntity;
import com.helix.engine.entity.engine.model.Engine;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.entity.engine.model.EngineVersion;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable engine configuration snapshot: "the snapshot is everything".
 *
 * <p>At load time everything needed for execution is prefetched and pre-parsed. The decision
 * path reads the snapshot only, with <b>zero DB queries and zero JSON parsing</b>.
 * On refresh the reference is swapped wholesale; there is never a half-new/half-old state.</p>
 *
 * <h3>Publish channels (Channel)</h3>
 * <p>Every active publish artifact (t_flow_publish status=1) forms a <b>channel</b>:
 * multiple channels may coexist for one version (e.g. main 90% + gray 10%); requests
 * are routed by normalized {@code traffic_weight}; a channel with {@code shadow=1} is a
 * <b>shadow track</b> -- every decision is evaluated and logged (shadow=1) but its result
 * is not returned and it does not participate in traffic splitting.</p>
 *
 * <p>The compatibility constructors wrap the five "version -> config" maps into a single
 * channel (publishId=null, weight 100, not shadow).</p>
 */
public final class EngineSnapshot {

    /** Engine code -> engine */
    private final Map<String, Engine> engineByCode;

    /** Engine id -> deployed version */
    private final Map<Integer, EngineVersion> deployedVersionByEngine;

    /** Version id -> publish channels (sorted by routing priority: routable weight desc first, shadow last) */
    private final Map<Integer, List<Channel>> channelsByVersion;

    /** Enabled list DB id -> valid entry set (trim-normalized; validity filtered at load; zero DB queries on decision path) */
    private final Map<Integer, java.util.Set<String>> listEntriesByListDb;

    /** Enabled list DB id -> name (for trace display) */
    private final Map<Integer, String> listNames;

    /**
     * Primary constructor: assembled by channel (empty list sets; for tests and compatibility).
     * Channels are sorted by {@link #CHANNEL_ORDER} at construction
     * (routable first, shadow last), guaranteeing the "primary channel = first" invariant
     * for any caller.
     */
    public EngineSnapshot(Map<String, Engine> engineByCode,
                          Map<Integer, EngineVersion> deployedVersionByEngine,
                          Map<Integer, List<Channel>> channelsByVersion) {
        this(engineByCode, deployedVersionByEngine, channelsByVersion,
                Collections.<Integer, java.util.Set<String>>emptyMap(), Collections.<Integer, String>emptyMap());
    }

    /**
     * Full constructor: channels + list DB sets.
     */
    public EngineSnapshot(Map<String, Engine> engineByCode,
                          Map<Integer, EngineVersion> deployedVersionByEngine,
                          Map<Integer, List<Channel>> channelsByVersion,
                          Map<Integer, java.util.Set<String>> listEntriesByListDb,
                          Map<Integer, String> listNames) {
        this.engineByCode = Collections.unmodifiableMap(engineByCode);
        this.deployedVersionByEngine = Collections.unmodifiableMap(deployedVersionByEngine);
        Map<Integer, List<Channel>> channels = new LinkedHashMap<>();
        channelsByVersion.forEach((versionId, list) -> {
            if (versionId != null && list != null && !list.isEmpty()) {
                List<Channel> sorted = new ArrayList<>(list);
                sorted.sort(CHANNEL_ORDER);
                channels.put(versionId, Collections.unmodifiableList(sorted));
            }
        });
        this.channelsByVersion = Collections.unmodifiableMap(channels);
        // List index: a plain LinkedHashMap suffices -- the snapshot is frozen right after
        // construction (wrapped by unmodifiableMap immediately after the puts, never written
        // again), so pure-read scenarios need no concurrent containers.
        //
        // Real concurrency safety comes from "volatile wholesale reference swap + immutable snapshot":
        // one decision pins one snapshot generation, so all maps are naturally same-generation
        // and need no per-key concurrency control.
        Map<Integer, java.util.Set<String>> entries = new LinkedHashMap<>();
        if (listEntriesByListDb != null) {
            listEntriesByListDb.forEach((listId, set) -> {
                if (listId != null && set != null && !set.isEmpty()) {
                    entries.put(listId, Collections.unmodifiableSet(new LinkedHashSet<>(set)));
                }
            });
        }
        this.listEntriesByListDb = Collections.unmodifiableMap(entries);
        this.listNames = listNames == null
                ? Collections.<Integer, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(listNames));
    }

    /**
     * Compatibility constructor: five per-version maps wrapped into a single channel.
     */
    public EngineSnapshot(Map<String, Engine> engineByCode,
                          Map<Integer, EngineVersion> deployedVersionByEngine,
                          Map<Integer, Map<String, EngineNode>> nodesByVersion,
                          Map<Integer, Map<String, PolicyConfig>> policyConfigs,
                          Map<Integer, Map<String, ScorecardConfig>> scorecardConfigs,
                          Map<Integer, Map<String, String>> ruleNamesByVersion) {
        this(engineByCode, deployedVersionByEngine, nodesByVersion, policyConfigs,
                scorecardConfigs, ruleNamesByVersion, Collections.<Integer, Map<String, DecisionTableConfig>>emptyMap());
    }

    /**
     * Compatibility constructor: five per-version maps wrapped into a single channel (with decision tables).
     */
    public EngineSnapshot(Map<String, Engine> engineByCode,
                          Map<Integer, EngineVersion> deployedVersionByEngine,
                          Map<Integer, Map<String, EngineNode>> nodesByVersion,
                          Map<Integer, Map<String, PolicyConfig>> policyConfigs,
                          Map<Integer, Map<String, ScorecardConfig>> scorecardConfigs,
                          Map<Integer, Map<String, String>> ruleNamesByVersion,
                          Map<Integer, Map<String, DecisionTableConfig>> decisionTableConfigs) {
        this(engineByCode, deployedVersionByEngine,
                wrapLegacy(nodesByVersion, policyConfigs, scorecardConfigs, ruleNamesByVersion, decisionTableConfigs));
    }

    /** Wrap the legacy five maps into "one default channel per version" */
    private static Map<Integer, List<Channel>> wrapLegacy(
            Map<Integer, Map<String, EngineNode>> nodesByVersion,
            Map<Integer, Map<String, PolicyConfig>> policyConfigs,
            Map<Integer, Map<String, ScorecardConfig>> scorecardConfigs,
            Map<Integer, Map<String, String>> ruleNamesByVersion,
            Map<Integer, Map<String, DecisionTableConfig>> decisionTableConfigs) {
        Set<Integer> versionIds = new LinkedHashSet<>();
        versionIds.addAll(nodesByVersion.keySet());
        versionIds.addAll(policyConfigs.keySet());
        versionIds.addAll(scorecardConfigs.keySet());
        versionIds.addAll(ruleNamesByVersion.keySet());
        versionIds.addAll(decisionTableConfigs.keySet());

        Map<Integer, List<Channel>> out = new LinkedHashMap<>();
        for (Integer versionId : versionIds) {
            Channel ch = new Channel(versionId, null, null, 100, false,
                    orEmpty(nodesByVersion.get(versionId)),
                    orEmptyP(policyConfigs.get(versionId)),
                    orEmptyS(scorecardConfigs.get(versionId)),
                    orEmptyR(ruleNamesByVersion.get(versionId)),
                    orEmptyD(decisionTableConfigs.get(versionId)));
            out.put(versionId, Collections.singletonList(ch));
        }
        return out;
    }

    // ===== Basic queries (version-independent) =====

    public Engine getEngine(String code) {
        return engineByCode.get(code);
    }

    public EngineVersion getDeployedVersion(Integer engineId) {
        return deployedVersionByEngine.get(engineId);
    }

    // ===== Immutable views (for incremental reloads to reuse existing parts) =====
    //
    // The snapshot itself is immutable; the five maps below are all unmodifiable-wrapped,
    // so returning them directly leaks no writable internal state. Incremental reloads
    // (lightweight list reload / engine-granularity reload) build a new snapshot via
    // "copy + override", reusing unchanged parts and avoiding full re-parsing.

    /** Engine code -> engine (read-only view) */
    public Map<String, Engine> getEngineByCodeMap() {
        return engineByCode;
    }

    /** Engine id -> deployed version (read-only view) */
    public Map<Integer, EngineVersion> getDeployedVersionMap() {
        return deployedVersionByEngine;
    }

    /** Version id -> publish channels (read-only view) */
    public Map<Integer, List<Channel>> getChannelsByVersionMap() {
        return channelsByVersion;
    }

    /** List DB id -> valid entry set (read-only view) */
    public Map<Integer, java.util.Set<String>> getListEntriesMap() {
        return listEntriesByListDb;
    }

    /** List DB id -> name (read-only view) */
    public Map<Integer, String> getListNamesMap() {
        return listNames;
    }

    // ===== Channel queries =====

    /** All publish channels of a version (routing-priority order); empty list when the version is absent */
    public List<Channel> getChannels(Integer versionId) {
        return channelsByVersion.getOrDefault(versionId, Collections.<Channel>emptyList());
    }

    /** Primary channel: the channel with the highest routing priority (default view) */
    public Channel primary(Integer versionId) {
        List<Channel> list = getChannels(versionId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Route one decision by weight.
     *
     * <p>Only "routable" channels (non-shadow and weight > 0) participate; weights are
     * normalized and the landing point is decided by {@code roll ∈ [0,1)}; with no routable
     * channel, fall back to the primary channel (e.g. all-shadow / all-zero-weight misconfigs,
     * so decisions never break).</p>
     *
     * @param roll random landing point; callers pass {@code ThreadLocalRandom.current().nextDouble()}
     */
    public Channel route(Integer versionId, double roll) {
        List<Channel> list = getChannels(versionId);
        if (list.isEmpty()) {
            return null;
        }
        long total = 0;
        for (Channel c : list) {
            if (c.isRoutable()) {
                total += c.getTrafficWeight();
            }
        }
        if (total <= 0) {
            return primary(versionId);
        }
        double point = roll * total;
        long acc = 0;
        for (Channel c : list) {
            if (!c.isRoutable()) {
                continue;
            }
            acc += c.getTrafficWeight();
            if (point < acc) {
                return c;
            }
        }
        // Floating-point boundary fallback (theoretically unreachable)
        return primary(versionId);
    }

    // ===== Compatibility views: delegate to primary channel =====

    public Map<String, EngineNode> getNodes(Integer versionId) {
        Channel p = primary(versionId);
        return p == null ? Collections.<String, EngineNode>emptyMap() : p.getNodes();
    }

    public PolicyConfig getPolicyConfig(Integer versionId, String nodeCode) {
        Channel p = primary(versionId);
        return p == null ? null : p.getPolicyConfig(nodeCode);
    }

    public ScorecardConfig getScorecardConfig(Integer versionId, String nodeCode) {
        Channel p = primary(versionId);
        return p == null ? null : p.getScorecardConfig(nodeCode);
    }

    public Map<String, String> getRuleNames(Integer versionId) {
        Channel p = primary(versionId);
        return p == null ? Collections.<String, String>emptyMap() : p.getRuleNames();
    }

    /** Decision table config of a decision node (present when node_json references decision_table_id) */
    public DecisionTableConfig getDecisionTableConfig(Integer versionId, String nodeCode) {
        Channel p = primary(versionId);
        return p == null ? null : p.getDecisionTableConfig(nodeCode);
    }

    // ===== List DB queries =====

    /** Valid entry set of a list DB; empty set when not loaded or empty */
    public java.util.Set<String> getListEntries(Integer listDbId) {
        return listEntriesByListDb.getOrDefault(listDbId, Collections.<String>emptySet());
    }

    /** List DB name (trace display); "#id" form when unknown */
    public String getListName(Integer listDbId) {
        String name = listNames.get(listDbId);
        return name == null ? "List#" + listDbId : name;
    }

    // ===== Observability counters =====

    public int getEngineCount() {
        return engineByCode.size();
    }

    /** Number of list DBs with loaded entries (observability) */
    public int getListDbCount() {
        return listEntriesByListDb.size();
    }

    /** Total number of list entries (observability) */
    public int getListEntryCount() {
        int n = 0;
        for (java.util.Set<String> set : listEntriesByListDb.values()) {
            n += set.size();
        }
        return n;
    }

    public int getNodeCount() {
        int n = 0;
        for (List<Channel> list : channelsByVersion.values()) {
            for (Channel c : list) {
                n += c.getNodes().size();
            }
        }
        return n;
    }

    public int getPolicyNodeCount() {
        int n = 0;
        for (List<Channel> list : channelsByVersion.values()) {
            for (Channel c : list) {
                n += c.getPolicyConfigs().size();
            }
        }
        return n;
    }

    /** Total rule execution plans across all policy nodes (for /status observability) */
    public int getRulePlanCount() {
        int n = 0;
        for (List<Channel> list : channelsByVersion.values()) {
            for (Channel c : list) {
                n += c.getRulePlanCount();
            }
        }
        return n;
    }

    /** Total decision-table node count (v3 observability) */
    public int getDecisionTableCount() {
        int n = 0;
        for (List<Channel> list : channelsByVersion.values()) {
            for (Channel c : list) {
                n += c.getDecisionTableConfigs().size();
            }
        }
        return n;
    }

    // ===== Internal helpers =====

    private static <K, V> Map<K, V> emptyIfNull(Map<K, V> m) {
        return m == null ? Collections.<K, V>emptyMap() : m;
    }

    private static Map<String, EngineNode> orEmpty(Map<String, EngineNode> m) {
        return emptyIfNull(m);
    }

    private static Map<String, PolicyConfig> orEmptyP(Map<String, PolicyConfig> m) {
        return emptyIfNull(m);
    }

    private static Map<String, ScorecardConfig> orEmptyS(Map<String, ScorecardConfig> m) {
        return emptyIfNull(m);
    }

    private static Map<String, String> orEmptyR(Map<String, String> m) {
        return emptyIfNull(m);
    }

    private static Map<String, DecisionTableConfig> orEmptyD(Map<String, DecisionTableConfig> m) {
        return emptyIfNull(m);
    }

    /**
     * Publish channel: a single-version executable configuration frozen from one publish artifact.
     *
     * <p>Immutable. {@code publishId == null} denotes a live-DB self-heal channel
     * (the version has no publish artifact yet; config taken from the live DB).</p>
     */
    public static final class Channel {

        private final Integer versionId;
        /** Publish artifact id; null = live-DB self-heal channel */
        private final Long publishId;
        /** Publish sequence number */
        private final Integer publishSeq;
        /** Gray traffic weight 0-100 (normalized splitting) */
        private final int trafficWeight;
        /** Shadow mode: evaluate and log only; no result returned, no traffic splitting */
        private final boolean shadow;
        private final Map<String, EngineNode> nodes;
        private final Map<String, PolicyConfig> policyConfigs;
        private final Map<String, ScorecardConfig> scorecardConfigs;
        private final Map<String, String> ruleNames;
        private final Map<String, DecisionTableConfig> decisionTableConfigs;
        /** Flow topology (fromCode -> ordered toCodes), the navigation source of truth */
        private final Map<String, List<String>> nextByCode;

        /** Owning engine id (for version-ownership validation; null in compat constructors = unknown owner) */
        private final Integer engineId;

        public Channel(Integer versionId, Long publishId, Integer publishSeq,
                       Integer trafficWeight, boolean shadow,
                       Map<String, EngineNode> nodes,
                       Map<String, PolicyConfig> policyConfigs,
                       Map<String, ScorecardConfig> scorecardConfigs,
                       Map<String, String> ruleNames,
                       Map<String, DecisionTableConfig> decisionTableConfigs) {
            this(versionId, publishId, publishSeq, trafficWeight, shadow,
                    nodes, policyConfigs, scorecardConfigs, ruleNames, decisionTableConfigs, null);
        }

        public Channel(Integer versionId, Long publishId, Integer publishSeq,
                       Integer trafficWeight, boolean shadow,
                       Map<String, EngineNode> nodes,
                       Map<String, PolicyConfig> policyConfigs,
                       Map<String, ScorecardConfig> scorecardConfigs,
                       Map<String, String> ruleNames,
                       Map<String, DecisionTableConfig> decisionTableConfigs,
                       Map<String, List<String>> edges) {
            this(versionId, publishId, publishSeq, trafficWeight, shadow,
                    nodes, policyConfigs, scorecardConfigs, ruleNames, decisionTableConfigs, edges, null);
        }

        /**
         * Full constructor.
         *
         * @param engineId owning engine id: lets the execution entry perform "version-ownership
         *                 validation", preventing running B engine's version under A engine's code
         *                 (logs would record the wrong owner).
         */
        public Channel(Integer versionId, Long publishId, Integer publishSeq,
                       Integer trafficWeight, boolean shadow,
                       Map<String, EngineNode> nodes,
                       Map<String, PolicyConfig> policyConfigs,
                       Map<String, ScorecardConfig> scorecardConfigs,
                       Map<String, String> ruleNames,
                       Map<String, DecisionTableConfig> decisionTableConfigs,
                       Map<String, List<String>> edges,
                       Integer engineId) {
            this.versionId = versionId;
            this.publishId = publishId;
            this.publishSeq = publishSeq;
            this.trafficWeight = trafficWeight == null ? 100 : trafficWeight;
            this.shadow = shadow;
            this.engineId = engineId;
            this.nodes = Collections.unmodifiableMap(nodes == null
                    ? new LinkedHashMap<String, EngineNode>() : nodes);
            this.policyConfigs = Collections.unmodifiableMap(policyConfigs == null
                    ? new LinkedHashMap<String, PolicyConfig>() : policyConfigs);
            this.scorecardConfigs = Collections.unmodifiableMap(scorecardConfigs == null
                    ? new LinkedHashMap<String, ScorecardConfig>() : scorecardConfigs);
            this.ruleNames = Collections.unmodifiableMap(ruleNames == null
                    ? new LinkedHashMap<String, String>() : ruleNames);
            this.decisionTableConfigs = Collections.unmodifiableMap(decisionTableConfigs == null
                    ? new LinkedHashMap<String, DecisionTableConfig>() : decisionTableConfigs);
            // Edge table first; without edge data (legacy artifacts/tests) derive from the
            // nextNodes comma strings, keeping navigation behavior unchanged
            Map<String, List<String>> next = new LinkedHashMap<>();
            if (edges != null && !edges.isEmpty()) {
                edges.forEach((from, tos) -> {
                    if (from != null && tos != null && !tos.isEmpty()) {
                        next.put(from, new ArrayList<>(tos));
                    }
                });
            } else {
                for (EngineNode n : this.nodes.values()) {
                    String nx = n.getNextNodes();
                    if (nx == null || nx.isEmpty() || "null".equals(nx)) {
                        continue;
                    }
                    List<String> tos = new ArrayList<>();
                    for (String p : nx.split(",")) {
                        String t = p.trim();
                        if (!t.isEmpty() && !"null".equals(t)) {
                            tos.add(t);
                        }
                    }
                    if (!tos.isEmpty()) {
                        next.put(n.getNodeCode(), tos);
                    }
                }
            }
            Map<String, List<String>> unmodifiable = new LinkedHashMap<>();
            next.forEach((k, v) -> unmodifiable.put(k, Collections.unmodifiableList(v)));
            this.nextByCode = Collections.unmodifiableMap(unmodifiable);
        }

        public Integer getVersionId() { return versionId; }
        public Long getPublishId() { return publishId; }
        public Integer getPublishSeq() { return publishSeq; }
        public int getTrafficWeight() { return trafficWeight; }
        public boolean isShadow() { return shadow; }
        /** Owning engine id (null in compat constructors) */
        public Integer getEngineId() { return engineId; }

        public Map<String, EngineNode> getNodes() { return nodes; }
        public Map<String, PolicyConfig> getPolicyConfigs() { return policyConfigs; }
        public Map<String, ScorecardConfig> getScorecardConfigs() { return scorecardConfigs; }
        public Map<String, String> getRuleNames() { return ruleNames; }
        public Map<String, DecisionTableConfig> getDecisionTableConfigs() { return decisionTableConfigs; }

        /** Whether this channel participates in traffic splitting: non-shadow and weight > 0 */
        public boolean isRoutable() {
            return !shadow && trafficWeight > 0;
        }

        /** Whether this is a live-DB self-heal channel (no publish artifact) */
        public boolean isLiveFallback() {
            return publishId == null;
        }

        public PolicyConfig getPolicyConfig(String nodeCode) {
            return policyConfigs.get(nodeCode);
        }

        public ScorecardConfig getScorecardConfig(String nodeCode) {
            return scorecardConfigs.get(nodeCode);
        }

        public DecisionTableConfig getDecisionTableConfig(String nodeCode) {
            return decisionTableConfigs.get(nodeCode);
        }

        /** Downstream codes of a node (navigation source of truth: t_flow_edge -> artifact; derived from nextNodes when absent) */
        public List<String> nextOf(String nodeCode) {
            return nextByCode.getOrDefault(nodeCode, Collections.<String>emptyList());
        }

        /**
         * Read-only adjacency table (node code -> successors), for the orchestrator to compute topological order.
         */
        public Map<String, List<String>> getNextByCode() {
            return nextByCode;
        }

        /** Total rule execution plans within the channel (observability) */
        public int getRulePlanCount() {
            int n = 0;
            for (PolicyConfig p : policyConfigs.values()) {
                n += p.getRulePlans().size();
            }
            return n;
        }
    }

    /**
     * Channel ordering: routable (non-shadow and weight>0) first -- weight desc, publish seq desc;
     * disabled (weight 0) next; shadow last. Guarantees deterministic primary-channel selection.
     */
    public static final Comparator<Channel> CHANNEL_ORDER = Comparator
            .comparing((Channel c) -> !c.isRoutable())
            .thenComparing(c -> c.isRoutable() ? -c.getTrafficWeight() : 0)
            .thenComparing(c -> c.isShadow() ? 1 : 0)
            .thenComparing(c -> c.getPublishSeq() == null ? 0 : -c.getPublishSeq());

    /**
     * Policy node prefetched config: everything needed at execution time lives here.
     *
     * <p>Holds the "rule -> condition AST" in-memory trees, evaluated directly by
     * {@code ConditionEvaluator} at execution time.</p>
     */
    public static final class PolicyConfig {

        /** Pre-parsed node_json (used for deny/addOrSub semantics aggregation) */
        private final com.helix.engine.entity.NodeJson nodeJson;

        /** Rule execution list: priority ascending, rules without condition/result already filtered out */
        private final List<RulePlan> rulePlans;

        /** Rule id -> entity (to backfill names into hit results) */
        private final Map<Integer, RuleEntity> ruleById;

        /**
         * Total reject-rule count (denominator for serial/all-hit policies).
         *
         * <p>Standard: must be taken from the <b>actually loaded</b> reject-rule count, not the
         * count declared in node_json -- actual loading gets reduced ({@code t_rule.status=1}
         * filter, deleted rules, skipped AST compile failures); if the denominator used the
         * declared count, the hit count might never catch up and <b>this node's rejecting
         * capability would silently fail entirely</b>.</p>
         */
        private final int denyTotal;

        /** Whether reject rules are serial (all must hit to reject) */
        private final boolean denySerial;

        /** Add/subtract score threshold */
        private final double addSubThreshold;

        public PolicyConfig(com.helix.engine.entity.NodeJson nodeJson, List<RulePlan> rulePlans,
                            Map<Integer, RuleEntity> ruleById) {
            this.nodeJson = nodeJson;
            this.rulePlans = rulePlans == null
                    ? Collections.<RulePlan>emptyList()
                    : Collections.unmodifiableList(rulePlans);
            this.ruleById = ruleById == null
                    ? Collections.<Integer, RuleEntity>emptyMap()
                    : Collections.unmodifiableMap(ruleById);
            com.helix.engine.entity.NodeJson.DenyRules den =
                    nodeJson == null ? null : nodeJson.getDenyRules();
            // Standard: count only "reject rules actually loaded into rulePlans", same source as
            // the hit count, avoiding a declared-vs-loaded mismatch that would make serial
            // rejection unreachable. RulePlan is a nested class of the same type; fields are
            // directly visible (no getter).
            int loadedDeny = 0;
            for (RulePlan plan : this.rulePlans) {
                if (plan != null && plan.resultType == com.helix.engine.rule.ast.ResultType.DENY) {
                    loadedDeny++;
                }
            }
            this.denyTotal = loadedDeny;
            this.denySerial = den != null && den.getIsSerial() != null && den.getIsSerial() == 1;
            com.helix.engine.entity.NodeJson.AddOrSubRules asr =
                    nodeJson == null ? null : nodeJson.getAddOrSubRules();
            this.addSubThreshold = asr == null || asr.getThreshold() == null ? 0D : asr.getThreshold();
        }

        public com.helix.engine.entity.NodeJson getNodeJson() {
            return nodeJson;
        }

        public List<RulePlan> getRulePlans() {
            return rulePlans;
        }

        public Map<Integer, RuleEntity> getRuleById() {
            return ruleById;
        }

        public int getDenyTotal() {
            return denyTotal;
        }

        public boolean isDenySerial() {
            return denySerial;
        }

        public double getAddSubThreshold() {
            return addSubThreshold;
        }

        /** Whether any executable rules exist */
        public boolean hasRules() {
            return !rulePlans.isEmpty();
        }
    }

    /**
     * Rule execution plan: the executable form of one rule.
     *
     * <p>Packages "condition tree + result definition + name" so execution needs no further lookups.</p>
     */
    public static final class RulePlan {

        private final Integer ruleId;
        private final String code;
        private final String name;
        /** Condition tree; null means unconditional (treated as always true) */
        private final com.helix.engine.rule.ast.Condition condition;
        /** Strongly typed result */
        private final com.helix.engine.rule.ast.ResultType resultType;
        /** Score value for scoring types */
        private final Integer scoreValue;
        /** Priority (smaller runs first) */
        private final int priority;

        public RulePlan(Integer ruleId, String code, String name,
                        com.helix.engine.rule.ast.Condition condition,
                        com.helix.engine.rule.ast.ResultType resultType,
                        Integer scoreValue, int priority) {
            this.ruleId = ruleId;
            this.code = code;
            this.name = name;
            this.condition = condition;
            this.resultType = resultType == null
                    ? com.helix.engine.rule.ast.ResultType.PASS : resultType;
            this.scoreValue = scoreValue;
            this.priority = priority;
        }

        public Integer getRuleId() {
            return ruleId;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public com.helix.engine.rule.ast.Condition getCondition() {
            return condition;
        }

        public com.helix.engine.rule.ast.ResultType getResultType() {
            return resultType;
        }

        public Integer getScoreValue() {
            return scoreValue;
        }

        public int getPriority() {
            return priority;
        }
    }

    /**
     * Scorecard prefetched config: dims array pre-parsed; execution only does numeric matching.
     */
    public static final class ScorecardConfig {

        private final Integer cardId;
        private final String name;
        private final JsonNode dims;

        public ScorecardConfig(Integer cardId, String name, JsonNode dims) {
            this.cardId = cardId;
            this.name = name;
            this.dims = dims;
        }

        public Integer getCardId() {
            return cardId;
        }

        public String getName() {
            return name;
        }

        /** Dims array (parsed); null means not configured */
        public JsonNode getDims() {
            return dims;
        }

        public boolean isConfigured() {
            return dims != null && dims.isArray() && dims.size() > 0;
        }
    }

    /**
     * Decision table prefetched config (v3): row conditions compiled to AST at load; zero parsing at execution.
     *
     * <p>Row conditions are compiled from "column definitions (field + default operator) x cell values",
     * all as AND groups (intra-row decision-table semantics: all dimensions must hold).
     * The output field comes from the node's node_json (output.field_code); rows only produce values.</p>
     */
    public static final class DecisionTableConfig {

        private final Integer tableId;
        private final String code;
        private final String name;
        /** Hit policy: FIRST / ALL (others treated as FIRST) */
        private final String hitPolicy;
        /** Rule rows (ascending by rowNo, disabled rows removed; conditions compiled) */
        private final List<TableRow> rows;

        public DecisionTableConfig(Integer tableId, String code, String name,
                                   String hitPolicy, List<TableRow> rows) {
            this.tableId = tableId;
            this.code = code;
            this.name = name;
            this.hitPolicy = hitPolicy == null ? "FIRST" : hitPolicy;
            this.rows = rows == null
                    ? Collections.<TableRow>emptyList()
                    : Collections.unmodifiableList(rows);
        }

        public Integer getTableId() { return tableId; }
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getHitPolicy() { return hitPolicy; }
        public List<TableRow> getRows() { return rows; }

        /** One decision table row (condition compiled + output parsed) */
        public static final class TableRow {

            private final Integer rowNo;
            /** Row condition; null means unconditional (always true), for fallback rows */
            private final com.helix.engine.rule.ast.Condition condition;
            /** Human-readable expression (trace and log display) */
            private final String expression;
            /** Row output value (business code, written to the node's output field) */
            private final String resultValue;
            /** Row conclusion (nullable) */
            private final String resultType;
            /** Row score (nullable) */
            private final Integer scoreValue;

            public TableRow(Integer rowNo, com.helix.engine.rule.ast.Condition condition,
                            String expression, String resultValue, String resultType, Integer scoreValue) {
                this.rowNo = rowNo;
                this.condition = condition;
                this.expression = expression;
                this.resultValue = resultValue;
                this.resultType = resultType;
                this.scoreValue = scoreValue;
            }

            public Integer getRowNo() { return rowNo; }
            public com.helix.engine.rule.ast.Condition getCondition() { return condition; }
            public String getExpression() { return expression; }
            public String getResultValue() { return resultValue; }
            public String getResultType() { return resultType; }
            public Integer getScoreValue() { return scoreValue; }
        }
    }
}
