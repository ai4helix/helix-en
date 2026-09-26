package com.helix.console.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.engine.mapper.NodeKnowledgeRelMapper;
import com.helix.console.engine.entity.NodeKnowledgeRel;
import com.helix.console.knowledge.dto.RuleConditionDTO;
import com.helix.console.knowledge.dto.RuleConditionNodeDTO;
import com.helix.console.knowledge.dto.RuleSaveDTO;
import com.helix.console.knowledge.dto.RuleVO;
import com.helix.console.knowledge.entity.KnowledgeTree;
import com.helix.console.knowledge.entity.Rule;
import com.helix.console.knowledge.entity.RuleCondition;
import com.helix.console.knowledge.mapper.KnowledgeTreeMapper;
import com.helix.console.datamanage.entity.Field;
import com.helix.console.datamanage.mapper.FieldMapper;
import com.helix.console.knowledge.entity.RuleHistory;
import com.helix.console.knowledge.mapper.RuleConditionMapper;
import com.helix.console.knowledge.mapper.RuleHistoryMapper;
import com.helix.console.knowledge.mapper.RuleMapper;
import com.helix.console.knowledge.service.RuleService;
import com.helix.console.system.security.TenantScope;
import com.helix.console.knowledge.support.RuleAstBuilder;
import com.helix.console.knowledge.support.RuleExpressionBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rule service implementation.
 *
 * <p>Status convention (inherited from the original DB semantics):
 * <ul>
 *   <li>{@code 1} enabled, {@code 0} disabled, {@code -1} recycle bin;</li>
 *   <li>the execution engine only loads rules with {@code status = 1}.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleServiceImpl implements RuleService {

    /** Recycle bin status */
    private static final int STATUS_RECYCLE = -1;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    private final RuleMapper ruleMapper;
    private final FieldMapper fieldMapper;
    private final RuleHistoryMapper ruleHistoryMapper;
    private final RuleConditionMapper ruleConditionMapper;
    private final ObjectMapper objectMapper;
    private final KnowledgeTreeMapper knowledgeTreeMapper;
    private final NodeKnowledgeRelMapper nodeKnowledgeRelMapper;
    private final RuleExpressionBuilder expressionBuilder;
    private final RuleAstBuilder astBuilder;
    private final com.helix.console.engine.client.EngineClient engineClient;

    @Override
    public PageResult<RuleVO> page(Integer parentId, Integer engineId, Integer status, String keyword,
                                   long pageNo, long pageSize) {
        LambdaQueryWrapper<Rule> qw = new LambdaQueryWrapper<>();
        // Org users only see rules in [0 platform common, own organization]
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(Rule::getOrganId, organs);
        }
        if (parentId != null) {
            qw.eq(Rule::getParentId, parentId);
        }
        if (engineId != null) {
            qw.eq(Rule::getEngineId, engineId);
        } else {
            qw.isNull(Rule::getEngineId);
        }
        // Exclude recycle bin when status is not specified
        qw.eq(Rule::getStatus, status == null ? STATUS_ENABLED : status);
        if (status == null) {
            // Default display: enabled and disabled, excluding the recycle bin
            qw.in(Rule::getStatus, STATUS_DISABLED, STATUS_ENABLED);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like(Rule::getName, keyword).or().like(Rule::getCode, keyword));
        }
        qw.orderByAsc(Rule::getPriority).orderByDesc(Rule::getId);

        Page<Rule> page = ruleMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<RuleVO> vos = toVOList(page.getRecords());

        // Fill in catalog names
        fillParentName(vos);
        return PageResult.of(vos, page.getTotal(), pageNo, pageSize);
    }

    @Override
    public RuleVO detail(Integer id) {
        Rule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw BizException.of(ResultCode.RULE_NOT_FOUND);
        }
        // Viewing rules of other organizations is forbidden
        TenantScope.checkVisible(rule.getOrganId() == null ? null : rule.getOrganId().longValue());
        RuleVO vo = toVO(rule);
        vo.setConditions(loadConditions(id));

        // Child rules (rule set scenario)
        List<Rule> children = ruleMapper.selectList(new LambdaQueryWrapper<Rule>()
                .eq(Rule::getParentId, rule.getId())
                .ne(Rule::getStatus, STATUS_RECYCLE)
                .orderByAsc(Rule::getPriority));
        if (!children.isEmpty()) {
            vo.setShowType(1);
            vo.setChildren(toVOList(children));
        }
        fillParentName(Collections.singletonList(vo));
        return vo;
    }

    @Override
    public List<RuleVO> listByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Rule> rules = ruleMapper.selectBatchIds(ids);
        return toVOList(rules);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer create(RuleSaveDTO dto) {
        validateDuplicate(dto.getName(), dto.getCode(), null);

        Rule rule = new Rule();
        applyFields(rule, dto);
        // Admin users default to platform common (0); org users are forced to their own organization
        rule.setOrganId(TenantScope.writeOrgan(dto.getOrganId() == null ? null : dto.getOrganId().longValue()).intValue());
        rule.setStatus(STATUS_ENABLED);
        rule.setType(dto.getType() == null ? 1 : dto.getType());
        rule.setPriority(dto.getPriority() == null ? 100 : dto.getPriority());
        validateConditionFields(dto.getConditions());
        rule.setContent(expressionBuilder.build(dto.getConditions(), dto.getIsNon()));
        rule.setDefinition(buildDefinition(dto));
        rule.setLastLogical(resolveLastLogical(dto.getConditions()));
        applyV2Fields(rule, dto);
        rule.setCreatedTime(LocalDateTime.now());
        rule.setUpdatedTime(LocalDateTime.now());
        ruleMapper.insert(rule);

        saveConditions(rule.getId(), dto.getConditions());
        saveAstConditions(rule.getId(), dto);
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void update(RuleSaveDTO dto) {
        if (dto.getId() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Rule id must not be blank");
        }
        Rule exist = ruleMapper.selectById(dto.getId());
        if (exist == null) {
            throw BizException.of(ResultCode.RULE_NOT_FOUND);
        }
        // Modifying rules of other organizations is forbidden (platform common rules can only be changed by admin users)
        TenantScope.checkVisible(exist.getOrganId() == null ? null : exist.getOrganId().longValue());
        validateDuplicate(dto.getName(), dto.getCode(), dto.getId());

        applyFields(exist, dto);
        validateConditionFields(dto.getConditions());
        saveHistory(exist);
        exist.setContent(expressionBuilder.build(dto.getConditions(), dto.getIsNon()));
        exist.setDefinition(buildDefinition(dto));
        exist.setLastLogical(resolveLastLogical(dto.getConditions()));
        applyV2Fields(exist, dto);
        exist.setUpdatedTime(LocalDateTime.now());
        ruleMapper.updateById(exist);

        // Single source of truth for conditions = AST (the flat t_rule_field double-write is deprecated;
        // the editor now restores from the AST).
        // Rebuild the AST in full -- the engine reads only this table at execution time,
        // so it must be updated in the same transaction as the rule main table.
        saveAstConditions(dto.getId(), dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer copy(Integer id) {
        Rule src = ruleMapper.selectById(id);
        if (src == null) {
            throw BizException.of(ResultCode.RULE_NOT_FOUND);
        }
        // Copying rules of other organizations is forbidden
        TenantScope.checkVisible(src.getOrganId() == null ? null : src.getOrganId().longValue());
        Rule copy = new Rule();
        copy.setName(buildCopyName(src.getName()));
        copy.setCode(StringUtils.isBlank(src.getCode()) ? null : src.getCode() + "_copy");
        copy.setDescription(src.getDescription());
        copy.setPriority(src.getPriority());
        copy.setParentId(src.getParentId());
        copy.setCreatedBy(src.getCreatedBy());
        // Copy ownership stays with the operating organization (admin users copying platform common rules still land on platform common)
        copy.setOrganId(src.getOrganId());
        copy.setEngineId(src.getEngineId());
        copy.setType(src.getType());
        copy.setStatus(STATUS_DISABLED);
        copy.setIsNon(src.getIsNon());
        copy.setContent(src.getContent());
        copy.setRuleType(src.getRuleType());
        copy.setRuleAudit(src.getRuleAudit());
        copy.setScore(src.getScore());
        copy.setLastLogical(src.getLastLogical());
        // Copy the v2 fields too so the copy can be executed by the engine directly
        copy.setResultTypeV2(src.getResultTypeV2());
        copy.setScoreValue(src.getScoreValue());
        copy.setConditionVersion(src.getConditionVersion());
        copy.setCreatedTime(LocalDateTime.now());
        copy.setUpdatedTime(LocalDateTime.now());
        ruleMapper.insert(copy);

        // Copy conditions: copy the AST as-is (the only source of condition copies)
        copyAst(id, copy.getId());
        return copy.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void changeStatus(List<Integer> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if (status == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Status must not be blank");
        }
        // Only applies to rules visible to this organization
        ids = filterVisible(ids);
        for (Integer id : ids) {
            Rule rule = new Rule();
            rule.setId(id);
            rule.setStatus(status);
            rule.setUpdatedTime(LocalDateTime.now());
            ruleMapper.updateById(rule);
        }
    }

    @Override
    public void moveCatalog(Integer id, Integer parentId) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Rule id must not be blank");
        }
        if (parentId == null || parentId <= 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Target catalog must not be blank (rules must hang under a catalog)");
        }
        Rule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw BizException.of(ResultCode.RULE_NOT_FOUND);
        }
        TenantScope.checkVisible(rule.getOrganId() == null ? null : rule.getOrganId().longValue());
        // Target must be a visible catalog node under the rule tree (treeType=0)
        com.helix.console.knowledge.entity.KnowledgeTree target = knowledgeTreeMapper.selectById(parentId);
        if (target == null || target.getTreeType() == null
                || target.getTreeType() != com.helix.console.knowledge.enums.TreeType.RULE.getCode()
                || target.getStatus() == null || target.getStatus() != 1) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Target catalog does not exist: " + parentId);
        }
        TenantScope.checkVisible(target.getOrganId() == null ? null : target.getOrganId().longValue());
        if (parentId.equals(rule.getParentId())) {
            return; // dropped in place
        }
        Rule upd = new Rule();
        upd.setId(id);
        upd.setParentId(parentId);
        upd.setUpdatedTime(LocalDateTime.now());
        ruleMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void moveToRecycle(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // Only applies to rules visible to this organization
        ids = filterVisible(ids);
        // Rules referenced by decision flow nodes cannot be deleted, avoiding missing rules at execution time
        List<NodeKnowledgeRel> refs = nodeKnowledgeRelMapper.selectList(
                new LambdaQueryWrapper<NodeKnowledgeRel>().in(NodeKnowledgeRel::getKnowledgeId, ids));
        if (!refs.isEmpty()) {
            Set<Integer> refIds = refs.stream()
                    .map(NodeKnowledgeRel::getKnowledgeId).collect(Collectors.toSet());
            List<Rule> used = ruleMapper.selectBatchIds(refIds);
            String names = used.stream().map(Rule::getName).collect(Collectors.joining(", "));
            throw BizException.of(ResultCode.DATA_IN_USE,
                    "The following rules are referenced by decision flow nodes and cannot be deleted: " + names);
        }
        changeStatus(ids, STATUS_RECYCLE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void restore(List<Integer> ids) {
        changeStatus(ids, STATUS_DISABLED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void removePermanently(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // Only permanently delete rules visible to this organization
        ids = filterVisible(ids);
        for (Integer id : ids) {
            Rule rule = ruleMapper.selectById(id);
            if (rule == null) {
                continue;
            }
            if (rule.getStatus() == null || rule.getStatus() != STATUS_RECYCLE) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Only rules in the recycle bin can be permanently deleted");
            }
        }
        ruleMapper.deleteBatchIds(ids);
    }

    // ------------------------------------------------------------------ Internal methods

    private void applyFields(Rule rule, RuleSaveDTO dto) {
        rule.setName(dto.getName());
        rule.setCode(dto.getCode());
        rule.setDescription(dto.getDescription());
        rule.setParentId(dto.getParentId());
        if (dto.getEngineId() != null) {
            rule.setEngineId(dto.getEngineId());
        }
        // rule_audit = 2 (reject) normalizes rule_type to 0, consistent with the original semantics
        Integer ruleType = dto.getRuleType();
        if (dto.getRuleAudit() != null && dto.getRuleAudit() == 2) {
            ruleType = 0;
        }
        rule.setRuleType(ruleType == null ? 1 : ruleType);
        rule.setRuleAudit(dto.getRuleAudit());
        rule.setScore(dto.getScore());
        rule.setIsNon(dto.getIsNon() == null ? 0 : dto.getIsNon());
        if (dto.getPriority() != null) {
            rule.setPriority(dto.getPriority());
        }
    }

    /**
     * t_rule_field is no longer written; the only save path for conditions is
     * {@link #saveAstConditions}. Kept as an empty implementation for potential
     * callers (currently none).
     */
    @SuppressWarnings("unused")
    private void saveConditions(Integer ruleId, List<RuleConditionDTO> conditions) {
        // no-op: single source of truth for conditions is t_rule_condition; the old flat double-write table is no longer maintained
    }

    /**
     * Rule condition echo: single source of truth = condition AST (t_rule_condition);
     * engine execution reads only the AST -- the editor should display exactly what the engine executes.
     *
     * <p>Restoration rules (strict inverse of {@code RuleAstBuilder.build}):</p>
     * <ul>
     *   <li>Leaf nodes (nodeType=1) -> one editor condition: fieldEn/fieldId from fieldCode,
     *       operator and value copied as-is;</li>
     *   <li>Connector decided by the parent group node: parent OR(3) -> "||", otherwise (AND(2)/NOT(4)) -> "&&";</li>
     *   <li>The last condition's logical is always -1, consistent with the save-time convention.</li>
     * </ul>
     */
    private List<RuleConditionDTO> loadConditions(Integer ruleId) {
        List<RuleCondition> rows = ruleConditionMapper.selectList(
                new LambdaQueryWrapper<RuleCondition>()
                        .eq(RuleCondition::getRuleId, ruleId)
                        .orderByAsc(RuleCondition::getParentId)
                        .orderByAsc(RuleCondition::getSortNo));
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, RuleCondition> byId = new HashMap<>();
        for (RuleCondition r : rows) {
            byId.put(r.getId(), r);
        }
        List<RuleCondition> leaves = rows.stream()
                .filter(r -> r.getNodeType() != null && r.getNodeType() == RuleAstBuilder.NODE_LEAF)
                .collect(Collectors.toList());

        List<RuleConditionDTO> out = new ArrayList<>(leaves.size());
        for (int i = 0; i < leaves.size(); i++) {
            RuleCondition leaf = leaves.get(i);
            RuleConditionDTO c = new RuleConditionDTO();
            c.setId(leaf.getId() == null ? null : leaf.getId().intValue());
            c.setOperator(leaf.getOperator());
            c.setFieldValue(leaf.getValue());
            c.setFieldEn(leaf.getFieldCode());
            c.setFieldId(leaf.getFieldCode());
            if (i == leaves.size() - 1) {
                c.setLogical("-1");
            } else {
                RuleCondition parent = leaf.getParentId() == null ? null : byId.get(leaf.getParentId());
                boolean or = parent != null && parent.getNodeType() != null
                        && parent.getNodeType() == RuleAstBuilder.NODE_OR;
                c.setLogical(or ? "||" : "&&");
            }
            out.add(c);
        }
        return out;
    }

    /**
     * Restore the condition AST tree: rebuild the {@code t_rule_condition}
     * parent_id / node_type adjacency list into a nested structure for the
     * frontend X6 condition subtree visualization. Inverse of the flattening in
     * {@link #loadConditions} -- this keeps the real AND/OR/NOT nesting.
     */
    @Override
    public List<RuleConditionNodeDTO> getRuleAst(Integer ruleId) {
        List<RuleCondition> rows = ruleConditionMapper.selectList(
                new LambdaQueryWrapper<RuleCondition>()
                        .eq(RuleCondition::getRuleId, ruleId)
                        .orderByAsc(RuleCondition::getParentId)
                        .orderByAsc(RuleCondition::getSortNo));
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, RuleCondition> byId = rows.stream()
                .collect(Collectors.toMap(RuleCondition::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<RuleCondition>> childrenByParent = rows.stream()
                .filter(r -> r.getParentId() != null)
                .collect(Collectors.groupingBy(
                        RuleCondition::getParentId, LinkedHashMap::new, Collectors.toList()));

        List<RuleConditionNodeDTO> roots = new ArrayList<>();
        for (RuleCondition r : rows) {
            if (r.getParentId() == null) {
                roots.add(toAstNode(r, childrenByParent));
            }
        }
        return roots;
    }

    private RuleConditionNodeDTO toAstNode(RuleCondition r,
                                           Map<Long, List<RuleCondition>> childrenByParent) {
        RuleConditionNodeDTO node = new RuleConditionNodeDTO();
        node.setId(r.getId());
        node.setNodeType(r.getNodeType());
        node.setFieldCode(r.getFieldCode());
        node.setOperator(r.getOperator());
        node.setValue(r.getValue());
        List<RuleCondition> kids = childrenByParent.get(r.getId());
        if (kids != null) {
            for (RuleCondition k : kids) {
                node.getChildren().add(toAstNode(k, childrenByParent));
            }
        }
        return node;
    }

    private void validateDuplicate(String name, String code, Integer excludeId) {
        // Duplicate check is limited to organizations visible to the current user (organizations may reuse names; platform common does not conflict)
        List<Long> organs = TenantScope.visibleOrgans();
        if (StringUtils.isNotBlank(name)) {
            LambdaQueryWrapper<Rule> qw = new LambdaQueryWrapper<Rule>()
                    .eq(Rule::getName, name).ne(Rule::getStatus, STATUS_RECYCLE);
            if (organs != null) {
                qw.in(Rule::getOrganId, organs);
            }
            if (excludeId != null) {
                qw.ne(Rule::getId, excludeId);
            }
            if (ruleMapper.selectCount(qw) > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Rule name already exists: " + name);
            }
        }
        if (StringUtils.isNotBlank(code)) {
            LambdaQueryWrapper<Rule> qw = new LambdaQueryWrapper<Rule>()
                    .eq(Rule::getCode, code).ne(Rule::getStatus, STATUS_RECYCLE);
            if (organs != null) {
                qw.in(Rule::getOrganId, organs);
            }
            if (excludeId != null) {
                qw.ne(Rule::getId, excludeId);
            }
            if (ruleMapper.selectCount(qw) > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Rule code already exists: " + code);
            }
        }
    }

    private String resolveLastLogical(List<RuleConditionDTO> conditions) {
        return conditions == null || conditions.isEmpty() ? null : "-1";
    }

    private String buildCopyName(String name) {
        String base = StringUtils.defaultString(name);
        String candidate = base + "_Copy";
        List<Long> organs = TenantScope.visibleOrgans();
        int i = 1;
        while (true) {
            LambdaQueryWrapper<Rule> qw = new LambdaQueryWrapper<Rule>()
                    .eq(Rule::getName, candidate).ne(Rule::getStatus, STATUS_RECYCLE);
            if (organs != null) {
                qw.in(Rule::getOrganId, organs);
            }
            if (ruleMapper.selectCount(qw) == 0) {
                break;
            }
            candidate = base + "_Copy" + (++i);
        }
        return candidate;
    }

    /**
     * Filter the id list to rule ids visible to the current user.
     * Operations on invisible rules by org users are silently skipped
     * (platform common and admin users are unaffected).
     */
    private List<Integer> filterVisible(List<Integer> ids) {
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs == null || ids == null || ids.isEmpty()) {
            return ids;
        }
        return ruleMapper.selectBatchIds(ids).stream()
                .filter(r -> TenantScope.isVisible(r.getOrganId() == null ? null : r.getOrganId().longValue()))
                .map(Rule::getId)
                .collect(Collectors.toList());
    }

    private List<RuleVO> toVOList(List<Rule> rules) {
        return rules.stream().map(this::toVO).collect(Collectors.toList());
    }

    private RuleVO toVO(Rule rule) {
        RuleVO vo = new RuleVO();
        vo.setId(rule.getId());
        vo.setName(rule.getName());
        vo.setCode(rule.getCode());
        vo.setDescription(rule.getDescription());
        vo.setPriority(rule.getPriority());
        vo.setParentId(rule.getParentId());
        vo.setType(rule.getType());
        vo.setEngineId(rule.getEngineId());
        vo.setStatus(rule.getStatus());
        vo.setRuleType(rule.getRuleType());
        vo.setRuleAudit(rule.getRuleAudit());
        vo.setScore(rule.getScore());
        vo.setIsNon(rule.getIsNon() == null ? Integer.valueOf(0) : rule.getIsNon());
        vo.setContent(rule.getContent());
        vo.setCreatedTime(rule.getCreatedTime());
        vo.setUpdatedTime(rule.getUpdatedTime());
        return vo;
    }

    /** Batch fill catalog names, avoiding N+1 queries */
    private void fillParentName(List<RuleVO> vos) {
        Set<Integer> parentIds = vos.stream()
                .map(RuleVO::getParentId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return;
        }
        Map<Integer, String> nameMap = knowledgeTreeMapper.selectBatchIds(parentIds).stream()
                .collect(Collectors.toMap(KnowledgeTree::getId, KnowledgeTree::getName, (a, b) -> a));
        vos.forEach(v -> v.setParentName(nameMap.get(v.getParentId())));
    }
private void saveHistory(Rule exist) {

        RuleHistory h = new RuleHistory();

        h.setRuleId(Long.valueOf(exist.getId()));

        h.setVersion(ruleHistoryMapper.selectCount(new LambdaQueryWrapper<RuleHistory>(){}.eq(RuleHistory::getRuleId, exist.getId())).intValue() + 1);

        h.setName(exist.getName());

        h.setContent(exist.getContent());

        h.setDefinition(exist.getDefinition());

        h.setCreatedBy(exist.getCreatedBy() == null ? null : Long.valueOf(exist.getCreatedBy()));

        h.setCreatedTime(java.time.LocalDateTime.now());

        ruleHistoryMapper.insert(h);

    }


    /** Structured definition JSON: condition groups + conclusion (single source of truth; loaded as a condition AST on the engine side) */

    private String buildDefinition(RuleSaveDTO dto) {

        try {

            java.util.Map<String, Object> def = new java.util.LinkedHashMap<>();

            def.put("isNon", dto.getIsNon() == null ? 0 : dto.getIsNon());

            def.put("ruleType", dto.getRuleType() == null ? "2" : String.valueOf(dto.getRuleType()));

            def.put("score", dto.getScore());

            java.util.List<java.util.Map<String, Object>> conds = new java.util.ArrayList<>();

            if (dto.getConditions() != null) {

                for (RuleConditionDTO c : dto.getConditions()) {

                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();

                    m.put("field", resolveVarName(c));

                    m.put("operator", c.getOperator());

                    m.put("value", c.getFieldValue());

                    m.put("logical", c.getLogical());

                    conds.add(m);

                }

            }

            def.put("conditions", conds);

            return objectMapper.writeValueAsString(def);

        } catch (Exception e) {

            throw BizException.of(ResultCode.PARAM_INVALID, "Failed to serialize rule definition: " + e.getMessage());

        }

    }


    /** Field dictionary validation: variables referenced by conditions must exist in t_field, preventing dirty rules from entering the DB */

    private void validateConditionFields(java.util.List<RuleConditionDTO> conditions) {

        if (conditions == null || conditions.isEmpty()) {

            return;

        }

        LambdaQueryWrapper<Field> fw = new LambdaQueryWrapper<Field>(){}.select(Field::getFieldEn);
        // Org users can only reference fields in [platform common, own organization]
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            fw.in(Field::getOrganId, organs);
        }
        java.util.Set<String> known = fieldMapper.selectList(fw).stream()

                .map(Field::getFieldEn).collect(java.util.stream.Collectors.toSet());

        for (RuleConditionDTO c : conditions) {

            String var = resolveVarName(c);

            if (!known.contains(var)) {

                throw BizException.of(ResultCode.PARAM_INVALID,

                        "Condition field does not exist in the field dictionary: " + var);

            }

        }

    }


    /**
     * Fill the v2 strongly typed fields.
     *
     * <p>Mapping rules (consistent with the migration script):
     * <ul>
     *   <li>{@code ruleType=1} (scoring) -> ADD_SCORE / SUB_SCORE by score sign;</li>
     *   <li>{@code ruleType != 1} -> DENY / MANUAL by resultType, default DENY.</li>
     * </ul>
     * Also sets {@code conditionVersion} to 2, marking that the rule already has an AST.</p>
     */
    private void applyV2Fields(Rule rule, RuleSaveDTO dto) {
        Integer ruleType = dto.getRuleType() == null ? rule.getRuleType() : dto.getRuleType();
        Integer score = dto.getScore() == null ? rule.getScore() : dto.getScore();

        if (ruleType != null && ruleType == 1) {
            // Scoring type: add/subtract score decided by the score sign
            rule.setResultTypeV2(score != null && score < 0 ? "SUB_SCORE" : "ADD_SCORE");
            rule.setScoreValue(score);
        } else {
            // Hard conclusions are decided by ruleAudit: 2=reject / 3=manual review (consistent with editor options).
            // Earlier implementations ignored this field, so changing "reject" to "manual review" still executed as reject.
            Integer audit = dto.getRuleAudit() != null ? dto.getRuleAudit() : rule.getRuleAudit();
            rule.setResultTypeV2(audit != null && audit == 3 ? "MANUAL" : "DENY");
            rule.setScoreValue(null);
        }
        rule.setConditionVersion(2);
    }

    /**
     * Write the condition AST (full rebuild).
     *
     * <p>Deletes the rule's previous AST rows first, then rebuilds -- rule
     * condition counts are small, rebuilding is more reliable than per-node diff,
     * and avoids tree corruption from partial updates.</p>
     *
     * <p>What is stored is the "execution-time source of truth": the engine
     * reads only this table when loading snapshots, so it must be updated in the
     * same transaction as the rule main table.</p>
     */
    private void saveAstConditions(Integer ruleId, RuleSaveDTO dto) {
        if (ruleId == null) {
            return;
        }
        ruleConditionMapper.delete(new LambdaQueryWrapper<RuleCondition>()
                .eq(RuleCondition::getRuleId, ruleId));

        RuleAstBuilder.AstNode root = astBuilder.build(dto.getConditions(), dto.getIsNon());
        if (root == null) {
            // Rule without conditions: the engine skips and warns at load time; keep no AST rows here
            return;
        }
        // Write the full tree rooted at root.
        // An early implementation "promoted" the composite root's children to top level, leaving AND/OR
        // semantics to the load-time "multiple roots = AND" implicit convention; changing that convention
        // would change rule meaning. Keeping the Group root explicitly keeps tree semantics
        // fully consistent with load-time parsing.
        insertAstNode(ruleId, root, null, 0);
    }

    /** Recursively write AST nodes */
    private void insertAstNode(Integer ruleId, RuleAstBuilder.AstNode node, Long parentId, int sortNo) {
        RuleCondition row = new RuleCondition();
        row.setRuleId(ruleId);
        row.setParentId(parentId);
        row.setNodeType(node.nodeType);
        row.setFieldCode(node.fieldCode);
        row.setOperator(node.operator);
        row.setValue(node.value);
        row.setSortNo(sortNo);
        row.setDepth(node.depth);
        row.setCreatedTime(LocalDateTime.now());
        row.setUpdatedTime(LocalDateTime.now());
        row.setDeleted(0);
        ruleConditionMapper.insert(row);

        int childSort = 0;
        for (RuleAstBuilder.AstNode child : node.children) {
            insertAstNode(ruleId, child, row.getId(), childSort++);
        }
    }

    /**
     * Copy the source rule's condition AST to the target rule.
     *
     * <p>Copy rather than re-parse: the source rule's AST may come from a past
     * migration (its original definition may not match current editor data);
     * copying the AST as-is guarantees the copy behaves exactly like the source rule.</p>
     */
    private void copyAst(Integer srcRuleId, Integer targetRuleId) {
        List<RuleCondition> rows = ruleConditionMapper.selectList(
                new LambdaQueryWrapper<RuleCondition>()
                        .eq(RuleCondition::getRuleId, srcRuleId)
                        .orderByAsc(RuleCondition::getParentId)
                        .orderByAsc(RuleCondition::getSortNo));
        if (rows.isEmpty()) {
            return;
        }
        // old id -> new id mapping, used to rebuild parent-child relations
        Map<Long, Long> idMap = new HashMap<Long, Long>();
        for (RuleCondition r : rows) {
            RuleCondition n = new RuleCondition();
            n.setRuleId(targetRuleId);
            n.setParentId(r.getParentId() == null ? null : idMap.get(r.getParentId()));
            n.setNodeType(r.getNodeType());
            n.setFieldCode(r.getFieldCode());
            n.setOperator(r.getOperator());
            n.setValue(r.getValue());
            n.setSortNo(r.getSortNo());
            n.setDepth(r.getDepth());
            n.setCreatedTime(LocalDateTime.now());
            n.setUpdatedTime(LocalDateTime.now());
            n.setDeleted(0);
            ruleConditionMapper.insert(n);
            idMap.put(r.getId(), n.getId());
        }
    }

    /**
     * Rule dry run: forwards conditions to helix-engine for per-condition evaluation.
     *
     * <p>Evaluation happens on the production engine side, so editor results
     * naturally match actual execution; this method only maps formats and forwards.</p>
     */
    @Override
    public java.util.Map<String, Object> dryRun(com.helix.console.knowledge.dto.DryRunReq req) {
        if (req == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Dry run parameters must not be blank");
        }
        return engineClient.eval(
                astBuilder.toFlatConditions(req.getConditions()),
                req.getIsNon(),
                req.getVariables());
    }

    /** Batch rule briefs: sorted by priority ascending, consistent with engine load order */
    @Override
    public java.util.List<com.helix.console.knowledge.dto.RuleBriefVO> listBriefs(java.util.List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        java.util.List<Rule> rules = ruleMapper.selectBatchIds(ids);
        rules.sort(java.util.Comparator.comparing(
                r -> r.getPriority() == null ? Integer.valueOf(100) : r.getPriority()));
        java.util.List<com.helix.console.knowledge.dto.RuleBriefVO> out =
                new java.util.ArrayList<>(rules.size());
        for (Rule r : rules) {
            com.helix.console.knowledge.dto.RuleBriefVO vo =
                    new com.helix.console.knowledge.dto.RuleBriefVO();
            vo.setId(r.getId());
            vo.setName(r.getName());
            vo.setCode(r.getCode());
            vo.setContent(r.getContent());
            vo.setResultTypeV2(r.getResultTypeV2());
            vo.setScoreValue(r.getScoreValue());
            vo.setPriority(r.getPriority());
            out.add(vo);
        }
        return out;
    }

    /** Resolve the variable name with the same convention as RuleExpressionBuilder.resolveVariable */

    private String resolveVarName(RuleConditionDTO c) {

        if (c.getFieldEn() != null && !c.getFieldEn().trim().isEmpty()) {

            return c.getFieldEn().trim();

        }

        String fieldId = c.getFieldId() == null ? "" : c.getFieldId().trim();

        if (fieldId.contains("|")) {

            String[] parts = fieldId.split("\\|", 2);

            if (parts.length > 1 && !parts[1].trim().isEmpty()) {

                return parts[1].trim();

            }

        }

        return fieldId;

    }
}
