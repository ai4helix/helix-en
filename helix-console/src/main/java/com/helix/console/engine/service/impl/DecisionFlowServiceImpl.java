package com.helix.console.engine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.engine.dto.GraphVO;
import com.helix.console.engine.dto.NodeMoveDTO;
import com.helix.console.engine.dto.NodeSaveDTO;
import com.helix.console.engine.dto.VersionSaveDTO;
import com.helix.console.engine.entity.EngineNode;
import com.helix.console.engine.entity.EngineVersion;
import com.helix.console.engine.entity.NodeKnowledgeRel;
import com.helix.console.engine.enums.NodeType;
import com.helix.console.engine.mapper.EngineNodeMapper;
import com.helix.console.engine.mapper.EngineVersionMapper;
import com.helix.console.engine.entity.FlowEdgeEntity;
import com.helix.console.engine.mapper.FlowEdgeMapper;
import com.helix.console.engine.mapper.NodeKnowledgeRelMapper;
import com.helix.console.engine.service.DecisionFlowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decision flow canvas service implementation: canvas read/write, structure validation, publish, draft copy.
 *
 * <p>Validation rules (enforced before save/publish): exactly one start node,
 * no dangling references (next pointing to a non-existent node), no cycles, no isolated nodes.
 * Violations throw {@code FLOW_INVALID}, blocking dirty data from entering the DB at the source.</p>
 *
 * <p>Publish semantics: other versions of the same engine go offline (boot_state=0), this version goes
 * online (boot_state=1), and the {@code /engineApi/update} callback to helix-engine rebuilds the snapshot
 * and warms up (failure does not block; manual compensation is possible).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionFlowServiceImpl implements DecisionFlowService {

    private static final String NODE_PREFIX = "n_";
    private static final String EDGE_SHAPE_KEYWORD = "edge";
    /** Edge action markers (the frontend FlowDesigner sends uppercase ADD/REMOVE; equalsIgnoreCase tolerates legacy lowercase). */
    private static final String EDGE_ACTION_ADD = "ADD";
    private static final String EDGE_ACTION_DELETE = "REMOVE";

    private final EngineVersionMapper engineVersionMapper;
    private final FlowEdgeMapper flowEdgeMapper;
    private final EngineNodeMapper engineNodeMapper;
    private final NodeKnowledgeRelMapper nodeKnowledgeRelMapper;
    private final com.helix.console.engine.mapper.FlowPublishMapper flowPublishMapper;
    private final com.helix.console.engine.mapper.EngineMapper engineMapper;
    private final com.helix.console.engine.mapper.DecisionTableMapper decisionTableMapper;
    private final com.helix.console.engine.mapper.DtRowMapper dtRowMapper;
    private final com.helix.console.engine.mapper.DtColumnMapper dtColumnMapper;
    private final com.helix.console.engine.mapper.DtCellMapper dtCellMapper;
    private final com.helix.console.knowledge.mapper.KnowledgeTreeMapper knowledgeTreeMapper;
    private final com.helix.console.knowledge.mapper.RuleMapper ruleMapper;
    private final com.helix.console.knowledge.mapper.ScorecardMapper scorecardMapper;
    private final com.helix.console.engine.client.EngineClient engineClient;
    private final ObjectMapper objectMapper;

    // ===== Canvas reading =====

    @Override
    public GraphVO loadGraph(Integer versionId) {
        EngineVersion version = mustGetVersion(versionId);
        GraphVO vo = new GraphVO();
        vo.setVersionId(versionId);
        vo.setZoom(version.getLayout() == null ? 1.0 : version.getLayout() / 100.0);

        List<EngineNode> nodes = engineNodeMapper.selectByVersionId(versionId);
        Map<Integer, List<NodeKnowledgeRel>> relByNode = loadRelMap(nodes);

        // Edges read from the t_flow_edge source of truth (fromCode -> ordered toCode); fall back to next_nodes derivation when no edge rows exist.
        // The whole edge entity goes into the List - the same from->to pair may have multiple edges with different labels
        // (e.g. Rule->Decision Table may have both "Reject" and "Manual Review"); storing only toCode would merge and lose edges on the canvas.
        Map<String, List<com.helix.console.engine.entity.FlowEdgeEntity>> edgesByFrom = new LinkedHashMap<>();
        for (com.helix.console.engine.entity.FlowEdgeEntity e : flowEdgeMapper.selectByVersionId(versionId)) {
            if (e.getFromCode() != null && e.getToCode() != null) {
                edgesByFrom.computeIfAbsent(e.getFromCode(), k -> new ArrayList<>()).add(e);
            }
        }
        boolean edgeTableEmpty = edgesByFrom.isEmpty();

        for (EngineNode node : nodes) {
            GraphVO.CellVO cell = new GraphVO.CellVO();
            cell.setId(NODE_PREFIX + node.getNodeCode());
            cell.setShape("x6-node");
            GraphVO.Position pos = new GraphVO.Position();
            pos.setX(node.getNodeX() == null ? 0D : node.getNodeX().doubleValue());
            pos.setY(node.getNodeY() == null ? 0D : node.getNodeY().doubleValue());
            cell.setPosition(pos);

            GraphVO.NodeData data = new GraphVO.NodeData();
            data.setNodeId(node.getNodeId());
            data.setNodeName(node.getNodeName());
            data.setNodeCode(node.getNodeCode());
            data.setNodeType(node.getNodeType());
            data.setNodeOrder(node.getNodeOrder());
            data.setNodeJson(toJsonObject(node.getNodeJson()));
            data.setNodeScript(node.getNodeScript());
            List<NodeKnowledgeRel> rels = relByNode.getOrDefault(node.getNodeId(), new ArrayList<>());
            data.setKnowledge(rels.stream().map(r -> {
                GraphVO.KnowledgeRef ref = new GraphVO.KnowledgeRef();
                ref.setKnowledgeId(r.getKnowledgeId());
                ref.setKnowledgeType(r.getKnowledgeType());
                return ref;
            }).collect(Collectors.toList()));
            data.setNextNodes(splitCodes(node.getNextNodes()));
            cell.setData(data);
            vo.getCells().add(cell);

            // Prefer the edge table; fall back to the comma-joined string when empty (same semantics as the engine-channel derivation fallback)
            List<com.helix.console.engine.entity.FlowEdgeEntity> nextEdges = edgeTableEmpty
                    ? java.util.Collections.emptyList()
                    : edgesByFrom.getOrDefault(node.getNodeCode(), new ArrayList<>());
            // Uniquify the id with the label - multiple edges of the same from->to (Reject/Manual Review) must coexist;
            // identical ids would be deduplicated by X6, and opening the canvas then saving would silently drop edges.
            Map<String, Integer> dupSeq = new java.util.HashMap<>();
            for (com.helix.console.engine.entity.FlowEdgeEntity e : nextEdges) {
                String next = e.getToCode();
                GraphVO.CellVO edge = new GraphVO.CellVO();
                String base = "e_" + node.getNodeCode() + "_" + next;
                int seq = dupSeq.merge(base, 1, Integer::sum);
                edge.setId(seq == 1 ? base : base + "_" + seq);
                edge.setShape("x6-edge");
                edge.setLabel(e.getLabel());
                edge.setKind(e.getEdgeKind());
                GraphVO.Terminal source = new GraphVO.Terminal();
                source.setCell(NODE_PREFIX + node.getNodeCode());
                edge.setSource(source);
                GraphVO.Terminal target = new GraphVO.Terminal();
                target.setCell(NODE_PREFIX + next);
                edge.setTarget(target);
                vo.getCells().add(edge);
            }
            if (edgeTableEmpty) {
                for (String next : splitCodes(node.getNextNodes())) {
                    GraphVO.CellVO edge = new GraphVO.CellVO();
                    edge.setId("e_" + node.getNodeCode() + "_" + next);
                    edge.setShape("x6-edge");
                    GraphVO.Terminal source = new GraphVO.Terminal();
                    source.setCell(NODE_PREFIX + node.getNodeCode());
                    edge.setSource(source);
                    GraphVO.Terminal target = new GraphVO.Terminal();
                    target.setCell(NODE_PREFIX + next);
                    edge.setTarget(target);
                    vo.getCells().add(edge);
                }
            }
        }
        return vo;
    }

    // ===== Canvas save: incremental persistence after diff comparison =====

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void saveGraph(VersionSaveDTO dto) {
        mustGetVersion(dto.getVersionId());
        GraphVO graph = dto.getGraph();
        if (graph == null || graph.getCells() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Canvas data is empty");
        }

        List<EngineNode> existing = engineNodeMapper.selectByVersionId(dto.getVersionId());
        Map<String, EngineNode> existByCode = existing.stream()
                .collect(Collectors.toMap(EngineNode::getNodeCode, n -> n, (a, b) -> a));

        // cellId -> nodeCode mapping fallback. Newly dragged nodes get a uuid
        // as cell id generated by X6 (the nodeCode cannot be stripped from the n_ prefix), and edge mapping failures would be silently dropped.
        Map<String, String> cellIdToCode = new LinkedHashMap<>();
        for (GraphVO.CellVO cell : graph.getCells()) {
            if (isNodeCell(cell) && cell.getId() != null && cell.getData() != null
                    && StringUtils.isNotBlank(cell.getData().getNodeCode())) {
                cellIdToCode.put(cell.getId(), cell.getData().getNodeCode().trim());
            }
        }

        // 1. Node upsert
        Map<String, Integer> codeToId = new LinkedHashMap<>();
        for (GraphVO.CellVO cell : graph.getCells()) {
            if (!isNodeCell(cell) || cell.getData() == null) {
                continue;
            }
            GraphVO.NodeData data = cell.getData();
            EngineNode node = existByCode.get(data.getNodeCode());
            boolean create = node == null;
            if (create) {
                node = new EngineNode();
                node.setVersionId(dto.getVersionId());
                node.setNodeCode(data.getNodeCode());
            }
            node.setNodeName(data.getNodeName());
            node.setNodeType(data.getNodeType());
            node.setNodeOrder(data.getNodeOrder());
            node.setNodeJson(toJsonString(data.getNodeJson()));
            node.setNodeScript(data.getNodeScript());
            if (cell.getPosition() != null) {
                node.setNodeX(toBigDecimal(cell.getPosition().getX()));
                node.setNodeY(toBigDecimal(cell.getPosition().getY()));
            }
            if (create) {
                engineNodeMapper.insert(node);
            } else {
                engineNodeMapper.updateById(node);
            }
            codeToId.put(node.getNodeCode(), node.getNodeId());

            if (data.getKnowledge() != null) {
                syncKnowledge(node.getNodeId(), data.getKnowledge());
            }
        }

        // 1.1 Delete nodes that no longer exist on the canvas (the whole-canvas save is the source of truth).
        // Cascade: clear node-knowledge relations (same as removeNode); in/out edges are naturally cleared by the
        // full edge-table rebuild below; dangling next_nodes references are naturally eliminated by
        // "full recomputation based on canvas edges".
        Set<String> canvasCodes = new HashSet<>(codeToId.keySet());
        for (EngineNode exist : existing) {
            if (!canvasCodes.contains(exist.getNodeCode())) {
                nodeKnowledgeRelMapper.delete(Wrappers.<NodeKnowledgeRel>lambdaQuery()
                        .eq(NodeKnowledgeRel::getNodeId, exist.getNodeId()));
                engineNodeMapper.deleteById(exist.getNodeId());
            }
        }

        // 2. Edges -> next_nodes (fully recomputed based on canvas edges)
        Map<String, Set<String>> nextByCode = new LinkedHashMap<>();
        codeToId.keySet().forEach(code -> nextByCode.put(code, new HashSet<>()));
        for (GraphVO.CellVO cell : graph.getCells()) {
            if (!isEdgeCell(cell) || cell.getSource() == null || cell.getTarget() == null) {
                continue;
            }
            String from = resolveCode(cell.getSource().getCell(), cellIdToCode);
            String to = resolveCode(cell.getTarget().getCell(), cellIdToCode);
            if (nextByCode.containsKey(from) && nextByCode.containsKey(to)) {
                nextByCode.get(from).add(to);
            }
        }
        nextByCode.forEach((code, nexts) -> {
            EngineNode node = existByCode.get(code);
            Integer nodeId = codeToId.get(code);
            String joined = String.join(",", nexts);
            String old = node == null ? null : node.getNextNodes();
            if (!Objects.equals(old == null ? "" : old, joined)) {
                EngineNode update = new EngineNode();
                update.setNodeId(nodeId);
                update.setNextNodes(joined);
                engineNodeMapper.updateById(update);
            }
        });

        // 3. Edges are also written to t_flow_edge (explicit edge table).
        //    Double-written with next_nodes: the engine snapshot still reads the node string, while the edge table
        //    provides structured storage for conditional-edge capability (hit/miss branches, priority).
        //    Fully rebuilt based on canvas edges: the number of edges is small, so rebuild is more reliable than diff.
        flowEdgeMapper.deleteByVersionId(dto.getVersionId());
        for (GraphVO.CellVO cell : graph.getCells()) {
            if (!isEdgeCell(cell) || cell.getSource() == null || cell.getTarget() == null) {
                continue;
            }
            String from = resolveCode(cell.getSource().getCell(), cellIdToCode);
            String to = resolveCode(cell.getTarget().getCell(), cellIdToCode);
            if (!codeToId.containsKey(from) || !codeToId.containsKey(to)) {
                continue;
            }
            FlowEdgeEntity edge = new FlowEdgeEntity();
            edge.setVersionId(dto.getVersionId());
            edge.setFromCode(from);
            edge.setToCode(to);
            // Write back the canvas edge label and kind (otherwise the "Reject/Manual Review" branches of the
            // same from->to pair are lost after one round trip to the canvas)
            edge.setLabel(cell.getLabel());
            edge.setEdgeKind(cell.getKind() == null ? 1 : cell.getKind());
            edge.setPriority(0);
            edge.setCreatedTime(java.time.LocalDateTime.now());
            edge.setUpdatedTime(java.time.LocalDateTime.now());
            edge.setDeleted(0);
            flowEdgeMapper.insert(edge);
        }

        // 3. Layout
        if (dto.getLayout() != null) {
            EngineVersion v = new EngineVersion();
            v.setId(dto.getVersionId());
            v.setLayout(dto.getLayout());
            engineVersionMapper.updateById(v);
        }
        log.info("Canvas saved versionId={} nodeCount={}", dto.getVersionId(), codeToId.size());
    }

    // ===== Single node maintenance =====

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer saveNode(NodeSaveDTO dto) {
        mustGetVersion(dto.getVersionId());
        EngineNode node;
        boolean create = dto.getNodeId() == null;
        if (create) {
            node = new EngineNode();
            node.setVersionId(dto.getVersionId());
            node.setNodeCode(ensureUniqueCode(dto.getVersionId(), dto.getNodeCode()));
        } else {
            node = engineNodeMapper.selectById(dto.getNodeId());
            if (node == null || !node.getVersionId().equals(dto.getVersionId())) {
                throw BizException.of(ResultCode.NOT_FOUND, "Node not found");
            }
        }
        if (dto.getNodeName() != null) {
            node.setNodeName(dto.getNodeName());
        }
        if (dto.getNodeType() != null) {
            node.setNodeType(dto.getNodeType());
        }
        if (dto.getNodeOrder() != null) {
            node.setNodeOrder(dto.getNodeOrder());
        }
        if (dto.getNodeJson() != null) {
            node.setNodeJson(toJsonString(dto.getNodeJson()));
        }
        if (dto.getNodeScript() != null) {
            node.setNodeScript(dto.getNodeScript());
        }
        node.setNodeX(dto.getNodeX());
        node.setNodeY(dto.getNodeY());
        if (dto.getNextNodes() != null) {
            node.setNextNodes(String.join(",", dto.getNextNodes()));
        }
        if (create) {
            engineNodeMapper.insert(node);
        } else {
            engineNodeMapper.updateById(node);
        }
        if (dto.getKnowledge() != null) {
            syncKnowledge(node.getNodeId(), dto.getKnowledge());
        }
        return node.getNodeId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void removeNode(Integer versionId, Integer nodeId) {
        EngineNode node = engineNodeMapper.selectById(nodeId);
        if (node == null || !node.getVersionId().equals(versionId)) {
            return;
        }
        nodeKnowledgeRelMapper.delete(Wrappers.<NodeKnowledgeRel>lambdaQuery()
                .eq(NodeKnowledgeRel::getNodeId, nodeId));
        engineNodeMapper.deleteById(nodeId);

        // v3: clean up the node's in/out edges in the edge table to avoid dangling links
        flowEdgeMapper.delete(Wrappers.<FlowEdgeEntity>lambdaQuery()
                .eq(FlowEdgeEntity::getVersionId, versionId)
                .and(w -> w.eq(FlowEdgeEntity::getFromCode, node.getNodeCode())
                        .or().eq(FlowEdgeEntity::getToCode, node.getNodeCode())));

        // Remove the node from other nodes' next references
        for (EngineNode other : engineNodeMapper.selectByVersionId(versionId)) {
            if (containsCode(other.getNextNodes(), node.getNodeCode())) {
                List<String> kept = splitCodes(other.getNextNodes()).stream()
                        .filter(c -> !c.equals(node.getNodeCode()))
                        .collect(Collectors.toList());
                EngineNode update = new EngineNode();
                update.setNodeId(other.getNodeId());
                update.setNextNodes(String.join(",", kept));
                engineNodeMapper.updateById(update);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void removeNodes(Integer versionId, List<Integer> nodeIds) {
        if (nodeIds == null) {
            return;
        }
        nodeIds.forEach(id -> removeNode(versionId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void moveAndLink(NodeMoveDTO dto) {
        mustGetVersion(dto.getVersionId());
        if (dto.getNodes() != null) {
            for (NodeMoveDTO.NodePosition p : dto.getNodes()) {
                EngineNode node = engineNodeMapper.selectById(p.getNodeId());
                if (node == null || !node.getVersionId().equals(dto.getVersionId())) {
                    continue;
                }
                EngineNode update = new EngineNode();
                update.setNodeId(p.getNodeId());
                update.setNodeX(p.getNodeX());
                update.setNodeY(p.getNodeY());
                engineNodeMapper.updateById(update);
            }
        }
        if (dto.getEdges() != null) {
            for (NodeMoveDTO.EdgeChange e : dto.getEdges()) {
                applyEdgeChange(dto.getVersionId(), e);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer copyNode(Integer nodeId) {
        EngineNode src = engineNodeMapper.selectById(nodeId);
        if (src == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Node not found");
        }
        EngineNode copy = new EngineNode();
        copy.setVersionId(src.getVersionId());
        copy.setNodeCode(ensureUniqueCode(src.getVersionId(), src.getNodeCode() + "_copy"));
        copy.setNodeName(src.getNodeName() + " (copy)");
        copy.setNodeType(src.getNodeType());
        copy.setNodeOrder(src.getNodeOrder() == null ? null : src.getNodeOrder() + 1);
        copy.setNodeJson(src.getNodeJson());
        copy.setNodeScript(src.getNodeScript());
        copy.setNodeX(src.getNodeX() == null ? null : src.getNodeX().add(new java.math.BigDecimal(40)));
        copy.setNodeY(src.getNodeY() == null ? null : src.getNodeY().add(new java.math.BigDecimal(40)));
        engineNodeMapper.insert(copy);

        for (NodeKnowledgeRel rel : nodeKnowledgeRelMapper.selectByNodeId(nodeId)) {
            NodeKnowledgeRel r = new NodeKnowledgeRel();
            r.setNodeId(copy.getNodeId());
            r.setKnowledgeId(rel.getKnowledgeId());
            r.setKnowledgeType(rel.getKnowledgeType());
            nodeKnowledgeRelMapper.insert(r);
        }
        return copy.getNodeId();
    }

    // ===== Validation / publish / draft =====

    @Override
    public void validateGraph(Integer versionId) {
        List<EngineNode> nodes = engineNodeMapper.selectByVersionId(versionId);
        if (nodes.isEmpty()) {
            throw BizException.of(ResultCode.FLOW_INVALID, "The decision flow has no nodes");
        }

        // 1. Exactly one start node
        List<EngineNode> starts = nodes.stream()
                .filter(n -> n.getNodeType() != null && n.getNodeType() == NodeType.START.getCode())
                .collect(Collectors.toList());
        if (starts.size() != 1) {
            throw BizException.of(ResultCode.FLOW_INVALID,
                    "Exactly 1 start node is required, current count: " + starts.size());
        }

        // 2. No dangling references
        Set<String> codes = nodes.stream()
                .map(EngineNode::getNodeCode)
                .collect(Collectors.toSet());
        for (EngineNode node : nodes) {
            for (String next : splitCodes(node.getNextNodes())) {
                if (!codes.contains(next)) {
                    throw BizException.of(ResultCode.FLOW_INVALID,
                            "Downstream node " + next + " of node " + node.getNodeCode() + " does not exist");
                }
            }
        }

        // 3. No cycles + no isolated nodes: BFS from the start node, all nodes must be reachable
        Map<String, List<String>> adj = nodes.stream()
                .collect(Collectors.toMap(EngineNode::getNodeCode,
                        n -> splitCodes(n.getNextNodes()), (a, b) -> a, LinkedHashMap::new));
        String startCode = starts.get(0).getNodeCode();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startCode);
        visited.add(startCode);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String next : adj.getOrDefault(cur, new ArrayList<>())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        // Cycle detection: DFS with a visited stack
        detectCycle(adj, startCode, new HashSet<>(), new HashSet<>());

        if (visited.size() != nodes.size()) {
            List<String> isolated = nodes.stream()
                    .map(EngineNode::getNodeCode)
                    .filter(c -> !visited.contains(c))
                    .collect(Collectors.toList());
            throw BizException.of(ResultCode.FLOW_INVALID, "Nodes unreachable from the start node exist: " + isolated);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void publishVersion(Integer versionId) {
        EngineVersion version = mustGetVersion(versionId);
        validateGraph(versionId);

        // Solidify publish artifacts first, then mark deployment - guaranteeing "deployed => artifacts must exist".
        // Artifact solidification failure aborts the publish (prevents a "live but without artifacts" bad state when the engine is unreachable).
        try {
            engineClient.publishArtifact(versionId);
        } catch (Exception e) {
            throw BizException.of(ResultCode.SYSTEM_ERROR,
                    "Failed to solidify publish artifacts, publish aborted: " + e.getMessage());
        }

        // Take other versions of the same engine offline
        engineVersionMapper.update(null, Wrappers.<EngineVersion>lambdaUpdate()
                .eq(EngineVersion::getEngineId, version.getEngineId())
                .ne(EngineVersion::getId, versionId)
                .set(EngineVersion::getBootState, 0));
        engineVersionMapper.update(null, Wrappers.<EngineVersion>lambdaUpdate()
                .eq(EngineVersion::getId, versionId)
                .set(EngineVersion::getBootState, 1)
                .set(EngineVersion::getStatus, 1));

        // Publishing takes effect immediately: notify helix-engine to rebuild the snapshot and warm up (failure does not block publish;
        // artifacts are already solidified; the engine will load on the next reload or a manual /engineApi/update)
        notifyEngineReload(versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer saveAsDraft(Integer versionId) {
        EngineVersion src = engineVersionMapper.selectById(versionId);
        if (src == null) {
            throw BizException.of(ResultCode.VERSION_NOT_FOUND);
        }
        EngineVersion draft = new EngineVersion();
        draft.setEngineId(src.getEngineId());
        draft.setVersion(src.getVersion());
        draft.setSubVersion(src.getSubVersion() == null ? 1 : src.getSubVersion() + 1);
        draft.setBootState(0);
        draft.setStatus(1);
        draft.setLayout(src.getLayout());
        draft.setCreatedTime(src.getCreatedTime());
        engineVersionMapper.insert(draft);

        List<EngineNode> nodes = engineNodeMapper.selectByVersionId(versionId);
        Map<Integer, List<NodeKnowledgeRel>> relByNode = loadRelMap(nodes);
        Map<Integer, Integer> idMap = new LinkedHashMap<>();
        for (EngineNode node : nodes) {
            EngineNode copy = new EngineNode();
            copy.setVersionId(draft.getId());
            copy.setNodeName(node.getNodeName());
            copy.setNodeCode(node.getNodeCode());
            copy.setNodeOrder(node.getNodeOrder());
            copy.setNodeType(node.getNodeType());
            copy.setNodeJson(node.getNodeJson());
            copy.setNodeX(node.getNodeX());
            copy.setNodeY(node.getNodeY());
            copy.setNodeScript(node.getNodeScript());
            copy.setNextNodes(node.getNextNodes());
            engineNodeMapper.insert(copy);
            idMap.put(node.getNodeId(), copy.getNodeId());

            for (NodeKnowledgeRel rel : relByNode.getOrDefault(node.getNodeId(), new ArrayList<>())) {
                NodeKnowledgeRel r = new NodeKnowledgeRel();
                r.setNodeId(copy.getNodeId());
                r.setKnowledgeId(rel.getKnowledgeId());
                r.setKnowledgeType(rel.getKnowledgeType());
                nodeKnowledgeRelMapper.insert(r);
            }
        }

        // Copy edges to t_flow_edge (the draft must match the source version topology, including "Reject/Manual Review" branch edges).
        List<FlowEdgeEntity> srcEdges = flowEdgeMapper.selectByVersionId(versionId);
        for (FlowEdgeEntity e : srcEdges) {
            FlowEdgeEntity copy = new FlowEdgeEntity();
            copy.setVersionId(draft.getId());
            copy.setFromCode(e.getFromCode());
            copy.setToCode(e.getToCode());
            copy.setLabel(e.getLabel());
            copy.setPriority(e.getPriority());
            copy.setEdgeKind(e.getEdgeKind());
            copy.setDeleted(0);
            copy.setCreatedTime(java.time.LocalDateTime.now());
            copy.setUpdatedTime(java.time.LocalDateTime.now());
            flowEdgeMapper.insert(copy);
        }
        log.info("Saved as draft src={} draft={} nodes={} edges={}", versionId, draft.getId(), nodes.size(), srcEdges.size());
        return draft.getId();
    }

    /**
     * Delete a version.
     *
     * <p>Guards: (1) a running (boot_state=1) version cannot be deleted - the live snapshot depends on its
     * artifacts and routing; publish another version to take it offline first, then delete;
     * (2) the engine must be within this host's visible range (prevents cross-tenant engine deletion).
     * Cascade cleanup: node-knowledge relations, edges, nodes, publish artifacts, and the version row itself;
     * if the deleted version is the engine's **last version**, the engine is deleted too (decision tables and
     * engine-level directories are cleaned with the engine; rules/scorecards under engine-level directories move
     * back to the organization top level, knowledge base assets are preserved).</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void removeVersion(Integer versionId) {
        EngineVersion version = mustGetVersion(versionId);
        com.helix.console.engine.entity.Engine engine = engineMapper.selectById(version.getEngineId());
        if (engine != null) {
            List<Long> visible = com.helix.console.system.security.TenantScope.visibleOrgans();
            if (visible != null && engine.getOrganId() != null && !visible.contains(engine.getOrganId().longValue())) {
                throw BizException.of(ResultCode.FORBIDDEN, "No permission to delete other organizations' decision data");
            }
        }
        if (version.getBootState() != null && version.getBootState() == 1) {
            throw BizException.of(ResultCode.PARAM_INVALID,
                    "A running version cannot be deleted; publish another version to take it offline first, or copy it as a draft to iterate before deleting");
        }
        boolean lastVersion = countEngineVersions(version.getEngineId()) <= 1;
        deleteVersionCascade(versionId);
        if (lastVersion && engine != null) {
            deleteEngineCascade(engine);
            log.info("Engine deleted along with its last version engine={} code={}", engine.getId(), engine.getCode());
        }
    }

    private long countEngineVersions(Integer engineId) {
        Long n = engineVersionMapper.selectCount(Wrappers.<EngineVersion>lambdaQuery()
                .eq(EngineVersion::getEngineId, engineId));
        return n == null ? 0 : n;
    }

    /** Version cascade cleanup: knowledge relations -> edges -> nodes -> publish artifacts -> version row */
    private void deleteVersionCascade(Integer versionId) {
        List<EngineNode> nodes = engineNodeMapper.selectByVersionId(versionId);
        for (EngineNode n : nodes) {
            nodeKnowledgeRelMapper.delete(Wrappers.<NodeKnowledgeRel>lambdaQuery()
                    .eq(NodeKnowledgeRel::getNodeId, n.getNodeId()));
        }
        flowEdgeMapper.deleteByVersionId(versionId);
        engineNodeMapper.delete(Wrappers.<EngineNode>lambdaQuery()
                .eq(EngineNode::getVersionId, versionId));
        flowPublishMapper.delete(Wrappers.<com.helix.console.engine.entity.FlowPublishEntity>lambdaQuery()
                .eq(com.helix.console.engine.entity.FlowPublishEntity::getVersionId, versionId));
        engineVersionMapper.deleteById(versionId);
    }

    /**
     * Engine cascade cleanup (triggered only when the last version is deleted):
     * cascade of all versions -> decision tables bound to this engine (rows/columns/cells) -> engine-level directories
     * (rules/scorecards under the directories move back to the organization top level, knowledge base assets are not lost) -> engine row.
     */
    private void deleteEngineCascade(com.helix.console.engine.entity.Engine engine) {
        Integer engineId = engine.getId();
        for (EngineVersion v : engineVersionMapper.selectList(Wrappers.<EngineVersion>lambdaQuery()
                .eq(EngineVersion::getEngineId, engineId))) {
            deleteVersionCascade(v.getId());
        }
        // Decision tables (engine_id bound to this engine): rows/columns/cells -> table
        List<com.helix.console.engine.entity.DecisionTableEntity> dts =
                decisionTableMapper.selectList(Wrappers.<com.helix.console.engine.entity.DecisionTableEntity>lambdaQuery()
                        .eq(com.helix.console.engine.entity.DecisionTableEntity::getEngineId, engineId));
        for (com.helix.console.engine.entity.DecisionTableEntity dt : dts) {
            dtRowMapper.delete(Wrappers.<com.helix.console.engine.entity.DtRowEntity>lambdaQuery()
                    .eq(com.helix.console.engine.entity.DtRowEntity::getTableId, dt.getId()));
            dtColumnMapper.delete(Wrappers.<com.helix.console.engine.entity.DtColumnEntity>lambdaQuery()
                    .eq(com.helix.console.engine.entity.DtColumnEntity::getTableId, dt.getId()));
            dtCellMapper.delete(Wrappers.<com.helix.console.engine.entity.DtCellEntity>lambdaQuery()
                    .eq(com.helix.console.engine.entity.DtCellEntity::getTableId, dt.getId()));
            decisionTableMapper.deleteById(dt.getId());
        }
        // Engine-level knowledge directories: rules/scorecards under the directories move back to the organization top level (parent_id=0), then delete the directories
        List<com.helix.console.knowledge.entity.KnowledgeTree> engineTrees =
                knowledgeTreeMapper.selectList(Wrappers.<com.helix.console.knowledge.entity.KnowledgeTree>lambdaQuery()
                        .eq(com.helix.console.knowledge.entity.KnowledgeTree::getEngineId, engineId));
        for (com.helix.console.knowledge.entity.KnowledgeTree t : engineTrees) {
            if (t.getTreeType() != null && t.getTreeType() == 0) {
                ruleMapper.update(null, Wrappers.<com.helix.console.knowledge.entity.Rule>lambdaUpdate()
                        .eq(com.helix.console.knowledge.entity.Rule::getParentId, t.getId())
                        .set(com.helix.console.knowledge.entity.Rule::getParentId, 0));
            } else if (t.getTreeType() != null && t.getTreeType() == 1) {
                scorecardMapper.update(null, Wrappers.<com.helix.console.knowledge.entity.Scorecard>lambdaUpdate()
                        .eq(com.helix.console.knowledge.entity.Scorecard::getParentId, t.getId())
                        .set(com.helix.console.knowledge.entity.Scorecard::getParentId, 0));
            }
        }
        if (!engineTrees.isEmpty()) {
            knowledgeTreeMapper.delete(Wrappers.<com.helix.console.knowledge.entity.KnowledgeTree>lambdaQuery()
                    .eq(com.helix.console.knowledge.entity.KnowledgeTree::getEngineId, engineId));
        }
        engineMapper.deleteById(engineId);
    }

    // ===== Internal methods =====

    private EngineVersion mustGetVersion(Integer versionId) {
        EngineVersion version = engineVersionMapper.selectById(versionId);
        if (version == null) {
            throw BizException.of(ResultCode.VERSION_NOT_FOUND);
        }
        return version;
    }

    private Map<Integer, List<NodeKnowledgeRel>> loadRelMap(List<EngineNode> nodes) {
        if (nodes.isEmpty()) {
            return new HashMap<>();
        }
        Map<Integer, List<NodeKnowledgeRel>> map = new HashMap<>();
        nodeKnowledgeRelMapper.selectByNodeIds(
                nodes.stream().map(EngineNode::getNodeId).collect(Collectors.toList()))
                .forEach(r -> map.computeIfAbsent(r.getNodeId(), k -> new ArrayList<>()).add(r));
        return map;
    }

    private void syncKnowledge(Integer nodeId, List<GraphVO.KnowledgeRef> refs) {
        nodeKnowledgeRelMapper.delete(Wrappers.<NodeKnowledgeRel>lambdaQuery()
                .eq(NodeKnowledgeRel::getNodeId, nodeId));
        for (GraphVO.KnowledgeRef ref : refs) {
            NodeKnowledgeRel r = new NodeKnowledgeRel();
            r.setNodeId(nodeId);
            r.setKnowledgeId(ref.getKnowledgeId());
            r.setKnowledgeType(ref.getKnowledgeType());
            nodeKnowledgeRelMapper.insert(r);
        }
    }

    private void applyEdgeChange(Integer versionId, NodeMoveDTO.EdgeChange e) {
        EngineNode source = engineNodeMapper.selectOne(Wrappers.<EngineNode>lambdaQuery()
                .eq(EngineNode::getVersionId, versionId)
                .eq(EngineNode::getNodeCode, e.getSourceNodeCode())
                .last("LIMIT 1"));
        if (source == null) {
            return;
        }
        List<String> nexts = new ArrayList<>(splitCodes(source.getNextNodes()));
        boolean changed;
        if (EDGE_ACTION_DELETE.equalsIgnoreCase(e.getAction())) {
            changed = nexts.remove(e.getTargetNodeCode());
        } else if (EDGE_ACTION_ADD.equalsIgnoreCase(e.getAction())) {
            changed = !nexts.contains(e.getTargetNodeCode());
            if (changed) {
                nexts.add(e.getTargetNodeCode());
            }
        } else {
            return;
        }
        if (changed) {
            EngineNode update = new EngineNode();
            update.setNodeId(source.getNodeId());
            update.setNextNodes(String.join(",", nexts));
            engineNodeMapper.updateById(update);
        }
        // Edge row sync is decoupled from next_nodes dedup, each idempotent - if insert were nested under
        // changed, then in the same flush "REMOVE old row + ADD new row" when changing branch type, the ADD would
        // get changed=false because next_nodes already contains the target, and the new row would be silently skipped
        // (branch type changed but not persisted).
        if (EDGE_ACTION_ADD.equalsIgnoreCase(e.getAction())) {
            String label = StringUtils.isBlank(e.getLabel()) ? "Pass" : e.getLabel().trim();
            Integer kind = e.getKind() == null ? 1 : e.getKind();
            Long dup = flowEdgeMapper.selectCount(Wrappers.<FlowEdgeEntity>lambdaQuery()
                    .eq(FlowEdgeEntity::getVersionId, versionId)
                    .eq(FlowEdgeEntity::getFromCode, e.getSourceNodeCode())
                    .eq(FlowEdgeEntity::getToCode, e.getTargetNodeCode())
                    .eq(FlowEdgeEntity::getLabel, label));
            if (dup == null || dup == 0) {
                FlowEdgeEntity edge = new FlowEdgeEntity();
                edge.setVersionId(versionId);
                edge.setFromCode(e.getSourceNodeCode());
                edge.setToCode(e.getTargetNodeCode());
                edge.setLabel(label);
                edge.setEdgeKind(kind);
                edge.setPriority(0);
                edge.setDeleted(0);
                edge.setCreatedTime(java.time.LocalDateTime.now());
                edge.setUpdatedTime(java.time.LocalDateTime.now());
                flowEdgeMapper.insert(edge);
            }
        } else {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowEdgeEntity> dw =
                    Wrappers.<FlowEdgeEntity>lambdaQuery()
                            .eq(FlowEdgeEntity::getVersionId, versionId)
                            .eq(FlowEdgeEntity::getFromCode, e.getSourceNodeCode())
                            .eq(FlowEdgeEntity::getToCode, e.getTargetNodeCode());
            if (StringUtils.isNotBlank(e.getLabel())) {
                dw.eq(FlowEdgeEntity::getLabel, e.getLabel().trim());
            }
            // kind disambiguation - for edges of the same from->to with the same label but different kinds, delete only the removed one
            // (the frontend edge:removed passes kind in the payload; if absent, do not filter by kind)
            if (e.getKind() != null) {
                dw.eq(FlowEdgeEntity::getEdgeKind, e.getKind());
            }
            flowEdgeMapper.delete(dw);
        }
    }

    /** Codes are unique within a version; on conflict append a _N sequence */
    private String ensureUniqueCode(Integer versionId, String raw) {
        String base = StringUtils.isBlank(raw) ? "node_" + System.currentTimeMillis() : raw.trim();
        Set<String> used = engineNodeMapper.selectByVersionId(versionId).stream()
                .map(EngineNode::getNodeCode)
                .collect(Collectors.toSet());
        if (!used.contains(base)) {
            return base;
        }
        for (int i = 1; i < 1000; i++) {
            if (!used.contains(base + "_" + i)) {
                return base + "_" + i;
            }
        }
        throw BizException.of(ResultCode.PARAM_INVALID, "Node code conflict: " + base);
    }

    /** DFS cycle detection: a repeated visit within the stack means a cycle */
    private void detectCycle(Map<String, List<String>> adj, String cur,
                             Set<String> visiting, Set<String> done) {
        visiting.add(cur);
        for (String next : adj.getOrDefault(cur, new ArrayList<>())) {
            if (visiting.contains(next)) {
                throw BizException.of(ResultCode.FLOW_INVALID,
                        "The decision flow contains a cycle involving node " + next);
            }
            if (!done.contains(next)) {
                detectCycle(adj, next, visiting, done);
            }
        }
        visiting.remove(cur);
        done.add(cur);
    }

    private boolean isNodeCell(GraphVO.CellVO cell) {
        return cell.getSource() == null && cell.getTarget() == null
                && (cell.getShape() == null || !cell.getShape().contains(EDGE_SHAPE_KEYWORD));
    }

    private boolean isEdgeCell(GraphVO.CellVO cell) {
        return (cell.getShape() != null && cell.getShape().contains(EDGE_SHAPE_KEYWORD))
                || (cell.getSource() != null && cell.getTarget() != null);
    }

    private String trimCode(String cellId) {
        String code = StringUtils.isBlank(cellId) ? "" : cellId.trim();
        return code.startsWith(NODE_PREFIX) ? code.substring(NODE_PREFIX.length()) : code;
    }

    /** Edge endpoint resolution - first strip the code by the n_ prefix; if that fails (legacy uuid id), look up the node data.nodeCode mapping */
    private String resolveCode(String cellId, Map<String, String> cellIdToCode) {
        String code = trimCode(cellId);
        if (cellIdToCode.containsKey(code)) {
            return code;
        }
        return cellIdToCode.getOrDefault(cellId, code);
    }

    private boolean containsCode(String joined, String code) {
        return joined != null && splitCodes(joined).contains(code);
    }

    private List<String> splitCodes(String joined) {
        List<String> result = new ArrayList<>();
        if (StringUtils.isBlank(joined)) {
            return result;
        }
        for (String p : joined.split(",")) {
            String t = p.trim();
            if (!t.isEmpty() && !"null".equalsIgnoreCase(t)) {
                result.add(t);
            }
        }
        return result;
    }

    private Object toJsonObject(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private java.math.BigDecimal toBigDecimal(Double v) {
        return v == null ? null : java.math.BigDecimal.valueOf(v);
    }

    /**
     * Notify the Decision Engine to rebuild the snapshot and warm up (publish takes effect immediately).
     * Failure only logs a warning and does not block the publish: the engine's old snapshot keeps serving; manual compensation is possible.
     */
    private void notifyEngineReload(Integer versionId) {
        try {
            // Publish semantics: solidify artifacts + take effect immediately (the engine writes the current configuration
            // into t_flow_publish and then rebuilds the snapshot).
            // Artifacts were already solidified successfully at the start of publishVersion, so a failure here will not
            // cause a "deployed but without artifacts" state.
            engineClient.publishDeploy(versionId);
            log.info("Notified the Decision Engine to refresh its snapshot versionId={}", versionId);
        } catch (Exception e) {
            log.warn("Failed to notify the Decision Engine to refresh (can manually call /engineApi/update) versionId={} err={}",
                    versionId, e.getMessage());
        }
    }
}
