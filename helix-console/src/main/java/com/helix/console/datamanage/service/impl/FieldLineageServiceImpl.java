package com.helix.console.datamanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.datamanage.dto.FieldDetailVO;
import com.helix.console.datamanage.dto.FieldLineageDTO;
import com.helix.console.datamanage.dto.LineageUsageItem;
import com.helix.console.datamanage.dto.NodeRef;
import com.helix.console.datamanage.entity.Field;
import com.helix.console.datamanage.entity.ListDb;
import com.helix.console.datamanage.mapper.FieldMapper;
import com.helix.console.datamanage.mapper.ListDbMapper;
import com.helix.console.datamanage.service.FieldLineageService;
import com.helix.console.engine.entity.NodeKnowledgeRel;
import com.helix.console.engine.entity.NodeRefRow;
import com.helix.console.datamanage.dto.GraphEdge;
import com.helix.console.datamanage.dto.GraphNode;
import com.helix.console.datamanage.dto.LineageGraphDTO;
import com.helix.console.engine.enums.KnowledgeType;
import com.helix.console.engine.enums.NodeType;
import com.helix.console.engine.entity.Engine;
import com.helix.console.engine.entity.EngineNode;
import com.helix.console.engine.entity.EngineVersion;
import com.helix.console.engine.mapper.EngineMapper;
import com.helix.console.engine.mapper.EngineVersionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.helix.console.engine.mapper.DtColumnMapper;
import com.helix.console.engine.mapper.EngineNodeMapper;
import com.helix.console.engine.mapper.NodeKnowledgeRelMapper;
import com.helix.console.engine.entity.DecisionTableEntity;
import com.helix.console.engine.mapper.DecisionTableMapper;
import com.helix.console.knowledge.dto.ScorecardDimensionDTO;
import com.helix.console.knowledge.entity.Rule;
import com.helix.console.knowledge.entity.RuleCondition;
import com.helix.console.knowledge.mapper.RuleConditionMapper;
import com.helix.console.knowledge.mapper.RuleMapper;
import com.helix.console.knowledge.entity.Scorecard;
import com.helix.console.knowledge.mapper.ScorecardMapper;
import com.helix.console.system.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Field lineage reverse-lookup implementation.
 *
 * <p>Lineage is physically stored in three separate sources; this service consolidates
 * them into one reverse-lookup result:</p>
 * <ul>
 *   <li>Rules: {@code t_rule_condition.field_code}</li>
 *   <li>Scorecards: {@code t_scorecard.score} JSON dimension field</li>
 *   <li>Decision tables: {@code t_dt_column.field_code}</li>
 *   <li>List DBs: {@code t_list_db.table_column / query_field} (stores comma-joined field ids)</li>
 * </ul>
 *
 * <p>Knowledge objects are then mapped back to concrete decision flow nodes via
 * {@code t_node_knowledge_rel} (rule=1/complex rule=4, scorecard=2) or via node
 * {@code node_json} (decision table decision_table_id, list DB list_db_ids).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldLineageServiceImpl implements FieldLineageService {

    private final FieldMapper fieldMapper;
    private final RuleConditionMapper ruleConditionMapper;
    private final RuleMapper ruleMapper;
    private final ScorecardMapper scorecardMapper;
    private final DtColumnMapper dtColumnMapper;
    private final DecisionTableMapper decisionTableMapper;
    private final ListDbMapper listDbMapper;
    private final NodeKnowledgeRelMapper nodeKnowledgeRelMapper;
    private final EngineNodeMapper engineNodeMapper;
    private final EngineMapper engineMapper;
    private final EngineVersionMapper engineVersionMapper;
    private final ObjectMapper objectMapper;

    private static final List<Integer> RULE_TYPES = Arrays.asList(
            KnowledgeType.RULE.getCode(), KnowledgeType.COMPLEX_RULE.getCode());
    private static final Integer SCORECARD_TYPE = KnowledgeType.SCORECARD.getCode();

    @Override
    public FieldLineageDTO lineage(Integer fieldId) {
        Field field = loadVisibleField(fieldId);
        FieldLineageDTO dto = new FieldLineageDTO();
        dto.setFieldId(field.getId());
        dto.setFieldEn(field.getFieldEn());
        dto.setFieldCn(field.getFieldCn());
        List<Long> organs = TenantScope.visibleOrgans();

        dto.setRules(lineageRules(field.getFieldEn(), organs));
        dto.setScorecards(lineageScorecards(field.getFieldEn(), organs));
        dto.setDecisionTables(lineageDecisionTables(field.getFieldEn(), organs));
        dto.setListDbs(lineageListDbs(field.getId(), organs));
        return dto;
    }

    @Override
    public FieldDetailVO detail(Integer fieldId) {
        Field field = loadVisibleField(fieldId);
        FieldDetailVO vo = new FieldDetailVO();
        vo.setId(field.getId());
        vo.setFieldEn(field.getFieldEn());
        vo.setFieldCn(field.getFieldCn());
        vo.setFieldTypeid(field.getFieldTypeid());
        vo.setValueType(field.getValueType());
        vo.setValueScope(field.getValueScope());
        vo.setIsDerivative(field.getIsDerivative());
        vo.setIsOutput(field.getIsOutput());

        // Derived-field dependency resolution (usedFieldid / origFieldid store comma-joined field ids)
        vo.setUsedFields(resolveFieldBriefs(field.getUsedFieldid()));
        vo.setOrigFields(resolveFieldBriefs(field.getOrigFieldid()));

        vo.setReferencedBy(lineage(fieldId).getTotal());
        return vo;
    }

    // ------------------------------------------------------------------ Decision-flow-level lineage graph

    @Override
    public LineageGraphDTO graph(Integer versionId) {
        if (versionId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Decision flow version id must not be empty");
        }
        EngineVersion ev = engineVersionMapper.selectById(versionId);
        if (ev == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Decision flow version not found or already deleted");
        }
        List<Long> organs = TenantScope.visibleOrgans();
        Engine engine = engineMapper.selectById(ev.getEngineId());
        if (engine == null || !isVisible(engine.getOrganId(), organs)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Decision flow version is not visible");
        }

        LineageGraphDTO dto = new LineageGraphDTO();
        GraphNode versionNode = new GraphNode();
        versionNode.setId("V:" + versionId);
        versionNode.setType("VERSION");
        versionNode.setRefId(versionId.longValue());
        versionNode.setSubType("ENGINE_VERSION");
        versionNode.setLabel(engine.getName() + " v" + ev.getVersion() + "." + ev.getSubVersion());
        dto.setVersion(versionNode);

        List<EngineNode> nodes = engineNodeMapper.selectByVersionId(versionId);
        if (nodes.isEmpty()) {
            return dto;
        }

        Map<Integer, EngineNode> nodeById = new HashMap<>();
        List<Integer> nodeIds = new ArrayList<>();
        for (EngineNode n : nodes) {
            nodeById.put(n.getNodeId(), n);
            nodeIds.add(n.getNodeId());
        }

        // Knowledge objects referenced by each node (globally deduplicated)
        Map<Integer, Set<KnowledgeRef>> nodeKnowledge = new HashMap<>();
        Set<KnowledgeRef> allRefs = new HashSet<>();
        List<NodeKnowledgeRel> rels = nodeKnowledgeRelMapper.selectList(
                new LambdaQueryWrapper<NodeKnowledgeRel>().in(NodeKnowledgeRel::getNodeId, nodeIds));
        for (NodeKnowledgeRel rel : rels) {
            KKind kind = toKind(rel.getKnowledgeType());
            if (kind == null) {
                continue;
            }
            KnowledgeRef kr = new KnowledgeRef(kind, rel.getKnowledgeId());
            allRefs.add(kr);
            nodeKnowledge.computeIfAbsent(rel.getNodeId(), k -> new HashSet<>()).add(kr);
        }
        // node_json: scorecard cardId / decision table decision_table_id / list DB list_db_ids
        for (EngineNode n : nodes) {
            String json = n.getNodeJson();
            if (StringUtils.isBlank(json)) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                if (root.hasNonNull("cardId")) {
                    addRef(nodeKnowledge, allRefs, n.getNodeId(), KKind.SCORECARD, root.get("cardId").asInt());
                }
                if (root.hasNonNull("decision_table_id")) {
                    addRef(nodeKnowledge, allRefs, n.getNodeId(), KKind.DECISION_TABLE,
                            root.get("decision_table_id").asInt());
                }
                if (root.hasNonNull("list_db_ids") && root.get("list_db_ids").isArray()) {
                    for (JsonNode e : root.get("list_db_ids")) {
                        if (e.isInt()) {
                            addRef(nodeKnowledge, allRefs, n.getNodeId(), KKind.LIST_DB, e.asInt());
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fault tolerance: a malformed node config must not break the whole graph
            }
        }

        // Visible field index: fieldEn -> Field, id -> Field
        Map<String, Field> fieldByEn = new HashMap<>();
        Map<Integer, Field> fieldById = new HashMap<>();
        for (Field f : fieldMapper.selectList(null)) {
            if (f.getFieldEn() != null) {
                fieldByEn.put(f.getFieldEn(), f);
            }
            fieldById.put(f.getId(), f);
        }

        Map<String, GraphNode> gNodeMap = new HashMap<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();

        // Nodes -> version
        for (EngineNode n : nodes) {
            GraphNode gn = new GraphNode();
            gn.setId("N:" + n.getNodeId());
            gn.setType("NODE");
            gn.setRefId(n.getNodeId().longValue());
            gn.setLabel(n.getNodeName());
            NodeType nt = NodeType.of(n.getNodeType());
            gn.setSubType(nt != null ? nt.getDesc() : "Node");
            gNodeMap.put(gn.getId(), gn);
            addEdge(edges, edgeKeys, gn.getId(), versionNode.getId(), "NODE_VERSION");
        }

        int knowledgeCount = 0;
        for (KnowledgeRef kr : allRefs) {
            KnowledgeMeta meta = resolveKnowledge(kr.kind, kr.id);
            if (meta == null || !isVisible(meta.organId, organs)) {
                continue;
            }
            String kId = "K:" + kr.kind.name() + ":" + kr.id;
            GraphNode kNode = new GraphNode();
            kNode.setId(kId);
            kNode.setType("KNOWLEDGE");
            kNode.setRefType(kr.kind.name());
            kNode.setRefId((long) kr.id);
            kNode.setLabel(meta.name != null ? meta.name : (kr.kind.name() + "#" + kr.id));
            kNode.setSubType(knowledgeSubType(kr.kind));
            gNodeMap.put(kId, kNode);
            knowledgeCount++;

            for (Integer fid : collectKnowledgeFieldIds(kr.kind, kr.id, fieldByEn)) {
                if (fid == null) {
                    continue;
                }
                Field f = fieldById.get(fid);
                if (f == null || !isVisible(f.getOrganId(), organs)) {
                    continue;
                }
                String fId = "F:" + fid;
                if (!gNodeMap.containsKey(fId)) {
                    GraphNode fNode = new GraphNode();
                    fNode.setId(fId);
                    fNode.setType("FIELD");
                    fNode.setRefId(fid.longValue());
                    fNode.setLabel(f.getFieldEn() + (f.getFieldCn() != null ? "(" + f.getFieldCn() + ")" : ""));
                    fNode.setSubType(valueTypeLabel(f.getValueType()));
                    gNodeMap.put(fId, fNode);
                }
                addEdge(edges, edgeKeys, fId, kId, "FIELD_KNOWLEDGE");
            }
        }

        // Knowledge objects -> nodes
        for (Map.Entry<Integer, Set<KnowledgeRef>> e : nodeKnowledge.entrySet()) {
            String nId = "N:" + e.getKey();
            for (KnowledgeRef kr : e.getValue()) {
                String kId = "K:" + kr.kind.name() + ":" + kr.id;
                if (gNodeMap.containsKey(kId)) {
                    addEdge(edges, edgeKeys, kId, nId, "KNOWLEDGE_NODE");
                }
            }
        }

        // Merge the VERSION node into nodes (otherwise the VERSION column in the frontend's
        // four-column layout has no node and NODE_VERSION edges would be filtered out entirely)
        List<GraphNode> allNodes = new ArrayList<>(gNodeMap.values());
        allNodes.add(versionNode);
        dto.setNodes(allNodes);
        dto.setEdges(edges);
        dto.setNodeCount(nodes.size());
        dto.setKnowledgeCount(knowledgeCount);
        dto.setFieldCount((int) gNodeMap.values().stream()
                .filter(g -> "FIELD".equals(g.getType())).count());
        return dto;
    }

    private void addRef(Map<Integer, Set<KnowledgeRef>> nodeKnowledge, Set<KnowledgeRef> allRefs,
                        int nodeId, KKind kind, int id) {
        KnowledgeRef kr = new KnowledgeRef(kind, id);
        allRefs.add(kr);
        nodeKnowledge.computeIfAbsent(nodeId, k -> new HashSet<>()).add(kr);
    }

    private KKind toKind(Integer type) {
        if (type == null) {
            return null;
        }
        if (type == KnowledgeType.RULE.getCode() || type == KnowledgeType.COMPLEX_RULE.getCode()) {
            return KKind.RULE;
        }
        if (type == KnowledgeType.SCORECARD.getCode()) {
            return KKind.SCORECARD;
        }
        return null;
    }

    private KnowledgeMeta resolveKnowledge(KKind kind, int id) {
        switch (kind) {
            case RULE: {
                Rule r = ruleMapper.selectById(id);
                if (r == null || (r.getDeleted() != null && r.getDeleted() != 0)) {
                    return null;
                }
                return new KnowledgeMeta(r.getName(), r.getOrganId());
            }
            case SCORECARD: {
                Scorecard s = scorecardMapper.selectById(id);
                if (s == null) {
                    return null;
                }
                return new KnowledgeMeta(s.getName(), s.getOrganId());
            }
            case DECISION_TABLE: {
                DecisionTableEntity t = decisionTableMapper.selectById(id);
                if (t == null || (t.getDeleted() != null && t.getDeleted() != 0)) {
                    return null;
                }
                return new KnowledgeMeta(t.getName(), t.getOrganId());
            }
            case LIST_DB: {
                ListDb db = listDbMapper.selectById(id);
                if (db == null || (db.getStatus() != null && db.getStatus() == -1)) {
                    return null;
                }
                return new KnowledgeMeta(db.getListName(), db.getOrganId());
            }
            default:
                return null;
        }
    }

    private List<Integer> collectKnowledgeFieldIds(KKind kind, int id, Map<String, Field> fieldByEn) {
        switch (kind) {
            case RULE:
                return collectRuleFieldIds(id, fieldByEn);
            case SCORECARD:
                return collectScorecardFieldIds(id, fieldByEn);
            case DECISION_TABLE:
                return collectDtFieldIds(id, fieldByEn);
            case LIST_DB:
                return collectListDbFieldIds(id);
            default:
                return new ArrayList<>();
        }
    }

    private List<Integer> collectRuleFieldIds(Integer ruleId, Map<String, Field> fieldByEn) {
        List<RuleCondition> conds = ruleConditionMapper.selectList(
                new LambdaQueryWrapper<RuleCondition>()
                        .eq(RuleCondition::getRuleId, ruleId)
                        .eq(RuleCondition::getDeleted, 0));
        List<Integer> ids = new ArrayList<>();
        for (RuleCondition c : conds) {
            if (c.getFieldCode() != null) {
                Field f = fieldByEn.get(c.getFieldCode());
                if (f != null) {
                    ids.add(f.getId());
                }
            }
        }
        return ids;
    }

    private List<Integer> collectScorecardFieldIds(Integer cardId, Map<String, Field> fieldByEn) {
        Scorecard sc = scorecardMapper.selectById(cardId);
        if (sc == null) {
            return new ArrayList<>();
        }
        List<Integer> ids = new ArrayList<>();
        for (ScorecardDimensionDTO d : parseDims(sc.getScore())) {
            if (d.getField() != null) {
                Field f = fieldByEn.get(d.getField());
                if (f != null) {
                    ids.add(f.getId());
                }
            }
        }
        return ids;
    }

    private List<Integer> collectDtFieldIds(Integer tableId, Map<String, Field> fieldByEn) {
        List<com.helix.console.engine.entity.DtColumnEntity> cols = dtColumnMapper.selectList(
                new LambdaQueryWrapper<com.helix.console.engine.entity.DtColumnEntity>()
                        .eq(com.helix.console.engine.entity.DtColumnEntity::getTableId, tableId)
                        .eq(com.helix.console.engine.entity.DtColumnEntity::getDeleted, 0));
        List<Integer> ids = new ArrayList<>();
        for (com.helix.console.engine.entity.DtColumnEntity col : cols) {
            if (col.getFieldCode() != null) {
                Field f = fieldByEn.get(col.getFieldCode());
                if (f != null) {
                    ids.add(f.getId());
                }
            }
        }
        return ids;
    }

    private List<Integer> collectListDbFieldIds(Integer listDbId) {
        ListDb db = listDbMapper.selectById(listDbId);
        if (db == null) {
            return new ArrayList<>();
        }
        List<Integer> ids = new ArrayList<>();
        addIds(ids, db.getTableColumn());
        addIds(ids, db.getQueryField());
        return ids;
    }

    private void addIds(List<Integer> ids, String csv) {
        if (StringUtils.isBlank(csv)) {
            return;
        }
        for (String s : csv.split(",")) {
            if (StringUtils.isBlank(s)) {
                continue;
            }
            try {
                ids.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                // Fault tolerance: skip non-numeric fragments
            }
        }
    }

    private String knowledgeSubType(KKind kind) {
        switch (kind) {
            case RULE:
                return "Rule";
            case SCORECARD:
                return "Scorecard";
            case DECISION_TABLE:
                return "Decision Table";
            case LIST_DB:
                return "List DB";
            default:
                return "Knowledge object";
        }
    }

    private String valueTypeLabel(Integer vt) {
        if (vt == null) {
            return "";
        }
        switch (vt) {
            case 0:
                return "Pending";
            case 1:
                return "Numeric";
            case 2:
                return "String";
            case 3:
                return "Enum";
            case 4:
                return "Decimal";
            default:
                return "Value type " + vt;
        }
    }

    private void addEdge(List<GraphEdge> edges, Set<String> keys, String s, String t, String kind) {
        String key = s + "->" + t + ":" + kind;
        if (keys.contains(key)) {
            return;
        }
        keys.add(key);
        GraphEdge e = new GraphEdge();
        e.setSource(s);
        e.setTarget(t);
        e.setKind(kind);
        edges.add(e);
    }

    private enum KKind {
        RULE, SCORECARD, DECISION_TABLE, LIST_DB
    }

    private static final class KnowledgeRef {
        final KKind kind;
        final int id;

        KnowledgeRef(KKind kind, int id) {
            this.kind = kind;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof KnowledgeRef)) {
                return false;
            }
            KnowledgeRef r = (KnowledgeRef) o;
            return kind == r.kind && id == r.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, id);
        }
    }

    private static final class KnowledgeMeta {
        final String name;
        final Integer organId;

        KnowledgeMeta(String name, Integer organId) {
            this.name = name;
            this.organId = organId;
        }
    }


    // ------------------------------------------------------------------ Rules

    private List<LineageUsageItem> lineageRules(String fieldEn, List<Long> organs) {
        if (StringUtils.isBlank(fieldEn)) {
            return new ArrayList<>();
        }
        List<RuleCondition> conds = ruleConditionMapper.selectList(
                new LambdaQueryWrapper<RuleCondition>()
                        .eq(RuleCondition::getFieldCode, fieldEn)
                        .eq(RuleCondition::getDeleted, 0));
        if (conds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Integer> ruleIds = conds.stream()
                .map(RuleCondition::getRuleId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Rule> rules = ruleMapper.selectEnabledByIds(new ArrayList<>(ruleIds)).stream()
                .filter(r -> r.getDeleted() == null || r.getDeleted() == 0)
                .filter(r -> isVisible(r.getOrganId(), organs))
                .collect(Collectors.toList());

        Map<Integer, List<NodeRef>> refs = resolveByKnowledgeRel(
                rules.stream().map(Rule::getId).collect(Collectors.toSet()), RULE_TYPES);

        return rules.stream().map(r -> {
            LineageUsageItem item = new LineageUsageItem();
            item.setId(r.getId());
            item.setCode(r.getCode());
            item.setName(r.getName());
            item.setOrganId(r.getOrganId());
            item.setRefType("CONDITION");
            item.setUsedIn(refs.getOrDefault(r.getId(), new ArrayList<>()));
            return item;
        }).collect(Collectors.toList());
    }


    // ------------------------------------------------------------------ Scorecards

    private List<LineageUsageItem> lineageScorecards(String fieldEn, List<Long> organs) {
        if (StringUtils.isBlank(fieldEn)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<Scorecard> qw = new LambdaQueryWrapper<>();
        if (organs != null) {
            qw.in(Scorecard::getOrganId, organs);
        }
        qw.in(Scorecard::getStatus, 0, 1);
        List<Scorecard> all = scorecardMapper.selectList(qw);

        List<Scorecard> hit = new ArrayList<>();
        for (Scorecard sc : all) {
            List<ScorecardDimensionDTO> dims = parseDims(sc.getScore());
            if (dims.stream().anyMatch(d -> fieldEn.equals(d.getField()))) {
                hit.add(sc);
            }
        }
        if (hit.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Integer, List<NodeRef>> refs = resolveByKnowledgeRel(
                hit.stream().map(Scorecard::getId).collect(Collectors.toSet()),
                Collections.singletonList(SCORECARD_TYPE));

        return hit.stream().map(sc -> {
            LineageUsageItem item = new LineageUsageItem();
            item.setId(sc.getId());
            item.setCode(sc.getCode());
            item.setName(sc.getName());
            item.setOrganId(sc.getOrganId());
            item.setRefType("DIMENSION");
            // Scorecards rely on node_json.cardId first, with t_node_knowledge_rel as fallback
            List<NodeRef> wired = new ArrayList<>(refs.getOrDefault(sc.getId(), new ArrayList<>()));
            for (NodeRefRow row : engineNodeMapper.selectNodeRefsByScorecard(sc.getId())) {
                NodeRef nr = toNodeRef(row);
                if (wired.stream().noneMatch(x -> x.getNodeId().equals(nr.getNodeId()))) {
                    wired.add(nr);
                }
            }
            item.setUsedIn(wired);
            return item;
        }).collect(Collectors.toList());
    }


    // ------------------------------------------------------------------ Decision Tables

    private List<LineageUsageItem> lineageDecisionTables(String fieldEn, List<Long> organs) {
        if (StringUtils.isBlank(fieldEn)) {
            return new ArrayList<>();
        }
        List<com.helix.console.engine.entity.DtColumnEntity> cols = dtColumnMapper.selectList(
                new LambdaQueryWrapper<com.helix.console.engine.entity.DtColumnEntity>()
                        .eq(com.helix.console.engine.entity.DtColumnEntity::getFieldCode, fieldEn)
                        .eq(com.helix.console.engine.entity.DtColumnEntity::getDeleted, 0));
        if (cols.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Integer> tableIds = cols.stream()
                .map(com.helix.console.engine.entity.DtColumnEntity::getTableId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<DecisionTableEntity> tables = decisionTableMapper.selectBatchIds(new ArrayList<>(tableIds)).stream()
                .filter(t -> t.getDeleted() == null || t.getDeleted() == 0)
                .filter(t -> t.getStatus() != null && t.getStatus() == 1)
                .filter(t -> isVisible(t.getOrganId(), organs))
                .collect(Collectors.toList());

        return tables.stream().map(t -> {
            LineageUsageItem item = new LineageUsageItem();
            item.setId(t.getId());
            item.setCode(t.getCode());
            item.setName(t.getName());
            item.setOrganId(t.getOrganId());
            item.setRefType("COLUMN");
            item.setUsedIn(engineNodeMapper.selectNodeRefsByDecisionTable(t.getId()).stream()
                    .map(this::toNodeRef).collect(Collectors.toList()));
            return item;
        }).collect(Collectors.toList());
    }


    // ------------------------------------------------------------------ List DBs

    private List<LineageUsageItem> lineageListDbs(Integer fieldId, List<Long> organs) {
        if (fieldId == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<ListDb> qw = new LambdaQueryWrapper<>();
        if (organs != null) {
            qw.in(ListDb::getOrganId, organs);
        }
        qw.ne(ListDb::getStatus, -1);
        List<ListDb> all = listDbMapper.selectList(qw);

        List<ListDb> hit = all.stream()
                .filter(db -> containsId(db.getTableColumn(), fieldId)
                        || containsId(db.getQueryField(), fieldId))
                .collect(Collectors.toList());
        if (hit.isEmpty()) {
            return new ArrayList<>();
        }
        return hit.stream().map(db -> {
            LineageUsageItem item = new LineageUsageItem();
            item.setId(db.getId());
            item.setCode(db.getListType());
            item.setName(db.getListName());
            item.setOrganId(db.getOrganId());
            item.setRefType("MATCH_FIELD");
            item.setUsedIn(engineNodeMapper.selectNodeRefsByListDb(db.getId()).stream()
                    .map(this::toNodeRef).collect(Collectors.toList()));
            return item;
        }).collect(Collectors.toList());
    }


    // ------------------------------------------------------------------ Common

    private Map<Integer, List<NodeRef>> resolveByKnowledgeRel(Set<Integer> knowledgeIds, List<Integer> types) {
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return new HashMap<>();
        }
        List<NodeKnowledgeRel> rels = nodeKnowledgeRelMapper.selectList(
                new LambdaQueryWrapper<NodeKnowledgeRel>()
                        .in(NodeKnowledgeRel::getKnowledgeId, knowledgeIds)
                        .in(NodeKnowledgeRel::getKnowledgeType, types));
        if (rels.isEmpty()) {
            return new HashMap<>();
        }
        Map<Integer, List<Integer>> nodeIdsByKid = rels.stream()
                .collect(Collectors.groupingBy(NodeKnowledgeRel::getKnowledgeId,
                        Collectors.mapping(NodeKnowledgeRel::getNodeId, Collectors.toList())));
        Set<Integer> allNodeIds = rels.stream().map(NodeKnowledgeRel::getNodeId).collect(Collectors.toSet());
        Map<Integer, NodeRef> refByNodeId = engineNodeMapper
                .selectNodeRefsByNodeIds(new ArrayList<>(allNodeIds)).stream()
                .collect(Collectors.toMap(NodeRefRow::getNodeId, this::toNodeRef, (a, b) -> a));

        Map<Integer, List<NodeRef>> result = new HashMap<>();
        nodeIdsByKid.forEach((kid, nids) -> result.put(kid, nids.stream()
                .map(refByNodeId::get).filter(Objects::nonNull).collect(Collectors.toList())));
        return result;
    }

    private NodeRef toNodeRef(NodeRefRow r) {
        NodeRef nr = new NodeRef();
        nr.setEngineId(r.getEngineId());
        nr.setEngineCode(r.getEngineCode());
        nr.setEngineName(r.getEngineName());
        nr.setVersionId(r.getVersionId());
        nr.setVersionLabel(r.versionLabel());
        nr.setNodeId(r.getNodeId());
        nr.setNodeCode(r.getNodeCode());
        nr.setNodeName(r.getNodeName());
        return nr;
    }

    private boolean containsId(String csv, Integer id) {
        if (StringUtils.isBlank(csv) || id == null) {
            return false;
        }
        for (String s : csv.split(",")) {
            if (StringUtils.isBlank(s)) {
                continue;
            }
            try {
                if (Integer.parseInt(s.trim()) == id) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Fault tolerance: skip non-numeric fragments
            }
        }
        return false;
    }

    private List<FieldDetailVO.FieldBrief> resolveFieldBriefs(String csv) {
        if (StringUtils.isBlank(csv)) {
            return new ArrayList<>();
        }
        List<Integer> ids = Arrays.stream(csv.split(","))
                .map(String::trim).filter(StringUtils::isNotBlank)
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> organs = TenantScope.visibleOrgans();
        return fieldMapper.selectBatchIds(ids).stream()
                .filter(f -> isVisible(f.getOrganId(), organs))
                .map(f -> {
                    FieldDetailVO.FieldBrief b = new FieldDetailVO.FieldBrief();
                    b.setId(f.getId());
                    b.setFieldEn(f.getFieldEn());
                    b.setFieldCn(f.getFieldCn());
                    return b;
                }).collect(Collectors.toList());
    }

    private List<ScorecardDimensionDTO> parseDims(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ScorecardDimensionDTO>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse scorecard dimensions: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Field loadVisibleField(Integer fieldId) {
        if (fieldId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Field id must not be empty");
        }
        Field field = fieldMapper.selectById(fieldId);
        if (field == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Field not found");
        }
        TenantScope.checkVisible(field.getOrganId() == null ? null : field.getOrganId().longValue());
        return field;
    }

    private boolean isVisible(Integer organId, List<Long> organs) {
        if (organs == null) {
            return true;
        }
        long v = organId == null ? TenantScope.PLATFORM : organId;
        return organs.contains(v);
    }
}
