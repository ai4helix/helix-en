package com.helix.engine.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.engine.common.BizException;
import com.helix.engine.common.ResultCode;
import com.helix.engine.entity.NodeJson;
import com.helix.engine.entity.NodeKnowledgeRel;
import com.helix.engine.entity.RuleConditionEntity;
import com.helix.engine.entity.RuleEntity;
import com.helix.engine.entity.ScorecardEntity;
import com.helix.engine.entity.engine.model.Engine;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.entity.engine.model.EngineVersion;
import com.helix.engine.entity.FlowEdgeEntity;
import com.helix.engine.entity.FlowPublishEntity;
import com.helix.engine.entity.ListEntryEntity;
import com.helix.engine.mapper.EngineMapper;
import com.helix.engine.mapper.EngineNodeMapper;
import com.helix.engine.mapper.EngineVersionMapper;
import com.helix.engine.mapper.FlowEdgeMapper;
import com.helix.engine.mapper.FlowPublishMapper;
import com.helix.engine.mapper.ListEntryMapper;
import com.helix.engine.mapper.NodeKnowledgeRelMapper;
import com.helix.engine.mapper.RuleConditionMapper;
import com.helix.engine.mapper.RuleMapper;
import com.helix.engine.mapper.ScorecardMapper;
import com.helix.engine.node.NodeTypes;
import com.helix.engine.rule.ast.AstAssembler;
import com.helix.engine.rule.ast.Condition;
import com.helix.engine.rule.ast.ResultType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Engine snapshot loader: builds a "snapshot is everything" execution
 * environment on startup/refresh.
 *
 * <p>Flow: load engines/deployed versions/nodes -&gt; pre-parse node_json per
 * node -&gt; batch-load rules by aggregated rule ids (with relation-table
 * fallback) and compile condition ASTs -&gt; batch-load scorecards and
 * pre-parse -&gt; build the immutable snapshot and swap it atomically.
 * The decision path then runs with zero DB queries, zero JSON parsing,
 * zero compilation.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineSnapshotLoader implements ApplicationRunner {

    private final EngineMapper engineMapper;
    private final EngineVersionMapper engineVersionMapper;
    private final EngineNodeMapper engineNodeMapper;
    private final RuleMapper ruleMapper;
    private final RuleConditionMapper ruleConditionMapper;
    private final ScorecardMapper scorecardMapper;
    private final NodeKnowledgeRelMapper nodeKnowledgeRelMapper;
    private final com.helix.engine.mapper.DecisionTableMapper decisionTableMapper;
    private final com.helix.engine.mapper.DtColumnMapper dtColumnMapper;
    private final com.helix.engine.mapper.DtRowMapper dtRowMapper;
    private final com.helix.engine.mapper.DtCellMapper dtCellMapper;
    private final FlowPublishMapper flowPublishMapper;
    private final FlowEdgeMapper flowEdgeMapper;
    private final ListEntryMapper listEntryMapper;
    private final EngineSnapshotHolder holder;
    private final ObjectMapper objectMapper;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile java.time.LocalDateTime appliedListBoundary;

    @Override
    public void run(ApplicationArguments args) {
        try {
            reload();
        } catch (Exception e) {
            log.error("Engine snapshot initial load failed; call /engineApi/update to retry", e);
        }
    }

    public void reload() {
        lock.writeLock().lock();
        try {
            holder.countReload();
            doReload();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void doReload() {
        long start = System.currentTimeMillis();

        List<Engine> engines = engineMapper.selectList(
                new LambdaQueryWrapper<Engine>().eq(Engine::getStatus, 1));
        List<EngineVersion> versions = selectDeployableVersions();

        ChannelBuild build = buildChannels(versions);

        Map<String, Engine> engineByCode = engineIndex(engines);
        Map<Integer, EngineVersion> deployed = deployedIndex(versions);

        ListData lists = loadListData();

        EngineSnapshot snapshot = new EngineSnapshot(engineByCode, deployed, build.channelsByVersion,
                lists.entries, lists.names);
        holder.replace(snapshot, start);
        appliedListBoundary = lists.nextBoundary;

        log.info("Engine snapshot built in {} ms: engines {}, deployed versions {} (artifact channels {}, missing-artifact skipped {}), "
                        + "nodes {}, policy nodes {}, rule plans {}, condition ASTs {}",
                System.currentTimeMillis() - start, engineByCode.size(), deployed.size(),
                build.fromArtifact, build.missingArtifact, snapshot.getNodeCount(),
                snapshot.getPolicyNodeCount(), build.rulePlanCount, build.condTreeCount);
        if (build.missingArtifact > 0) {
            log.error("{} deployed versions not loaded for missing publish artifacts - deployed versions must be published first (v3.9 B)", build.missingArtifact);
        }
    }


    public void reloadLists() {
        lock.writeLock().lock();
        try {
            holder.countReload();
            doReloadLists();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void doReloadLists() {
        long start = System.currentTimeMillis();
        EngineSnapshot cur = holder.get();
        ListData lists = loadListData();
        EngineSnapshot snapshot = new EngineSnapshot(
                cur.getEngineByCodeMap(), cur.getDeployedVersionMap(), cur.getChannelsByVersionMap(),
                lists.entries, lists.names);
        holder.replace(snapshot, start);
        appliedListBoundary = lists.nextBoundary;
        log.info("List-only reload done in {} ms: list DBs {}, list entries {} (engines/versions/channels reused, not rebuilt)",
                System.currentTimeMillis() - start, snapshot.getListDbCount(), snapshot.getListEntryCount());
    }

    public void reloadEngineWithLists(String engineCode) {
        lock.writeLock().lock();
        try {
            holder.countReload();
            if (StringUtils.isBlank(engineCode)) {
                doReload();
                return;
            }
            try {
                doReloadEngineWithLists(engineCode);
            } catch (Exception e) {
                log.error("Incremental reload with lists failed for engine {}, falling back to full rebuild", engineCode, e);
                doReload();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void doReloadEngineWithLists(String engineCode) {
        long loadStartAt = System.currentTimeMillis();
        ListData lists = loadListData();
        EngineSnapshot staged = stageReloadEngine(engineCode, loadStartAt);
        EngineSnapshot snapshot = new EngineSnapshot(
                staged.getEngineByCodeMap(), staged.getDeployedVersionMap(),
                staged.getChannelsByVersionMap(), lists.entries, lists.names);
        holder.replace(snapshot, loadStartAt);
        appliedListBoundary = lists.nextBoundary;
        log.info("Incremental reload with lists done for engine {}: list DBs {}, list entries {}",
                engineCode, snapshot.getListDbCount(), snapshot.getListEntryCount());
    }

    public void reloadEngines(java.util.Collection<String> engineCodes, boolean refreshLists) {
        lock.writeLock().lock();
        try {
            holder.countReload();
            long loadStartAt = System.currentTimeMillis();
            List<String> codes = engineCodes == null ? java.util.Collections.emptyList()
                    : engineCodes.stream().filter(StringUtils::isNotBlank).distinct()
                            .collect(Collectors.toList());
            if (codes.isEmpty() && !refreshLists) {
                return;
            }
            try {
                ListData lists = refreshLists ? loadListData() : null;
                EngineSnapshot staged = holder.get();
                for (String code : codes) {
                    staged = stageReloadEngine(code, loadStartAt, staged);
                }
                EngineSnapshot snapshot = lists == null ? staged
                        : new EngineSnapshot(staged.getEngineByCodeMap(), staged.getDeployedVersionMap(),
                                staged.getChannelsByVersionMap(), lists.entries, lists.names);
                holder.replace(snapshot, loadStartAt);
                if (lists != null) {
                    appliedListBoundary = lists.nextBoundary;
                }
                log.info("Batch incremental reload done: {} engines {}, lists{}, took {} ms",
                        codes.size(), codes, refreshLists ? " refreshed" : " not refreshed",
                        System.currentTimeMillis() - loadStartAt);
            } catch (Exception e) {
                log.error("Batch incremental reload failed, falling back to full rebuild: engines={}", codes, e);
                doReload();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void reloadEngine(String engineCode) {
        lock.writeLock().lock();
        try {
            holder.countReload();
            if (StringUtils.isBlank(engineCode)) {
                doReload();
                return;
            }
            try {
                doReloadEngine(engineCode);
            } catch (Exception e) {
                log.error("Incremental reload failed for engine {}, falling back to full rebuild", engineCode, e);
                doReload();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void doReloadEngine(String engineCode) {
        long loadStartAt = System.currentTimeMillis();
        EngineSnapshot snapshot = stageReloadEngine(engineCode, loadStartAt);
        holder.replace(snapshot, loadStartAt);
    }

    private EngineSnapshot stageReloadEngine(String engineCode, long loadStartAt) {
        return stageReloadEngine(engineCode, loadStartAt, holder.get());
    }

    private EngineSnapshot stageReloadEngine(String engineCode, long loadStartAt, EngineSnapshot cur) {
        long start = System.currentTimeMillis();
        Engine engine = engineMapper.selectOne(new LambdaQueryWrapper<Engine>()
                .eq(Engine::getCode, engineCode).last("LIMIT 1"));
        Integer engineId = engine == null ? null : engine.getId();

        List<EngineVersion> allVersions = selectDeployableVersions();

        Map<String, Engine> engineByCode = new LinkedHashMap<>(cur.getEngineByCodeMap());
        Map<Integer, EngineVersion> deployed = new LinkedHashMap<>(cur.getDeployedVersionMap());
        Map<Integer, List<EngineSnapshot.Channel>> channels =
                new LinkedHashMap<>(cur.getChannelsByVersionMap());

        java.util.Set<Integer> oldVersionIds = collectEngineVersionIds(engineId, allVersions, deployed);
        oldVersionIds.forEach(channels::remove);

        if (engine == null || engine.getStatus() == null || engine.getStatus() != 1) {
            engineByCode.remove(engineCode);
            if (engineId != null) {
                deployed.remove(engineId);
            }
            log.info("Engine {} not found or disabled, removed from snapshot ({} stale versions)", engineCode, oldVersionIds.size());
        } else {
            engineByCode.put(engineCode, engine);
            List<EngineVersion> mine = allVersions.stream()
                    .filter(v -> engineId.equals(v.getEngineId()))
                    .collect(Collectors.toList());
            ChannelBuild build = buildChannels(mine);
            channels.putAll(build.channelsByVersion);
            EngineVersion dep = mine.stream().filter(this::isDeployed).findFirst().orElse(null);
            if (dep == null) {
                deployed.remove(engineId);
            } else {
                deployed.put(engineId, dep);
            }
            log.info("Incremental reload assembled for engine {} in {} ms: versions {}, channels {}, artifact channels {}, missing artifacts {}",
                    engineCode, System.currentTimeMillis() - start, mine.size(),
                    build.channelsByVersion.size(), build.fromArtifact, build.missingArtifact);
        }

        return new EngineSnapshot(engineByCode, deployed, channels,
                cur.getListEntriesMap(), cur.getListNamesMap());
    }

    private java.util.Set<Integer> collectEngineVersionIds(Integer engineId,
                                                           List<EngineVersion> allVersions,
                                                           Map<Integer, EngineVersion> deployed) {
        java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
        if (engineId == null) {
            return ids;
        }
        for (EngineVersion v : allVersions) {
            if (v.getId() != null && engineId.equals(v.getEngineId())) {
                ids.add(v.getId());
            }
        }
        EngineVersion stale = deployed.get(engineId);
        if (stale != null && stale.getId() != null) {
            ids.add(stale.getId());
        }
        try {
            for (EngineVersion v : engineVersionMapper.selectList(
                    new LambdaQueryWrapper<EngineVersion>()
                            .eq(EngineVersion::getEngineId, engineId))) {
                if (v.getId() != null) {
                    ids.add(v.getId());
                }
            }
        } catch (Exception e) {
            log.warn("Fallback query for all versions of engine {} failed (removal may be incomplete; next full reload will converge): {}",
                    engineId, e.getMessage());
        }
        return ids;
    }


    private List<EngineVersion> selectDeployableVersions() {
        return engineVersionMapper.selectList(
                new LambdaQueryWrapper<EngineVersion>()
                        .eq(EngineVersion::getBootState, 1)
                        .in(EngineVersion::getStatus, 0, 1));
    }

    private static final class ChannelBuild {
        final Map<Integer, List<EngineSnapshot.Channel>> channelsByVersion = new LinkedHashMap<>();
        int fromArtifact;
        int missingArtifact;
        int condTreeCount;
        int rulePlanCount;

        void countRulePlans() {
            for (List<EngineSnapshot.Channel> list : channelsByVersion.values()) {
                for (EngineSnapshot.Channel c : list) {
                    rulePlanCount += c.getRulePlanCount();
                }
            }
        }
    }

    private static final class ListData {
        final Map<Integer, java.util.Set<String>> entries = new LinkedHashMap<>();
        final Map<Integer, String> names = new LinkedHashMap<>();
        java.time.LocalDateTime nextBoundary;
    }

    private ChannelBuild buildChannels(List<EngineVersion> versions) {
        ChannelBuild build = new ChannelBuild();
        for (EngineVersion version : versions) {
            Integer versionId = version.getId();
            if (versionId == null) {
                continue;
            }
            List<EngineSnapshot.Channel> channels = new ArrayList<>();
            for (FlowPublishEntity pub : flowPublishMapper.selectActives(versionId)) {
                if (pub == null || StringUtils.isBlank(pub.getArtifact())) {
                    continue;
                }
                try {
                    VersionInputs inputs = objectMapper.readValue(pub.getArtifact(), VersionInputs.class);
                    channels.add(buildChannel(versionId, version.getEngineId(), pub, inputs));
                    build.fromArtifact++;
                    build.condTreeCount += inputs.getCondByRule().size();
                } catch (Exception e) {
                    log.warn("Failed to parse publish artifact {} of version {}, skipping this channel: {}", versionId, pub.getId(), e.getMessage());
                }
            }
            if (channels.isEmpty()) {
                log.error("Deployed version {} has no usable publish artifact, skipped this round (no fallback to live DB). "
                        + "Publish it first via POST /engineApi/publish/{} and retry", versionId, versionId);
                build.missingArtifact++;
                continue;
            }
            channels.sort(EngineSnapshot.CHANNEL_ORDER);
            build.channelsByVersion.put(versionId, channels);
        }
        build.countRulePlans();
        return build;
    }

    private Map<String, Engine> engineIndex(List<Engine> engines) {
        return engines.stream()
                .filter(e -> e.getCode() != null)
                .collect(Collectors.toMap(Engine::getCode, e -> e, (a, b) -> a));
    }

    private Map<Integer, EngineVersion> deployedIndex(List<EngineVersion> versions) {
        return versions.stream()
                .filter(v -> v.getEngineId() != null && isDeployed(v))
                .collect(Collectors.toMap(EngineVersion::getEngineId, v -> v, (a, b) -> a));
    }

    private ListData loadListData() {
        ListData data = new ListData();
        try {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            for (ListEntryEntity entry : listEntryMapper.selectEnabledEntries(now)) {
                if (entry.getListId() == null || StringUtils.isBlank(entry.getEntryValue())) {
                    continue;
                }
                data.entries.computeIfAbsent(entry.getListId(), k -> new java.util.HashSet<>())
                        .add(entry.getEntryValue().trim());
            }
            for (Map<String, Object> row : listEntryMapper.selectEnabledListNames()) {
                Object id = row.get("id");
                Object name = row.get("list_name");
                if (id instanceof Number && name != null) {
                    data.names.put(((Number) id).intValue(), String.valueOf(name));
                }
            }
            data.nextBoundary = listEntryMapper.selectNextEffectiveBoundary(now);
        } catch (Exception e) {
            log.warn("List DB load failed (list nodes will treat entries as no-hit): {}", e.getMessage());
        }
        return data;
    }

    public java.time.LocalDateTime getAppliedListBoundary() {
        return appliedListBoundary;
    }


    private EngineSnapshot.Channel buildChannel(Integer versionId, Integer engineId,
                                                FlowPublishEntity pub,
                                                VersionInputs inputs) {
        Map<String, EngineNode> nodeMap = new LinkedHashMap<>();
        inputs.getNodes().forEach((code, node) -> {
            if (code != null && node != null) {
                nodeMap.put(code, node);
            }
        });

        Map<String, EngineSnapshot.PolicyConfig> policyMap = new LinkedHashMap<>();
        Map<String, EngineSnapshot.ScorecardConfig> scorecardMap = new LinkedHashMap<>();
        Map<String, String> nameMap = new HashMap<>();
        Map<String, EngineSnapshot.DecisionTableConfig> dtMap = new LinkedHashMap<>();

        for (EngineNode node : nodeMap.values()) {
            try {
                parseNode(node, policyMap, scorecardMap, nameMap, inputs, dtMap);
            } catch (Exception e) {
                log.warn("Failed to parse config of node {}: {}", node.getNodeCode(), e.getMessage());
            }
        }

        return new EngineSnapshot.Channel(
                versionId,
                pub == null ? null : pub.getId(),
                pub == null ? null : pub.getPublishSeq(),
                pub == null || pub.getTrafficWeight() == null ? 100 : pub.getTrafficWeight(),
                pub != null && pub.getShadow() != null && pub.getShadow() == 1,
                nodeMap, policyMap, scorecardMap, nameMap, dtMap, inputs.getEdges(), engineId);
    }

    private void parseNode(EngineNode node,
                           Map<String, EngineSnapshot.PolicyConfig> policyMap,
                           Map<String, EngineSnapshot.ScorecardConfig> scorecardMap,
                           Map<String, String> nameMap,
                           VersionInputs inputs,
                           Map<String, EngineSnapshot.DecisionTableConfig> dtMap) {
        Integer type = node.getNodeType();
        if (type == null) {
            return;
        }
        if (type == NodeTypes.POLICY) {
            NodeJson nodeJson = parseNodeJson(node);
            policyMap.put(node.getNodeCode(), buildPolicyConfig(node, nodeJson, nameMap, inputs));
            return;
        }
        if (type == NodeTypes.DECISION) {
            Integer tableId = resolveDecisionTableId(node);
            if (tableId != null) {
                VersionInputs.DecisionTableBundle bundle = inputs.getDecisionTableById().get(tableId);
                if (bundle != null && bundle.getTable() != null) {
                    dtMap.put(node.getNodeCode(), buildDecisionTableConfig(tableId, bundle));
                } else {
                    log.warn("Decision node {} references decision table {} that is missing or disabled", node.getNodeCode(), tableId);
                }
            } else {
                EngineSnapshot.DecisionTableConfig inline = compileInlineDecision(node);
                if (inline != null) {
                    dtMap.put(node.getNodeCode(), inline);
                }
            }
            return;
        }
        if (type == NodeTypes.SCORECARD) {
            Integer cardId = resolveCardId(node);
            if (cardId == null) {
                return;
            }
            ScorecardEntity card = inputs.getScorecardById().get(cardId);
            if (card != null) {
                scorecardMap.put(node.getNodeCode(), buildScorecardConfig(cardId, card));
            }
        }
    }

    private EngineSnapshot.PolicyConfig buildPolicyConfig(EngineNode node, NodeJson nodeJson,
                                                          Map<String, String> nameMap,
                                                          VersionInputs inputs) {
        List<Integer> ruleIds = nodeJson.allRuleIds();

        if (ruleIds.isEmpty() && node.getNodeId() != null) {
            List<Integer> relIds = inputs.getRelRuleIdsByNode().get(node.getNodeId());
            if (relIds != null) {
                ruleIds = relIds;
            }
        }

        if (ruleIds.isEmpty()) {
            return new EngineSnapshot.PolicyConfig(nodeJson, Collections.emptyList(), Collections.emptyMap());
        }

        List<RuleEntity> rules = ruleIds.stream()
                .map(id -> inputs.getRulesById().get(id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Integer, RuleEntity> ruleById = rules.stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(RuleEntity::getId, r -> r, (a, b) -> a));

        ruleById.forEach((id, rule) -> {
            if (StringUtils.isNotBlank(rule.getCode()) && StringUtils.isNotBlank(rule.getName())) {
                nameMap.putIfAbsent(rule.getCode(), rule.getName());
                String code = rule.getCode();
                if (code.length() > 1 && (code.startsWith("r_") || code.startsWith("r"))) {
                    String stripped = code.startsWith("r_") ? code.substring(2) : code.substring(1);
                    nameMap.putIfAbsent(stripped, rule.getName());
                }
            }
        });

        List<EngineSnapshot.RulePlan> plans = new ArrayList<>();
        rules.stream()
                .sorted(Comparator.comparing(r -> r.getPriority() == null ? 100 : r.getPriority()))
                .forEach(r -> {
                    Condition cond = AstAssembler.assemble(inputs.getCondByRule().get(r.getId()));
                    if (cond == null) {
                        log.warn("Rule {} ({}) missing condition AST, skipped in this snapshot", r.getId(), r.getName());
                        return;
                    }
                    ResultType rt = resolveResultType(r);
                    if (rt == null) {
                        log.error("Cannot resolve result type for rule {} ({}): result_type_v2={}, rule_type={}; "
                                        + "skipped in this snapshot",
                                r.getId(), r.getName(), r.getResultTypeV2(), r.getRuleType());
                        return;
                    }
                    plans.add(new EngineSnapshot.RulePlan(
                            r.getId(), r.getCode(), r.getName(), cond, rt,
                            r.getScoreValue(), r.getPriority() == null ? 100 : r.getPriority()));
                });

        return new EngineSnapshot.PolicyConfig(nodeJson, plans, ruleById);
    }

    static ResultType resolveResultType(RuleEntity r) {
        if (ResultType.isNotMigrated(r.getResultTypeV2())) {
            if (r.getRuleType() != null) {
                if (r.getRuleType() == 1) {
                    return r.getScoreValue() != null && r.getScoreValue() < 0
                            ? ResultType.SUB_SCORE : ResultType.ADD_SCORE;
                }
                return r.getRuleAudit() != null && r.getRuleAudit() == 3
                        ? ResultType.MANUAL : ResultType.DENY;
            }
            return null;
        }
        return ResultType.tryParse(r.getResultTypeV2());
    }

    private Integer resolveDecisionTableId(EngineNode node) {
        try {
            JsonNode cfg = objectMapper.readTree(node.getNodeJson());
            JsonNode id = cfg.path("decision_table_id");
            return id.isInt() ? id.asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private EngineSnapshot.DecisionTableConfig buildDecisionTableConfig(
            Integer tableId, VersionInputs.DecisionTableBundle bundle) {
        com.helix.engine.entity.DecisionTableEntity table = bundle.getTable();

        Map<Integer, com.helix.engine.entity.DtColumnEntity> condCols = new LinkedHashMap<>();
        for (com.helix.engine.entity.DtColumnEntity c : bundle.getColumns()) {
            if (c.getColType() != null && c.getColType() == 1
                    && c.getFieldCode() != null && !c.getFieldCode().trim().isEmpty()) {
                condCols.put(c.getId(), c);
            }
        }

        List<EngineSnapshot.DecisionTableConfig.TableRow> rows = new ArrayList<>();
        for (com.helix.engine.entity.DtRowEntity row : bundle.getRows()) {
            if (row.getEnabled() == null || row.getEnabled() != 1) {
                continue;
            }
            Map<Integer, String> cells = bundle.getCellsByRow()
                    .getOrDefault(row.getId(), Collections.emptyMap());

            List<com.helix.engine.rule.ast.Condition> leaves = new ArrayList<>();
            for (Map.Entry<Integer, com.helix.engine.entity.DtColumnEntity> e : condCols.entrySet()) {
                String value = cells.get(e.getKey());
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                com.helix.engine.entity.DtColumnEntity col = e.getValue();
                com.helix.engine.rule.ast.Operator op =
                        com.helix.engine.rule.ast.Operator.parse(col.getOperator());
                if (op == null) {
                    op = com.helix.engine.rule.ast.Operator.EQ;
                }
                List<String> values = new ArrayList<>();
                if (op == com.helix.engine.rule.ast.Operator.BETWEEN) {
                    String[] parts = value.split(",");
                    values.add(parts.length > 0 ? parts[0].trim() : "");
                    values.add(parts.length > 1 ? parts[1].trim() : "");
                } else {
                    for (String p : value.split(",")) {
                        if (!p.trim().isEmpty()) {
                            values.add(p.trim());
                        }
                    }
                    if (values.isEmpty()) {
                        values.add(value.trim());
                    }
                }
                leaves.add(new com.helix.engine.rule.ast.Condition.Leaf(
                        col.getFieldCode().trim(), op, values));
            }
            if (leaves.isEmpty()) {
                log.warn("Decision table {} row {} has no valid condition, skipped (avoids always-true row)", table.getCode(), row.getRowNo());
                continue;
            }
            com.helix.engine.rule.ast.Condition cond = leaves.size() == 1
                    ? leaves.get(0)
                    : new com.helix.engine.rule.ast.Condition.Group(
                            com.helix.engine.rule.ast.Logic.AND, leaves);
            rows.add(new EngineSnapshot.DecisionTableConfig.TableRow(
                    row.getRowNo(), cond, row.getExpression(),
                    row.getResultValue(), row.getResultType(), row.getScoreValue()));
        }
        rows.sort(java.util.Comparator.comparing(
                EngineSnapshot.DecisionTableConfig.TableRow::getRowNo,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return new EngineSnapshot.DecisionTableConfig(
                tableId, table.getCode(), table.getName(), table.getHitPolicy(), rows);
    }


    public static EngineSnapshot.DecisionTableConfig compileInlineDecision(EngineNode node) {
        String nodeJson = node.getNodeJson();
        if (nodeJson == null || nodeJson.trim().isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode cfg =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(nodeJson);
            com.fasterxml.jackson.databind.JsonNode conditions = cfg.path("conditions");
            if (!conditions.isArray() || conditions.size() == 0) {
                return null;
            }
            List<EngineSnapshot.DecisionTableConfig.TableRow> rows = new ArrayList<>();
            int rowNo = 0;
            for (com.fasterxml.jackson.databind.JsonNode cond : conditions) {
                rowNo++;
                com.fasterxml.jackson.databind.JsonNode formula = cond.path("formula");
                com.helix.engine.rule.ast.Condition ast;
                String expression;
                if (formula.isArray() && formula.size() > 0) {
                    ast = compileFormulaNode(formula, node.getNodeCode(), rowNo);
                    expression = describeFormulaNode(formula);
                } else if (!cond.has("formula")) {
                    ast = compileRangeNode(cond, cfg, node.getNodeCode(), rowNo);
                    expression = "Range [" + cond.path("min").asText() + ", "
                            + cond.path("max").asText() + "]";
                } else {
                    log.warn("Decision node {} row {}: formula is empty, dropping the whole row", node.getNodeCode(), rowNo);
                    continue;
                }
                if (ast == null) {
                    continue;
                }
                rows.add(new EngineSnapshot.DecisionTableConfig.TableRow(
                        rowNo, ast, expression,
                        cond.path("result").asText(null), null, null));
            }
            if (rows.isEmpty()) {
                log.warn("None of the inline conditions of decision node {} compiled; not mounted (treated as unconfigured at runtime)",
                        node.getNodeCode());
                return null;
            }
            rows.sort(java.util.Comparator.comparing(
                    EngineSnapshot.DecisionTableConfig.TableRow::getRowNo,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
            return new EngineSnapshot.DecisionTableConfig(
                    null, node.getNodeCode(), "Inline decision(" + node.getNodeName() + ")", "FIRST", rows);
        } catch (Exception e) {
            log.warn("Inline config compilation failed for decision node {}: {}", node.getNodeCode(), e.getMessage());
            return null;
        }
    }

    private static com.helix.engine.rule.ast.Condition compileFormulaNode(
            com.fasterxml.jackson.databind.JsonNode formula, String nodeCode, int rowNo) {
        com.helix.engine.rule.ast.Condition acc = null;
        for (com.fasterxml.jackson.databind.JsonNode f : formula) {
            com.helix.engine.rule.ast.Condition item = compileItemNode(f, nodeCode, rowNo);
            if (item == null) {
                return null;
            }
            if (acc == null) {
                acc = item;
                continue;
            }
            com.helix.engine.rule.ast.Logic logic =
                    "or".equalsIgnoreCase(f.path("sign").asText("and"))
                            ? com.helix.engine.rule.ast.Logic.OR
                            : com.helix.engine.rule.ast.Logic.AND;
            List<com.helix.engine.rule.ast.Condition> children = new ArrayList<>(2);
            children.add(acc);
            children.add(item);
            acc = new com.helix.engine.rule.ast.Condition.Group(logic, children);
        }
        return acc;
    }

    private static com.helix.engine.rule.ast.Condition compileItemNode(
            com.fasterxml.jackson.databind.JsonNode f, String nodeCode, int rowNo) {
        String field = f.path("field_code").asText(null);
        if (field == null || field.trim().isEmpty()) {
            log.warn("Decision node {} row {}: condition missing field_code, dropping the whole row", nodeCode, rowNo);
            return null;
        }
        com.helix.engine.rule.ast.Operator op =
                com.helix.engine.rule.ast.Operator.parse(f.path("operator").asText("=="));
        if (op == null) {
            log.warn("Decision node {} row {}: unrecognized condition operator {}, dropping the whole row",
                    nodeCode, rowNo, f.path("operator").asText());
            return null;
        }
        String value = f.path("result").asText("");
        List<String> values;
        if (op.isMultiValue()) {
            values = new ArrayList<>();
            for (String p : value.split(",")) {
                values.add(p.trim());
            }
        } else {
            values = Collections.singletonList(value);
        }
        return new com.helix.engine.rule.ast.Condition.Leaf(field, op, values);
    }

    private static com.helix.engine.rule.ast.Condition compileRangeNode(
            com.fasterxml.jackson.databind.JsonNode cond,
            com.fasterxml.jackson.databind.JsonNode cfg, String nodeCode, int rowNo) {
        com.fasterxml.jackson.databind.JsonNode inputArr = cfg.path("input");
        if (!inputArr.isArray() || inputArr.size() == 0) {
            log.warn("Decision node {} row {}: min/max range missing input field definition, dropping the whole row", nodeCode, rowNo);
            return null;
        }
        String field = inputArr.get(0).path("field_code").asText(null);
        if (field == null || field.trim().isEmpty()) {
            log.warn("Decision node {} row {}: min/max range missing field, dropping the whole row", nodeCode, rowNo);
            return null;
        }
        String minText = cond.hasNonNull("min") ? cond.get("min").asText() : "-Infinity";
        String maxText = cond.hasNonNull("max") ? cond.get("max").asText() : "Infinity";
        return new com.helix.engine.rule.ast.Condition.Leaf(field,
                com.helix.engine.rule.ast.Operator.BETWEEN,
                java.util.Arrays.asList(minText, maxText));
    }

    private static String describeFormulaNode(com.fasterxml.jackson.databind.JsonNode formula) {
        StringBuilder sb = new StringBuilder();
        for (com.fasterxml.jackson.databind.JsonNode f : formula) {
            if (sb.length() > 0) {
                sb.append(" ").append(f.path("sign").asText("and").toUpperCase()).append(" ");
            }
            sb.append(f.path("field_code").asText())
                    .append(" ").append(f.path("operator").asText())
                    .append(" ").append(f.path("result").asText());
        }
        return sb.toString();
    }


    public VersionInputs collectLiveInputs(Integer versionId) {
        VersionInputs vi = new VersionInputs();

        List<EngineNode> nodes = engineNodeMapper.selectList(
                new LambdaQueryWrapper<EngineNode>().eq(EngineNode::getVersionId, versionId));
        for (EngineNode n : nodes) {
            if (n.getNodeCode() != null) {
                vi.getNodes().put(n.getNodeCode(), n);
            }
        }

        Map<String, List<String>> edges = new LinkedHashMap<>();
        for (FlowEdgeEntity edge : flowEdgeMapper.selectByVersionId(versionId)) {
            if (edge.getFromCode() == null || edge.getToCode() == null) {
                continue;
            }
            edges.computeIfAbsent(edge.getFromCode(), k -> new ArrayList<>()).add(edge.getToCode());
        }
        vi.setEdges(edges);

        Map<Integer, List<Integer>> relByNode = new LinkedHashMap<>();
        for (EngineNode n : nodes) {
            if (n.getNodeId() == null) {
                continue;
            }
            List<Integer> ids = nodeKnowledgeRelMapper.selectByNodeId(n.getNodeId()).stream()
                    .filter(r -> r.getKnowledgeType() == null || r.getKnowledgeType() == 1)
                    .map(NodeKnowledgeRel::getKnowledgeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!ids.isEmpty()) {
                relByNode.put(n.getNodeId(), ids);
            }
        }
        vi.setRelRuleIdsByNode(relByNode);

        java.util.Set<Integer> ruleIdSet = new java.util.LinkedHashSet<>();
        for (EngineNode n : vi.getNodes().values()) {
            if (n.getNodeType() != null && n.getNodeType() == NodeTypes.POLICY) {
                ruleIdSet.addAll(parseNodeJson(n).allRuleIds());
            }
        }
        relByNode.values().forEach(ruleIdSet::addAll);

        java.util.Set<Integer> cardIds = new java.util.LinkedHashSet<>();
        for (EngineNode n : vi.getNodes().values()) {
            if (n.getNodeType() != null && n.getNodeType() == NodeTypes.SCORECARD) {
                Integer cardId = resolveCardId(n);
                if (cardId != null) {
                    cardIds.add(cardId);
                }
            }
        }

        if (!ruleIdSet.isEmpty()) {
            List<RuleEntity> rules = ruleMapper.selectEnabledByIds(new ArrayList<>(ruleIdSet));
            for (RuleEntity r : rules) {
                if (r.getId() != null) {
                    vi.getRulesById().put(r.getId(), r);
                }
            }
            List<RuleConditionEntity> conds = ruleConditionMapper.selectByRuleIds(new ArrayList<>(ruleIdSet));
            for (RuleConditionEntity ce : conds) {
                if (ce.getRuleId() == null) {
                    continue;
                }
                vi.getCondByRule().computeIfAbsent(ce.getRuleId(), k -> new ArrayList<>()).add(ce);
            }
        }

        java.util.Set<Integer> dtIds = new java.util.LinkedHashSet<>();
        for (EngineNode n : vi.getNodes().values()) {
            if (n.getNodeType() != null && n.getNodeType() == NodeTypes.DECISION) {
                Integer tid = resolveDecisionTableId(n);
                if (tid != null) {
                    dtIds.add(tid);
                }
            }
        }
        for (Integer tid : dtIds) {
            com.helix.engine.entity.DecisionTableEntity t = decisionTableMapper.selectById(tid);
            if (t == null || t.getStatus() == null || t.getStatus() != 1) {
                continue;
            }
            VersionInputs.DecisionTableBundle b = new VersionInputs.DecisionTableBundle();
            b.setTable(t);
            b.setColumns(dtColumnMapper.selectList(new LambdaQueryWrapper<com.helix.engine.entity.DtColumnEntity>()
                    .eq(com.helix.engine.entity.DtColumnEntity::getTableId, tid)
                    .orderByAsc(com.helix.engine.entity.DtColumnEntity::getSeq)));
            b.setRows(dtRowMapper.selectList(new LambdaQueryWrapper<com.helix.engine.entity.DtRowEntity>()
                    .eq(com.helix.engine.entity.DtRowEntity::getTableId, tid)
                    .orderByAsc(com.helix.engine.entity.DtRowEntity::getRowNo)));
            Map<Integer, Map<Integer, String>> cells = new LinkedHashMap<>();
            for (com.helix.engine.entity.DtCellEntity cell : dtCellMapper.selectList(
                    new LambdaQueryWrapper<com.helix.engine.entity.DtCellEntity>()
                            .eq(com.helix.engine.entity.DtCellEntity::getTableId, tid))) {
                cells.computeIfAbsent(cell.getRowId(), k -> new LinkedHashMap<>())
                        .put(cell.getColId(), cell.getCellValue());
            }
            b.setCellsByRow(cells);
            vi.getDecisionTableById().put(tid, b);
        }

        if (!cardIds.isEmpty()) {
            for (ScorecardEntity card : scorecardMapper.selectBatchIds(cardIds)) {
                if (card.getId() != null) {
                    vi.getScorecardById().put(card.getId(), card);
                }
            }
        }
        return vi;
    }

    public String materialize(Integer versionId) throws Exception {
        lock.writeLock().lock();
        try {
            return doMaterialize(versionId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String doMaterialize(Integer versionId) throws Exception {
        VersionInputs inputs = collectLiveInputs(versionId);
        String json = objectMapper.writeValueAsString(inputs);

        FlowPublishEntity supersedes = flowPublishMapper.selectActive(versionId);
        if (supersedes != null) {
            supersedes.setStatus(0);
            supersedes.setUpdatedTime(java.time.LocalDateTime.now());
            flowPublishMapper.updateById(supersedes);
        }

        FlowPublishEntity row = new FlowPublishEntity();
        row.setVersionId(versionId);
        row.setArtifact(json);
        String sha = sha256(json);
        row.setArtifactSha256(sha);
        row.setPublishSeq(flowPublishMapper.maxSeq(versionId) + 1);
        row.setStatus(1);
        row.setTrafficWeight(100);
        row.setShadow(0);
        row.setPublishedTime(java.time.LocalDateTime.now());
        flowPublishMapper.insert(row);
        log.info("Publish artifact of version {} persisted: seq={}, sha256={}, {} bytes",
                versionId, row.getPublishSeq(), sha.substring(0, 12), json.length());
        return sha;
    }

    public Map<String, Object> publishNow(Integer versionId) throws Exception {
        lock.writeLock().lock();
        try {
            return doPublishNow(versionId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Map<String, Object> doPublishNow(Integer versionId) throws Exception {
        String sha = doMaterialize(versionId);
        reloadAffectedEngine(versionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("versionId", versionId);
        data.put("artifactSha256", sha);
        data.put("rulePlanCount", holder.getRulePlanCount());
        return data;
    }


    private void reloadAffectedEngine(Integer versionId) {
        String code = engineCodeOfVersion(versionId);
        if (code == null) {
            log.warn("Cannot locate owning engine of version {}, falling back to full reload", versionId);
            doReload();
        } else {
            doReloadEngine(code);
        }
    }

    private String engineCodeOfVersion(Integer versionId) {
        if (versionId == null) {
            return null;
        }
        EngineVersion version = engineVersionMapper.selectById(versionId);
        if (version == null || version.getEngineId() == null) {
            return null;
        }
        Engine engine = engineMapper.selectById(version.getEngineId());
        return engine == null ? null : engine.getCode();
    }


    public List<Map<String, Object>> listActives(Integer versionId) {
        lock.readLock().lock();
        try {
            return doListActives(versionId);
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<Map<String, Object>> doListActives(Integer versionId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FlowPublishEntity p : flowPublishMapper.selectActives(versionId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("publishId", p.getId());
            m.put("publishSeq", p.getPublishSeq());
            m.put("trafficWeight", p.getTrafficWeight());
            m.put("shadow", p.getShadow() != null && p.getShadow() == 1);
            m.put("status", p.getStatus());
            m.put("sha256", p.getArtifactSha256() == null ? null
                    : p.getArtifactSha256().substring(0, Math.min(12, p.getArtifactSha256().length())));
            m.put("remark", p.getRemark());
            m.put("publishedTime", p.getPublishedTime());
            EngineSnapshot.Channel live = findLiveChannel(versionId, p.getId());
            m.put("routed", live != null);
            m.put("routable", live != null && live.isRoutable());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> publishGray(Integer versionId, Integer weight) throws Exception {
        lock.writeLock().lock();
        try {
            return doPublishGray(versionId, weight);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Map<String, Object> doPublishGray(Integer versionId, Integer weight) throws Exception {
        if (weight == null || weight < 0 || weight > 100) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Gray weight must be 0-100");
        }
        List<FlowPublishEntity> actives = flowPublishMapper.selectActives(versionId);
        if (actives.isEmpty()) {
            return publishNow(versionId);
        }
        VersionInputs inputs = collectLiveInputs(versionId);
        String json = objectMapper.writeValueAsString(inputs);
        FlowPublishEntity row = new FlowPublishEntity();
        row.setVersionId(versionId);
        row.setArtifact(json);
        String sha = sha256(json);
        row.setArtifactSha256(sha);
        row.setPublishSeq(flowPublishMapper.maxSeq(versionId) + 1);
        row.setStatus(1);
        row.setTrafficWeight(weight);
        row.setShadow(0);
        row.setPublishedTime(java.time.LocalDateTime.now());
        flowPublishMapper.insert(row);

        FlowPublishEntity top = null;
        for (FlowPublishEntity p : actives) {
            if (p.getShadow() != null && p.getShadow() == 1) {
                continue;
            }
            if (top == null || weightOf(p) > weightOf(top)
                    || (weightOf(p) == weightOf(top) && seqOf(p) > seqOf(top))) {
                top = p;
            }
        }
        if (top != null && weightOf(top) == 100) {
            top.setTrafficWeight(100 - weight);
            top.setUpdatedTime(java.time.LocalDateTime.now());
            flowPublishMapper.updateById(top);
        }
        reloadAffectedEngine(versionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("versionId", versionId);
        data.put("newPublishId", row.getId());
        data.put("grayWeight", weight);
        data.put("artifactSha256", sha.substring(0, Math.min(12, sha.length())));
        data.put("tracks", doListActives(versionId));
        return data;
    }

    public void setWeight(Long publishId, Integer weight) {
        lock.writeLock().lock();
        try {
            doSetWeight(publishId, weight);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void doSetWeight(Long publishId, Integer weight) {
        if (weight == null || weight < 0 || weight > 100) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Weight must be 0-100");
        }
        FlowPublishEntity row = requirePublish(publishId);
        row.setTrafficWeight(weight);
        row.setUpdatedTime(java.time.LocalDateTime.now());
        flowPublishMapper.updateById(row);
        reloadAffectedEngine(row.getVersionId());
    }

    public void setShadow(Long publishId, boolean on) {
        lock.writeLock().lock();
        try {
            doSetShadow(publishId, on);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void doSetShadow(Long publishId, boolean on) {
        FlowPublishEntity row = requirePublish(publishId);
        row.setShadow(on ? 1 : 0);
        row.setUpdatedTime(java.time.LocalDateTime.now());
        flowPublishMapper.updateById(row);
        reloadAffectedEngine(row.getVersionId());
        if (!on && weightOf(row) == 0) {
            log.info("Track {} exited shadow mode with weight 0 and is paused; adjust the weight to resume traffic splitting", publishId);
        }
    }

    public void promote(Long publishId) {
        lock.writeLock().lock();
        try {
            doPromote(publishId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void doPromote(Long publishId) {
        FlowPublishEntity row = requirePublish(publishId);
        row.setStatus(1);
        row.setShadow(0);
        row.setTrafficWeight(100);
        row.setUpdatedTime(java.time.LocalDateTime.now());
        flowPublishMapper.updateById(row);
        for (FlowPublishEntity other : flowPublishMapper.selectActives(row.getVersionId())) {
            if (other.getId() != null && other.getId().equals(row.getId())) {
                continue;
            }
            other.setTrafficWeight(0);
            other.setUpdatedTime(java.time.LocalDateTime.now());
            flowPublishMapper.updateById(other);
        }
        reloadAffectedEngine(row.getVersionId());
        log.info("Track {} took over all traffic of version {}; other tracks set to weight 0 (artifacts kept for instant rollback)",
                publishId, row.getVersionId());
    }

    private FlowPublishEntity requirePublish(Long publishId) {
        FlowPublishEntity row = publishId == null ? null : flowPublishMapper.selectById(publishId);
        if (row == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Publish track not found: " + publishId);
        }
        return row;
    }

    public List<Map<String, Object>> listTracksOf(Long publishId) {
        lock.readLock().lock();
        try {
            return doListTracksOf(publishId);
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<Map<String, Object>> doListTracksOf(Long publishId) {
        FlowPublishEntity row = requirePublish(publishId);
        return doListActives(row.getVersionId());
    }

    private EngineSnapshot.Channel findLiveChannel(Integer versionId, Long publishId) {
        for (EngineSnapshot.Channel c : holder.get().getChannels(versionId)) {
            if (publishId == null ? c.isLiveFallback() : publishId.equals(c.getPublishId())) {
                return c;
            }
        }
        return null;
    }

    private static int weightOf(FlowPublishEntity p) {
        return p.getTrafficWeight() == null ? 0 : p.getTrafficWeight();
    }

    private static int seqOf(FlowPublishEntity p) {
        return p.getPublishSeq() == null ? 0 : p.getPublishSeq();
    }

    private String sha256(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private EngineSnapshot.ScorecardConfig buildScorecardConfig(Integer cardId, ScorecardEntity card) {
        JsonNode dims = null;
        if (StringUtils.isNotBlank(card.getScore())) {
            try {
                dims = objectMapper.readTree(card.getScore());
            } catch (Exception e) {
                log.warn("Failed to parse dimensions of scorecard {}: {}", card.getCode(), e.getMessage());
            }
        }
        return new EngineSnapshot.ScorecardConfig(cardId, card.getName(), dims);
    }

    private NodeJson parseNodeJson(EngineNode node) {
        if (StringUtils.isBlank(node.getNodeJson())) {
            return new NodeJson();
        }
        try {
            NodeJson nj = objectMapper.readValue(node.getNodeJson(), NodeJson.class);
            return nj == null ? new NodeJson() : nj;
        } catch (Exception e) {
            log.warn("Failed to parse node_json of node {}: {}", node.getNodeCode(), e.getMessage());
            return new NodeJson();
        }
    }

    private Integer resolveCardId(EngineNode node) {
        if (node.getCardId() != null) {
            return node.getCardId().intValue();
        }
        String json = node.getNodeJson();
        if (StringUtils.isBlank(json)) {
            return null;
        }
        String trimmed = json.trim();
        try {
            if (trimmed.matches("\\d+")) {
                return Integer.valueOf(trimmed);
            }
            JsonNode cfg = objectMapper.readTree(trimmed);
            if (cfg.has("cardId")) {
                return cfg.path("cardId").asInt();
            }
            if (cfg.has("id")) {
                return cfg.path("id").asInt();
            }
        } catch (Exception e) {
            log.warn("Failed to parse cardId of scorecard node {}", node.getNodeCode());
        }
        return null;
    }

    private boolean isDeployed(EngineVersion v) {
        return v.getBootState() != null && v.getBootState() == 1
                && (v.getStatus() == null || v.getStatus() == 1);
    }
}
