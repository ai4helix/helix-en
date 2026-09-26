package com.helix.console.datamanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.datamanage.dto.FieldSaveDTO;
import com.helix.console.datamanage.dto.FieldVO;
import com.helix.console.datamanage.entity.Field;
import com.helix.console.datamanage.mapper.FieldMapper;
import com.helix.console.datamanage.service.FieldService;
import com.helix.console.common.BizException;
import com.helix.console.knowledge.entity.KnowledgeTree;
import com.helix.console.knowledge.enums.TreeType;
import com.helix.console.knowledge.mapper.KnowledgeTreeMapper;
import com.helix.console.system.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FieldServiceImpl implements FieldService {

    private final FieldMapper fieldMapper;
    private final KnowledgeTreeMapper knowledgeTreeMapper;

    @Override
    public PageResult<FieldVO> page(String keyword, Integer fieldTypeId, Integer isOutput,
                                    Integer catalogId, Boolean recycle, long pageNo, long pageSize) {
        LambdaQueryWrapper<Field> qw = buildQuery(keyword, fieldTypeId, isOutput);
        applyCatalogFilter(qw, catalogId);
        // Recycle bin view queries status=-1; all other views exclude the recycle bin
        if (Boolean.TRUE.equals(recycle)) {
            qw.eq(Field::getStatus, -1);
        } else {
            qw.ne(Field::getStatus, -1);
        }
        qw.orderByAsc(Field::getCatalogId).orderByAsc(Field::getId);
        Page<Field> page = fieldMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return PageResult.of(toVOList(page.getRecords()), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public List<FieldVO> listAll() {
        LambdaQueryWrapper<Field> qw = buildQuery(null, null, null);
        qw.ne(Field::getStatus, -1);
        qw.orderByAsc(Field::getCatalogId).orderByAsc(Field::getId);
        return toVOList(fieldMapper.selectList(qw));
    }

    @Override
    public List<FieldVO> listByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        // Filter out fields of other organizations
        return toVOList(fieldMapper.selectBatchIds(ids).stream()
                .filter(f -> TenantScope.isVisible(f.getOrganId() == null ? null : f.getOrganId().longValue()))
                .collect(Collectors.toList()));
    }

    @Override
    public List<Map<String, Object>> listFieldTypes() {
        // Field-type grouping uses field_typeid + name for now, avoiding an extra table dependency (excludes the recycle bin)
        List<Field> all = fieldMapper.selectList(buildQuery(null, null, null).ne(Field::getStatus, -1));
        Map<Integer, List<Field>> grouped = all.stream()
                .filter(f -> f.getFieldTypeid() != null)
                .collect(Collectors.groupingBy(Field::getFieldTypeid, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((typeId, fields) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", typeId);
            m.put("name", typeName(typeId));
            m.put("count", fields.size());
            result.add(m);
        });
        return result;
    }


    // ------------------------------------------------------------------ Single-record maintenance

    @Override
    public FieldVO create(FieldSaveDTO dto) {
        Integer organId = TenantScope.writeOrgan((Integer) null);
        String fieldEn = normalizeEn(dto.getFieldEn());
        if (StringUtils.isBlank(dto.getFieldCn())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Field Chinese name must not be empty");
        }
        assertEnUnique(fieldEn, organId, null);
        Field f = new Field();
        applySave(f, dto, fieldEn, organId);
        f.setStatus(1);
        f.setCreatedTime(java.time.LocalDateTime.now());
        f.setUpdatedTime(java.time.LocalDateTime.now());
        fieldMapper.insert(f);
        return toVOList(Collections.singletonList(fieldMapper.selectById(f.getId()))).get(0);
    }

    @Override
    public FieldVO update(Integer id, FieldSaveDTO dto) {
        Field f = mustOwn(id);
        String fieldEn = normalizeEn(dto.getFieldEn());
        if (StringUtils.isBlank(dto.getFieldCn())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Field Chinese name must not be empty");
        }
        // Platform public fields (organ 0) are read-only for ordinary tenants: already blocked in mustOwn; uniqueness check excludes self
        assertEnUnique(fieldEn, f.getOrganId(), id);
        applySave(f, dto, fieldEn, f.getOrganId());
        f.setUpdatedTime(java.time.LocalDateTime.now());
        fieldMapper.updateById(f);
        return toVOList(Collections.singletonList(fieldMapper.selectById(id))).get(0);
    }

    @Override
    public void delete(Integer id) {
        Field f = mustOwn(id);
        // Platform public fields (organ 0) are a shared dictionary for tenants; no tenant can delete them, only platform admins (writeOrgan=0) can
        fieldMapper.deleteById(f.getId());
    }

    @Override
    public void moveCatalog(Integer id, Integer catalogId) {
        Field f = mustOwn(id);
        assertCatalogValid(catalogId);
        if (Objects.equals(f.getCatalogId(), catalogId)) {
            return; // Drop in place
        }
        f.setCatalogId(catalogId);
        f.setUpdatedTime(java.time.LocalDateTime.now());
        fieldMapper.updateById(f);
    }


    // ------------------------- Recycle bin (soft delete) -------------------------

    @Override
    public void recycle(List<Integer> ids) {
        setStatus(ids, -1);
    }

    @Override
    public void restore(List<Integer> ids) {
        setStatus(ids, 1);
    }

    @Override
    public void deletePermanently(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // Only fields of this organization already in the recycle bin (status=-1) can be physically deleted
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<>();
        qw.in(Field::getId, ids);
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(Field::getOrganId, organs);
        }
        qw.eq(Field::getStatus, -1);
        fieldMapper.delete(qw);
    }

    /** Batch-update status (only affects fields visible to this organization; other attributes untouched) */
    private void setStatus(List<Integer> ids, int status) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<>();
        qw.in(Field::getId, ids);
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(Field::getOrganId, organs);
        }
        Field upd = new Field();
        upd.setStatus(status);
        upd.setUpdatedTime(java.time.LocalDateTime.now());
        fieldMapper.update(upd, qw);
    }

    /** Validate the field exists and belongs to this organization (platform public fields are read-only for ordinary tenants and cannot be modified or deleted) */
    private Field mustOwn(Integer id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Field id must not be empty");
        }
        Field f = fieldMapper.selectById(id);
        if (f == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Field not found or already deleted");
        }
        Integer writeOrgan = TenantScope.writeOrgan((Integer) null);
        Integer owner = f.getOrganId() == null ? 0 : f.getOrganId();
        // Platform admins (writeOrgan=0) manage platform public fields; ordinary tenants manage only their own organization's fields
        boolean allowed = owner.equals(writeOrgan);
        if (!allowed) {
            throw BizException.of(ResultCode.FORBIDDEN,
                    owner == 0 ? "Platform public fields are maintained by the platform; tenants have read-only access"
                            : "No permission to operate on fields of other organizations");
        }
        return f;
    }

    private String normalizeEn(String fieldEn) {
        String en = StringUtils.trimToEmpty(fieldEn);
        if (!en.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
            throw BizException.of(ResultCode.PARAM_INVALID,
                    "Field English name must start with a letter and contain only letters/digits/underscores, length 2-50");
        }
        return en;
    }

    /** English-name uniqueness: checked within this organization + platform public (0), excluding self, to avoid ambiguity with the public dictionary */
    private void assertEnUnique(String fieldEn, Integer organId, Integer excludeId) {
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<>();
        qw.eq(Field::getFieldEn, fieldEn)
          .in(Field::getOrganId, Arrays.asList(0, organId == null ? 0 : organId));
        if (excludeId != null) {
            qw.ne(Field::getId, excludeId);
        }
        if (fieldMapper.selectCount(qw) > 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Field English name '" + fieldEn + "' already exists");
        }
    }

    private void applySave(Field f, FieldSaveDTO dto, String fieldEn, Integer organId) {
        f.setFieldEn(fieldEn);
        f.setFieldCn(StringUtils.trim(dto.getFieldCn()));
        f.setFieldTypeid(dto.getFieldTypeid() == null ? 1 : dto.getFieldTypeid());
        f.setValueType(dto.getValueType() == null ? 1 : dto.getValueType());
        f.setValueScope(StringUtils.trimToNull(dto.getValueScope()));
        f.setIsDerivative(dto.getIsDerivative() == null ? 0 : dto.getIsDerivative());
        f.setIsOutput(dto.getIsOutput() == null ? 0 : dto.getIsOutput());
        f.setOrganId(organId);
        // Catalog (null = uncategorized, a valid value)
        assertCatalogValid(dto.getCatalogId());
        f.setCatalogId(dto.getCatalogId());
    }

    // ------------------------------------------------------------------ Catalog tree

    /** All visible nodes of the FIELD tree ([0 platform public, this organization]) */
    private List<KnowledgeTree> visibleCatalogNodes() {
        LambdaQueryWrapper<KnowledgeTree> qw = new LambdaQueryWrapper<KnowledgeTree>()
                .eq(KnowledgeTree::getTreeType, TreeType.FIELD.getCode())
                .eq(KnowledgeTree::getStatus, 1)
                .isNull(KnowledgeTree::getEngineId);
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(KnowledgeTree::getOrganId, organs);
        }
        return knowledgeTreeMapper.selectList(qw);
    }

    /** catalogId filter: matches the catalog and all its descendants (null catalog = query all) */
    private void applyCatalogFilter(LambdaQueryWrapper<Field> qw, Integer catalogId) {
        if (catalogId == null) {
            return;
        }
        Set<Integer> ids = catalogWithDescendants(catalogId);
        if (ids.isEmpty()) {
            // Catalog missing/invisible: use an impossible value to avoid degrading to a full scan
            qw.eq(Field::getCatalogId, -1);
            return;
        }
        qw.in(Field::getCatalogId, ids);
    }

    private Set<Integer> catalogWithDescendants(Integer catalogId) {
        List<KnowledgeTree> nodes = visibleCatalogNodes();
        Map<Integer, List<Integer>> children = new HashMap<>();
        for (KnowledgeTree n : nodes) {
            children.computeIfAbsent(n.getParentId() == null ? 0 : n.getParentId(), k -> new ArrayList<>())
                    .add(n.getId());
        }
        Set<Integer> result = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(catalogId);
        while (!stack.isEmpty()) {
            Integer cur = stack.pop();
            if (!result.add(cur)) {
                continue;
            }
            for (Integer c : children.getOrDefault(cur, Collections.emptyList())) {
                stack.push(c);
            }
        }
        return result;
    }

    /** Validate the catalog exists, belongs to the FIELD tree, and is visible to the current user */
    private void assertCatalogValid(Integer catalogId) {
        if (catalogId == null) {
            return;
        }
        KnowledgeTree node = knowledgeTreeMapper.selectById(catalogId);
        if (node == null || node.getTreeType() == null
                || node.getTreeType() != TreeType.FIELD.getCode() || node.getStatus() == null || node.getStatus() != 1) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Field catalog not found: " + catalogId);
        }
        TenantScope.checkVisible(node.getOrganId() == null ? null : node.getOrganId().longValue());
    }


    // ------------------------------------------------------------------ Internal methods

    private LambdaQueryWrapper<Field> buildQuery(String keyword, Integer fieldTypeId, Integer isOutput) {
        LambdaQueryWrapper<Field> qw = new LambdaQueryWrapper<>();
        // Organization users only see the field dictionary of [0 platform public, this organization]
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(Field::getOrganId, organs);
        }
        if (fieldTypeId != null) {
            qw.eq(Field::getFieldTypeid, fieldTypeId);
        }
        if (isOutput != null) {
            qw.eq(Field::getIsOutput, isOutput);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like(Field::getFieldEn, keyword).or().like(Field::getFieldCn, keyword));
        }
        return qw;
    }

    /** Mapping from field type id to name. Fixed dictionary for now; replace after t_field_type is integrated. */
    private String typeName(Integer typeId) {
        switch (typeId) {
            case 1: return "Basic Info";
            case 2: return "Credit Info";
            case 3: return "Income & Liabilities";
            case 4: return "Behavior Score";
            default: return "Other";
        }
    }

    private List<FieldVO> toVOList(List<Field> fields) {
        if (fields.isEmpty()) {
            return new ArrayList<>();
        }
        // Catalog name lookup: load FIELD tree nodes once, id -> name
        Map<Integer, String> catalogNames = new HashMap<>();
        for (KnowledgeTree n : visibleCatalogNodes()) {
            catalogNames.put(n.getId(), n.getName());
        }
        return fields.stream().map(f -> {
            FieldVO vo = new FieldVO();
            vo.setId(f.getId());
            vo.setFieldEn(f.getFieldEn());
            vo.setFieldCn(f.getFieldCn());
            vo.setFieldTypeid(f.getFieldTypeid());
            vo.setFieldTypeName(typeName(f.getFieldTypeid()));
            vo.setCatalogId(f.getCatalogId());
            vo.setCatalogName(f.getCatalogId() == null ? null : catalogNames.get(f.getCatalogId()));
            vo.setStatus(f.getStatus());
            vo.setValueType(f.getValueType());
            vo.setValueScope(f.getValueScope());
            vo.setIsDerivative(f.getIsDerivative());
            vo.setIsOutput(f.getIsOutput());
            return vo;
        }).collect(Collectors.toList());
    }
}
