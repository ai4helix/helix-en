package com.helix.console.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.datamanage.entity.Field;
import com.helix.console.datamanage.mapper.FieldMapper;
import com.helix.console.knowledge.dto.TreeNodeVO;
import com.helix.console.knowledge.entity.KnowledgeTree;
import com.helix.console.knowledge.entity.Rule;
import com.helix.console.knowledge.entity.Scorecard;
import com.helix.console.knowledge.enums.TreeType;
import com.helix.console.knowledge.mapper.KnowledgeTreeMapper;
import com.helix.console.knowledge.mapper.RuleMapper;
import com.helix.console.knowledge.mapper.ScorecardMapper;
import com.helix.console.knowledge.service.KnowledgeTreeService;
import com.helix.console.system.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Knowledge base catalog tree service implementation.
 *
 * <p>Catalog-to-knowledge association: rule/scorecard {@code parent_id} points
 * to the catalog node id, not the reverse child list. Therefore a catalog must
 * be confirmed empty before deletion.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeTreeServiceImpl implements KnowledgeTreeService {

    private static final int STATUS_ENABLED = 1;
    private static final int ROOT_PARENT = 0;

    private final KnowledgeTreeMapper knowledgeTreeMapper;
    private final RuleMapper ruleMapper;
    private final ScorecardMapper scorecardMapper;
    private final FieldMapper fieldMapper;

    @Override
    public List<TreeNodeVO> tree(Integer treeType, Integer engineId) {
        LambdaQueryWrapper<KnowledgeTree> qw = new LambdaQueryWrapper<>();
        qw.eq(KnowledgeTree::getStatus, STATUS_ENABLED);
        // Org users only see catalogs in [0 platform common, own organization]
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(KnowledgeTree::getOrganId, organs);
        }
        qw.eq(KnowledgeTree::getTreeType, treeType == null ? TreeType.RULE.getCode() : treeType);
        if (engineId != null) {
            qw.eq(KnowledgeTree::getEngineId, engineId);
        } else {
            qw.isNull(KnowledgeTree::getEngineId);
        }
        qw.orderByAsc(KnowledgeTree::getId);

        List<KnowledgeTree> nodes = knowledgeTreeMapper.selectList(qw);
        if (nodes.isEmpty()) {
            // This engine has no catalog tree yet; initialize one automatically to avoid an empty frontend
            initDefaultTree(treeType, engineId);
            nodes = knowledgeTreeMapper.selectList(qw);
        }

        // Count knowledge items per catalog (rule tree vs scorecard tree)
        Map<Integer, Integer> countMap = countByParent(treeType);

        List<TreeNodeVO> vos = nodes.stream().map(n -> {
            TreeNodeVO vo = new TreeNodeVO();
            vo.setId(n.getId());
            vo.setName(n.getName());
            vo.setParentId(n.getParentId());
            vo.setType(n.getType());
            vo.setTreeType(n.getTreeType());
            vo.setEngineId(n.getEngineId());
            vo.setCount(countMap.getOrDefault(n.getId(), 0));
            // Recycle bin is a system catalog and cannot be deleted
            vo.setSystem(TreeType.of(n.getTreeType()).isRecycle());
            return vo;
        }).collect(Collectors.toList());

        // Parent count = own direct count + all descendants (direct-only counting
        // leaves parents at 0 since items usually hang on leaves, distorting frontend totals)
        aggregateTreeCounts(vos);

        return buildTree(vos);
    }

    /**
     * Bottom-up aggregation: each node's count = direct count + sum of all
     * descendants. direct comes from the vo's existing count (the direct
     * counting result of countByParent); visited guards against cycles in dirty data.
     */
    private void aggregateTreeCounts(List<TreeNodeVO> vos) {
        Map<Integer, List<Integer>> childrenByParent = new HashMap<>();
        Map<Integer, Integer> direct = new HashMap<>();
        for (TreeNodeVO vo : vos) {
            direct.put(vo.getId(), vo.getCount() == null ? 0 : vo.getCount());
            Integer pid = vo.getParentId();
            if (pid != null && pid != ROOT_PARENT) {
                childrenByParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(vo.getId());
            }
        }
        Map<Integer, Integer> memo = new HashMap<>();
        for (TreeNodeVO vo : vos) {
            vo.setCount(sumSubtree(vo.getId(), childrenByParent, direct, memo, new HashSet<>()));
        }
    }

    private int sumSubtree(Integer id, Map<Integer, List<Integer>> childrenByParent,
                           Map<Integer, Integer> direct, Map<Integer, Integer> memo, Set<Integer> visiting) {
        Integer cached = memo.get(id);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(id)) {
            return direct.getOrDefault(id, 0); // cycle: count self only to avoid infinite loop
        }
        int sum = direct.getOrDefault(id, 0);
        for (Integer child : childrenByParent.getOrDefault(id, Collections.emptyList())) {
            sum += sumSubtree(child, childrenByParent, direct, memo, visiting);
        }
        visiting.remove(id);
        memo.put(id, sum);
        return sum;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer create(String name, Integer parentId, Integer treeType, Integer engineId, Integer organId) {
        if (StringUtils.isBlank(name)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Catalog name must not be blank");
        }
        Integer pid = parentId == null ? ROOT_PARENT : parentId;

        // Same-level duplicate name check
        LambdaQueryWrapper<KnowledgeTree> dup = new LambdaQueryWrapper<KnowledgeTree>()
                .eq(KnowledgeTree::getName, name)
                .eq(KnowledgeTree::getParentId, pid)
                .eq(KnowledgeTree::getStatus, STATUS_ENABLED);
        if (knowledgeTreeMapper.selectCount(dup) > 0) {
            throw BizException.of(ResultCode.DATA_DUPLICATE, "Name already exists under the same parent catalog: " + name);
        }

        KnowledgeTree node = new KnowledgeTree();
        node.setName(name);
        node.setParentId(pid);
        node.setTreeType(treeType == null ? TreeType.RULE.getCode() : treeType);
        // Admin users default to platform common (0); org users are forced to their own organization
        node.setOrganId(TenantScope.writeOrgan(organId == null ? null : organId.longValue()).intValue());
        node.setType(engineId == null ? 1 : 2);
        node.setEngineId(engineId);
        node.setStatus(STATUS_ENABLED);
        LocalDateTime now = LocalDateTime.now();
        node.setCreatedTime(now);
        node.setUpdatedTime(now);
        knowledgeTreeMapper.insert(node);
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void rename(Integer id, String name) {
        if (StringUtils.isBlank(name)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Catalog name must not be blank");
        }
        KnowledgeTree node = knowledgeTreeMapper.selectById(id);
        if (node == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Catalog does not exist");
        }
        // Operating on catalogs of other organizations is forbidden
        TenantScope.checkVisible(node.getOrganId() == null ? null : node.getOrganId().longValue());
        if (TreeType.of(node.getTreeType()).isRecycle()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "System catalog cannot be renamed");
        }
        node.setName(name);
        node.setUpdatedTime(LocalDateTime.now());
        knowledgeTreeMapper.updateById(node);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void remove(Integer id) {
        KnowledgeTree node = knowledgeTreeMapper.selectById(id);
        if (node == null) {
            return;
        }
        // Operating on catalogs of other organizations is forbidden
        TenantScope.checkVisible(node.getOrganId() == null ? null : node.getOrganId().longValue());
        if (TreeType.of(node.getTreeType()).isRecycle()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "System catalog cannot be deleted");
        }
        // Cannot delete when sub-catalogs exist
        Long childCount = knowledgeTreeMapper.selectCount(new LambdaQueryWrapper<KnowledgeTree>()
                .eq(KnowledgeTree::getParentId, id).eq(KnowledgeTree::getStatus, STATUS_ENABLED));
        if (childCount != null && childCount > 0) {
            throw BizException.of(ResultCode.DATA_IN_USE, "This catalog contains sub-catalogs; delete them first");
        }
        // Cannot delete when knowledge items exist
        int knowledgeCount = countByParent(node.getTreeType()).getOrDefault(id, 0);
        if (knowledgeCount > 0) {
            throw BizException.of(ResultCode.DATA_IN_USE,
                    "This catalog contains " + knowledgeCount + " knowledge items; move or delete them first");
        }
        node.setStatus(-1);
        node.setUpdatedTime(LocalDateTime.now());
        knowledgeTreeMapper.updateById(node);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void move(Integer id, Integer newParentId) {
        if (Objects.equals(id, newParentId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Cannot move to itself");
        }
        KnowledgeTree node = knowledgeTreeMapper.selectById(id);
        if (node == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Catalog does not exist");
        }
        // Operating on catalogs of other organizations is forbidden
        TenantScope.checkVisible(node.getOrganId() == null ? null : node.getOrganId().longValue());
        // Prevent moving a catalog under its own descendant, which would form a cycle
        if (newParentId != null && newParentId != ROOT_PARENT && isDescendant(id, newParentId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Cannot move under its own descendant");
        }
        node.setParentId(newParentId == null ? ROOT_PARENT : newParentId);
        node.setUpdatedTime(LocalDateTime.now());
        knowledgeTreeMapper.updateById(node);
    }

    // ------------------------------------------------------------------ Internal methods

    /**
     * Initialize the default catalog tree: one root catalog + one recycle bin
     * catalog for the given type. Keeps the original data layout (tree_type 0/1
     * business trees, 2/3 recycle bins). The FIELD tree instead seeds four
     * common categories (no recycle bin concept).
     */
    private void initDefaultTree(Integer treeType, Integer engineId) {
        int type = treeType == null ? TreeType.RULE.getCode() : treeType;
        if (type == TreeType.FIELD.getCode()) {
            initFieldCatalogs();
            return;
        }
        boolean scorecardTree = type == TreeType.SCORECARD.getCode();
        int recycleType = scorecardTree ? TreeType.SCORECARD_RECYCLE.getCode() : TreeType.RULE_RECYCLE.getCode();

        KnowledgeTree root = new KnowledgeTree();
        root.setName(scorecardTree ? "Scorecard" : "Rule Set");
        root.setParentId(ROOT_PARENT);
        root.setTreeType(type);
        // Catalog ownership follows the current user's organization
        root.setOrganId(TenantScope.writeOrgan((Integer) null));
        root.setType(engineId == null ? 1 : 2);
        root.setEngineId(engineId);
        root.setStatus(STATUS_ENABLED);
        root.setCreatedTime(LocalDateTime.now());
        root.setUpdatedTime(LocalDateTime.now());
        knowledgeTreeMapper.insert(root);

        KnowledgeTree recycle = new KnowledgeTree();
        recycle.setName("Recycle Bin");
        recycle.setParentId(ROOT_PARENT);
        recycle.setTreeType(recycleType);
        recycle.setOrganId(root.getOrganId());
        recycle.setType(engineId == null ? 1 : 2);
        recycle.setEngineId(engineId);
        recycle.setStatus(STATUS_ENABLED);
        recycle.setCreatedTime(LocalDateTime.now());
        recycle.setUpdatedTime(LocalDateTime.now());
        knowledgeTreeMapper.insert(recycle);
    }

    /**
     * When the FIELD tree is empty, seed four common categories (semantics match
     * field_typeid 1-4), owned by the current user's organization; tenants can
     * add/remove/rename their own category catalogs.
     */
    private void initFieldCatalogs() {
        Integer organId = TenantScope.writeOrgan((Integer) null);
        LocalDateTime now = LocalDateTime.now();
        for (String name : Arrays.asList("Basic Info", "Credit Info", "Income & Debt", "Behavior Score")) {
            KnowledgeTree node = new KnowledgeTree();
            node.setName(name);
            node.setParentId(ROOT_PARENT);
            node.setTreeType(TreeType.FIELD.getCode());
            node.setOrganId(organId);
            node.setType(1);
            node.setEngineId(null);
            node.setStatus(STATUS_ENABLED);
            node.setCreatedTime(now);
            node.setUpdatedTime(now);
            knowledgeTreeMapper.insert(node);
        }
    }

    /** Count knowledge items mounted per catalog (only organizations visible to the current user).
     *  The FIELD tree counts t_field.catalog_id (field count). */
    private Map<Integer, Integer> countByParent(Integer treeType) {
        if (treeType != null && treeType == TreeType.FIELD.getCode()) {
            return countFieldsByCatalog();
        }
        boolean scorecardTree = treeType != null && treeType == TreeType.SCORECARD.getCode();
        List<Long> organs = TenantScope.visibleOrgans();
        List<Integer> parentIds;
        if (scorecardTree) {
            LambdaQueryWrapper<Scorecard> qw = new LambdaQueryWrapper<Scorecard>()
                    .isNotNull(Scorecard::getParentId).ne(Scorecard::getStatus, -1);
            if (organs != null) {
                qw.in(Scorecard::getOrganId, organs);
            }
            parentIds = scorecardMapper.selectList(qw)
                    .stream().map(Scorecard::getParentId).collect(Collectors.toList());
        } else {
            LambdaQueryWrapper<Rule> qw = new LambdaQueryWrapper<Rule>()
                    .isNotNull(Rule::getParentId).ne(Rule::getStatus, -1);
            if (organs != null) {
                qw.in(Rule::getOrganId, organs);
            }
            parentIds = ruleMapper.selectList(qw)
                    .stream().map(Rule::getParentId).collect(Collectors.toList());
        }
        Map<Integer, Integer> map = new HashMap<>();
        parentIds.forEach(pid -> map.merge(pid, 1, Integer::sum));
        return map;
    }

    /** FIELD tree: count fields per catalog_id under visible organizations (NULL = uncategorized, not counted in any catalog; excludes recycle bin status=-1) */
    private Map<Integer, Integer> countFieldsByCatalog() {
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<Field>()
                .isNotNull(Field::getCatalogId)
                .ne(Field::getStatus, -1);
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(Field::getOrganId, organs);
        }
        Map<Integer, Integer> map = new HashMap<>();
        fieldMapper.selectList(qw).forEach(f -> map.merge(f.getCatalogId(), 1, Integer::sum));
        return map;
    }

    /** Check whether candidate is a descendant of id */
    private boolean isDescendant(Integer id, Integer candidate) {
        Set<Integer> visited = new HashSet<>();
        Integer cur = candidate;
        while (cur != null && cur != ROOT_PARENT && visited.add(cur)) {
            if (cur.equals(id)) {
                return true;
            }
            KnowledgeTree parent = knowledgeTreeMapper.selectById(cur);
            cur = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    /** Build a tree from a flat list */
    private List<TreeNodeVO> buildTree(List<TreeNodeVO> flat) {
        Map<Integer, TreeNodeVO> byId = flat.stream()
                .collect(Collectors.toMap(TreeNodeVO::getId, v -> v, (a, b) -> a));
        List<TreeNodeVO> roots = new ArrayList<>();
        for (TreeNodeVO vo : flat) {
            Integer pid = vo.getParentId();
            if (pid == null || pid == ROOT_PARENT || !byId.containsKey(pid)) {
                roots.add(vo);
            } else {
                byId.get(pid).getChildren().add(vo);
            }
        }
        return roots;
    }
}
