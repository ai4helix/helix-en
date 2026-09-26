package com.helix.console.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.engine.entity.NodeKnowledgeRel;
import com.helix.console.engine.mapper.NodeKnowledgeRelMapper;
import com.helix.console.knowledge.dto.ScorecardDimensionDTO;
import com.helix.console.knowledge.dto.ScorecardSaveDTO;
import com.helix.console.knowledge.entity.Scorecard;
import com.helix.console.knowledge.mapper.ScorecardMapper;
import com.helix.console.knowledge.service.ScorecardService;
import com.helix.console.system.security.TenantScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scorecard service implementation.
 *
 * <p>{@code t_scorecard.score} stores a JSON array of dimension configs,
 * parsed and evaluated by helix-engine at runtime.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScorecardServiceImpl implements ScorecardService {

    private static final int STATUS_RECYCLE = -1;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    private final ScorecardMapper scorecardMapper;
    private final NodeKnowledgeRelMapper nodeKnowledgeRelMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<Scorecard> page(Integer parentId, Integer engineId, Integer status, String keyword,
                                      long pageNo, long pageSize) {
        LambdaQueryWrapper<Scorecard> qw = new LambdaQueryWrapper<>();
        // Org users only see scorecards in [0 platform common, own organization]
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(Scorecard::getOrganId, organs);
        }
        if (parentId != null) {
            qw.eq(Scorecard::getParentId, parentId);
        }
        if (engineId != null) {
            qw.eq(Scorecard::getEngineId, engineId);
        } else {
            qw.isNull(Scorecard::getEngineId);
        }
        if (status == null) {
            qw.in(Scorecard::getStatus, STATUS_DISABLED, STATUS_ENABLED);
        } else {
            qw.eq(Scorecard::getStatus, status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like(Scorecard::getName, keyword).or().like(Scorecard::getCode, keyword));
        }
        qw.orderByDesc(Scorecard::getId);

        Page<Scorecard> page = scorecardMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        // List page does not need dimension details; avoid transferring the redundant large field
        page.getRecords().forEach(s -> s.setScore(null));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public ScorecardSaveDTO detail(Integer id) {
        Scorecard sc = scorecardMapper.selectById(id);
        if (sc == null) {
            throw BizException.of(ResultCode.SCORECARD_NOT_FOUND);
        }
        // Viewing scorecards of other organizations is forbidden
        TenantScope.checkVisible(sc.getOrganId() == null ? null : sc.getOrganId().longValue());
        ScorecardSaveDTO dto = new ScorecardSaveDTO();
        dto.setId(sc.getId());
        dto.setName(sc.getName());
        dto.setCode(sc.getCode());
        dto.setDescription(sc.getDescription());
        dto.setVersion(sc.getVersion());
        dto.setParentId(sc.getParentId());
        dto.setType(sc.getType());
        dto.setEngineId(sc.getEngineId());
        dto.setDimensions(parseDimensions(sc.getScore()));
        return dto;
    }

    @Override
    public List<Scorecard> listByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Scorecard> list = scorecardMapper.selectBatchIds(ids);
        list.forEach(s -> s.setScore(null));
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer create(ScorecardSaveDTO dto) {
        validateDimensions(dto.getDimensions());
        validateDuplicate(dto.getName(), dto.getCode(), null);

        Scorecard sc = new Scorecard();
        applyFields(sc, dto);
        // Admin users default to platform common (0); org users are forced to their own organization
        sc.setOrganId(TenantScope.writeOrgan(dto.getOrganId() == null ? null : dto.getOrganId().longValue()).intValue());
        sc.setStatus(STATUS_ENABLED);
        sc.setType(dto.getType() == null ? 1 : dto.getType());
        sc.setScore(writeDimensions(dto.getDimensions()));
        sc.setCreatedTime(LocalDateTime.now());
        sc.setUpdatedTime(LocalDateTime.now());
        scorecardMapper.insert(sc);
        return sc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void update(ScorecardSaveDTO dto) {
        if (dto.getId() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Scorecard id must not be blank");
        }
        Scorecard exist = scorecardMapper.selectById(dto.getId());
        if (exist == null) {
            throw BizException.of(ResultCode.SCORECARD_NOT_FOUND);
        }
        // Modifying scorecards of other organizations is forbidden
        TenantScope.checkVisible(exist.getOrganId() == null ? null : exist.getOrganId().longValue());
        validateDimensions(dto.getDimensions());
        validateDuplicate(dto.getName(), dto.getCode(), dto.getId());

        applyFields(exist, dto);
        exist.setScore(writeDimensions(dto.getDimensions()));
        exist.setUpdatedTime(LocalDateTime.now());
        scorecardMapper.updateById(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer copy(Integer id) {
        Scorecard src = scorecardMapper.selectById(id);
        if (src == null) {
            throw BizException.of(ResultCode.SCORECARD_NOT_FOUND);
        }
        // Copying scorecards of other organizations is forbidden
        TenantScope.checkVisible(src.getOrganId() == null ? null : src.getOrganId().longValue());
        Scorecard copy = new Scorecard();
        copy.setName(buildCopyName(src.getName()));
        copy.setCode(StringUtils.isBlank(src.getCode()) ? null : src.getCode() + "_copy");
        copy.setDescription(src.getDescription());
        copy.setVersion(src.getVersion());
        copy.setParentId(src.getParentId());
        copy.setCreatedBy(src.getCreatedBy());
        // Copy ownership matches the source
        copy.setOrganId(src.getOrganId());
        copy.setEngineId(src.getEngineId());
        copy.setType(src.getType());
        copy.setStatus(STATUS_DISABLED);
        copy.setScore(src.getScore());
        copy.setCreatedTime(LocalDateTime.now());
        copy.setUpdatedTime(LocalDateTime.now());
        scorecardMapper.insert(copy);
        return copy.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void changeStatus(List<Integer> ids, Integer status) {
        if (ids == null || ids.isEmpty() || status == null) {
            return;
        }
        // Only applies to scorecards visible to this organization
        ids = filterVisible(ids);
        ids.forEach(id -> {
            Scorecard sc = new Scorecard();
            sc.setId(id);
            sc.setStatus(status);
            sc.setUpdatedTime(LocalDateTime.now());
            scorecardMapper.updateById(sc);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void moveToRecycle(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // Only applies to scorecards visible to this organization
        ids = filterVisible(ids);
        List<NodeKnowledgeRel> refs = nodeKnowledgeRelMapper.selectList(
                new LambdaQueryWrapper<NodeKnowledgeRel>().in(NodeKnowledgeRel::getKnowledgeId, ids));
        if (!refs.isEmpty()) {
            Set<Integer> refIds = refs.stream()
                    .map(NodeKnowledgeRel::getKnowledgeId).collect(Collectors.toSet());
            String names = scorecardMapper.selectBatchIds(refIds).stream()
                    .map(Scorecard::getName).collect(Collectors.joining(", "));
            throw BizException.of(ResultCode.DATA_IN_USE,
                    "The following scorecards are referenced by decision flow nodes and cannot be deleted: " + names);
        }
        changeStatus(ids, STATUS_RECYCLE);
    }

    @Override
    public void restore(List<Integer> ids) {
        changeStatus(ids, STATUS_DISABLED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void removePermanently(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // Only permanently delete scorecards visible to this organization
        ids = filterVisible(ids);
        for (Integer id : ids) {
            Scorecard sc = scorecardMapper.selectById(id);
            if (sc != null && (sc.getStatus() == null || sc.getStatus() != STATUS_RECYCLE)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Only scorecards in the recycle bin can be permanently deleted");
            }
        }
        scorecardMapper.deleteBatchIds(ids);
    }

    // ------------------------------------------------------------------ Internal methods

    private void applyFields(Scorecard sc, ScorecardSaveDTO dto) {
        sc.setName(dto.getName());
        sc.setCode(dto.getCode());
        sc.setDescription(dto.getDescription());
        sc.setVersion(dto.getVersion());
        sc.setParentId(dto.getParentId());
        if (dto.getEngineId() != null) {
            sc.setEngineId(dto.getEngineId());
        }
    }

    /** Dimension validation: bins must have min < max, and dimensions must be unique */
    private void validateDimensions(List<ScorecardDimensionDTO> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "At least one scoring dimension is required");
        }
        Set<String> fields = new HashSet<>();
        for (ScorecardDimensionDTO d : dimensions) {
            if (!fields.add(d.getField())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Duplicate dimension field: " + d.getField());
            }
            if (d.getBins() == null || d.getBins().isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Dimension " + d.getField() + " has no bins configured");
            }
            for (ScorecardDimensionDTO.ScorecardBinDTO bin : d.getBins()) {
                if (bin.getMin() == null || bin.getMax() == null) {
                    throw BizException.of(ResultCode.PARAM_INVALID,
                            "Dimension " + d.getField() + " bin bounds must not be blank");
                }
                if (bin.getMin() >= bin.getMax()) {
                    throw BizException.of(ResultCode.PARAM_INVALID,
                            "Dimension " + d.getField() + " has an invalid bin: min must be less than max");
                }
            }
        }
    }

    private String writeDimensions(List<ScorecardDimensionDTO> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(dimensions);
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Failed to serialize scoring dimensions");
        }
    }

    private List<ScorecardDimensionDTO> parseDimensions(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ScorecardDimensionDTO>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse scorecard dimensions, returning empty list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String writeMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> parseMap(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void validateDuplicate(String name, String code, Integer excludeId) {
        // Duplicate check is limited to organizations visible to the current user (organizations may reuse names; platform common does not conflict)
        List<Long> organs = TenantScope.visibleOrgans();
        if (StringUtils.isNotBlank(name)) {
            LambdaQueryWrapper<Scorecard> qw = new LambdaQueryWrapper<Scorecard>()
                    .eq(Scorecard::getName, name).ne(Scorecard::getStatus, STATUS_RECYCLE);
            if (organs != null) {
                qw.in(Scorecard::getOrganId, organs);
            }
            if (excludeId != null) {
                qw.ne(Scorecard::getId, excludeId);
            }
            if (scorecardMapper.selectCount(qw) > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Scorecard name already exists: " + name);
            }
        }
        if (StringUtils.isNotBlank(code)) {
            LambdaQueryWrapper<Scorecard> qw = new LambdaQueryWrapper<Scorecard>()
                    .eq(Scorecard::getCode, code).ne(Scorecard::getStatus, STATUS_RECYCLE);
            if (organs != null) {
                qw.in(Scorecard::getOrganId, organs);
            }
            if (excludeId != null) {
                qw.ne(Scorecard::getId, excludeId);
            }
            if (scorecardMapper.selectCount(qw) > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Scorecard code already exists: " + code);
            }
        }
    }

    private String buildCopyName(String name) {
        String base = StringUtils.defaultString(name);
        String candidate = base + "_Copy";
        List<Long> organs = TenantScope.visibleOrgans();
        int i = 1;
        while (true) {
            LambdaQueryWrapper<Scorecard> qw = new LambdaQueryWrapper<Scorecard>()
                    .eq(Scorecard::getName, candidate).ne(Scorecard::getStatus, STATUS_RECYCLE);
            if (organs != null) {
                qw.in(Scorecard::getOrganId, organs);
            }
            if (scorecardMapper.selectCount(qw) == 0) {
                break;
            }
            candidate = base + "_Copy" + (++i);
        }
        return candidate;
    }

    /**
     * Filter the id list to scorecard ids visible to the current user.
     * Operations on invisible scorecards by org users are silently skipped.
     */
    private List<Integer> filterVisible(List<Integer> ids) {
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs == null || ids == null || ids.isEmpty()) {
            return ids;
        }
        return scorecardMapper.selectBatchIds(ids).stream()
                .filter(s -> TenantScope.isVisible(s.getOrganId() == null ? null : s.getOrganId().longValue()))
                .map(Scorecard::getId)
                .collect(Collectors.toList());
    }
}
