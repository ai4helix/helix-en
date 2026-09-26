package com.helix.console.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.console.batch.mapper.IndicatorBatchMapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.platform.dto.EngineStatVO;
import com.helix.console.platform.dto.RoleStatVO;
import com.helix.console.platform.dto.TenantStatVO;
import com.helix.console.platform.service.PlatformService;
import com.helix.console.result.mapper.DecisionLogMapper;
import com.helix.console.system.entity.SysOrganization;
import com.helix.console.system.entity.SysRole;
import com.helix.console.system.mapper.SysOrganizationMapper;
import com.helix.console.system.mapper.SysRoleMapper;
import com.helix.console.system.mapper.SysUserMapper;
import com.helix.console.system.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Platform operator query service implementation.
 *
 * <p>Tenants/roles live in the sys DB; decision logs and batch tasks live in the
 * engine DB: each DB is aggregated separately and assembled in memory, no
 * cross-DB JOIN (see db/CONVENTIONS.md).</p>
 */
@Service
@RequiredArgsConstructor
public class PlatformServiceImpl implements PlatformService {

    private static final String PLATFORM_ORG_NAME = "Platform Common";

    private final SysOrganizationMapper organizationMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final DecisionLogMapper decisionLogMapper;
    private final IndicatorBatchMapper indicatorBatchMapper;

    @Override
    public Map<String, Object> overview() {
        requirePlatformAdmin();
        Map<String, Object> decisionTotalRow = decisionLogMapper.statsTotal();
        List<Map<String, Object>> userGroups = userMapper.countGroupByOrgan();
        long userCount = userGroups.stream()
                .mapToLong(m -> ((Number) m.getOrDefault("cnt", 0)).longValue()).sum();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenantCount", organizationMapper.selectCount(
                new LambdaQueryWrapper<SysOrganization>().eq(SysOrganization::getStatus, 1)));
        out.put("userCount", userCount);
        out.put("decisionTotal", number(decisionTotalRow, "total"));
        out.put("decisionToday", number(decisionTotalRow, "todayTotal"));
        List<Map<String, Object>> batchGroups = indicatorBatchMapper.statsByOrgan();
        out.put("batchCount", batchGroups.stream()
                .mapToLong(m -> ((Number) m.getOrDefault("batches", 0)).longValue()).sum());
        out.put("batchRows", batchGroups.stream()
                .mapToLong(m -> ((Number) m.getOrDefault("rowsTotal", 0)).longValue()).sum());
        return out;
    }

    @Override
    public List<TenantStatVO> tenantStats() {
        requirePlatformAdmin();
        List<SysOrganization> organs = organizationMapper.selectList(
                new LambdaQueryWrapper<SysOrganization>().orderByAsc(SysOrganization::getId));
        Map<Long, Long> userCounts = toLongMap(userMapper.countGroupByOrgan(), "organId", "cnt");
        Map<Long, Long> roleCounts = toLongMap(roleMapper.countGroupByOrgan(), "organId", "cnt");
        Map<Long, Map<String, Object>> decision = byKey(decisionLogMapper.statsByOrgan(), "organId");
        Map<Long, Object> today = toObjectMap(decisionLogMapper.statsTodayByOrgan(), "organId", "total");
        Map<Long, Map<String, Object>> batch = byKey(indicatorBatchMapper.statsByOrgan(), "organId");

        List<TenantStatVO> out = new ArrayList<>();
        for (SysOrganization org : organs) {
            TenantStatVO vo = new TenantStatVO();
            vo.setOrganId(org.getId());
            vo.setName(org.getName());
            vo.setCode(org.getCode());
            vo.setUserCount(userCounts.getOrDefault(org.getId(), 0L));
            vo.setRoleCount(roleCounts.getOrDefault(org.getId(), 0L));
            fillDecision(vo, decision.get(org.getId()), today.get(org.getId()));
            fillBatch(vo, batch.get(org.getId()));
            out.add(vo);
        }
        return out;
    }

    @Override
    public List<RoleStatVO> roleStats() {
        requirePlatformAdmin();
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .ne(SysRole::getStatus, -1)
                .orderByAsc(SysRole::getOrganId)
                .orderByAsc(SysRole::getId));
        Map<Long, String> organNames = organizationMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysOrganization::getId, SysOrganization::getName, (a, b) -> a));
        Map<Long, Long> userCounts = toLongMap(roleMapper.countUsersByRoleIds(), "roleId", "cnt");
        List<RoleStatVO> out = new ArrayList<>();
        for (SysRole role : roles) {
            RoleStatVO vo = new RoleStatVO();
            vo.setRoleId(role.getId());
            vo.setRoleName(role.getRoleName());
            vo.setRoleCode(role.getRoleCode());
            vo.setRoleDesc(role.getRoleDesc());
            vo.setOrganId(role.getOrganId());
            vo.setOrganName(role.getOrganId() == null || role.getOrganId() == 0
                    ? PLATFORM_ORG_NAME : organNames.getOrDefault(role.getOrganId(), String.valueOf(role.getOrganId())));
            vo.setUserCount(userCounts.getOrDefault(role.getId(), 0L));
            vo.setStatus(role.getStatus());
            out.add(vo);
        }
        return out;
    }

    @Override
    public List<EngineStatVO> engineStats() {
        requirePlatformAdmin();
        Map<Long, Map<String, Object>> decision = byKey(decisionLogMapper.statsByOrgan(), "organId");
        Map<Long, Object> today = toObjectMap(decisionLogMapper.statsTodayByOrgan(), "organId", "total");
        Map<Long, Map<String, Object>> batch = byKey(indicatorBatchMapper.statsByOrgan(), "organId");
        Map<Long, String> organNames = organizationMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysOrganization::getId, SysOrganization::getName, (a, b) -> a));

        // Full set of organizations that appeared in decisions and batch runs (TreeMap sorted by organId ascending)
        Map<Long, EngineStatVO> merged = new TreeMap<>();
        for (Long organId : decision.keySet()) {
            merged.put(organId, new EngineStatVO());
        }
        batch.keySet().forEach(k -> merged.putIfAbsent(k, new EngineStatVO()));
        List<EngineStatVO> out = new ArrayList<>();
        for (Map.Entry<Long, EngineStatVO> entry : merged.entrySet()) {
            Long organId = entry.getKey();
            EngineStatVO vo = entry.getValue();
            vo.setOrganId(organId);
            vo.setOrganName(organId == null || organId == 0
                    ? PLATFORM_ORG_NAME : organNames.getOrDefault(organId, "Deregistered organization #" + organId));
            fillDecision(vo, decision.get(organId), today.get(organId));
            fillBatch(vo, batch.get(organId));
            out.add(vo);
        }
        return out;
    }


    // ------------------------------------------------------------------ Internal methods

    /** Platform operator APIs are for platform admin users only (tenant users cannot access them even with roles) */
    private void requirePlatformAdmin() {
        if (!TenantScope.isAdmin()) {
            throw BizException.of(ResultCode.FORBIDDEN, "Only platform administrators can access operations data");
        }
    }

    private void fillDecision(TenantStatVO vo, Map<String, Object> row, Object todayTotal) {
        long total = number(row, "total");
        vo.setDecisionTotal(total);
        vo.setDecisionToday(todayTotal == null ? 0L : ((Number) todayTotal).longValue());
        vo.setPassCount(number(row, "passCnt"));
        vo.setRejectCount(number(row, "rejectCnt"));
        vo.setManualCount(number(row, "manualCnt"));
        vo.setPassRate(rate(vo.getPassCount(), total));
        vo.setLastDecisionTime(date(row, "lastTime"));
    }

    private void fillDecision(EngineStatVO vo, Map<String, Object> row, Object todayTotal) {
        long total = number(row, "total");
        vo.setDecisionTotal(total);
        vo.setDecisionToday(todayTotal == null ? 0L : ((Number) todayTotal).longValue());
        vo.setPassCount(number(row, "passCnt"));
        vo.setRejectCount(number(row, "rejectCnt"));
        vo.setManualCount(number(row, "manualCnt"));
        vo.setPassRate(rate(vo.getPassCount(), total));
        vo.setLastDecisionTime(date(row, "lastTime"));
    }

    private void fillBatch(TenantStatVO vo, Map<String, Object> row) {
        vo.setBatchCount(number(row, "batches"));
        vo.setBatchRows(number(row, "rowsTotal"));
        vo.setBatchRowsOk(number(row, "rowsOk"));
    }

    private void fillBatch(EngineStatVO vo, Map<String, Object> row) {
        vo.setBatchCount(number(row, "batches"));
        vo.setBatchRows(number(row, "rowsTotal"));
        vo.setBatchRowsOk(number(row, "rowsOk"));
    }

    private double rate(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(part * 1000.0 / total) / 10.0;
    }

    private long number(Map<String, Object> row, String key) {
        if (row == null) {
            return 0L;
        }
        Object v = row.get(key);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    private java.util.Date date(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object v = row.get(key);
        if (v instanceof java.util.Date) {
            return (java.util.Date) v;
        }
        if (v instanceof java.time.LocalDateTime) {
            return java.sql.Timestamp.valueOf((java.time.LocalDateTime) v);
        }
        return null;
    }

    private Map<Long, Long> toLongMap(List<Map<String, Object>> rows, String keyCol, String valCol) {
        Map<Long, Long> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object k = row.get(keyCol);
            Object v = row.get(valCol);
            if (k instanceof Number) {
                out.put(((Number) k).longValue(), v instanceof Number ? ((Number) v).longValue() : 0L);
            }
        }
        return out;
    }

    private Map<Long, Object> toObjectMap(List<Map<String, Object>> rows, String keyCol, String valCol) {
        Map<Long, Object> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object k = row.get(keyCol);
            if (k instanceof Number) {
                out.put(((Number) k).longValue(), row.get(valCol));
            }
        }
        return out;
    }

    private Map<Long, Map<String, Object>> byKey(List<Map<String, Object>> rows, String keyCol) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object k = row.get(keyCol);
            if (k instanceof Number) {
                out.put(((Number) k).longValue(), row);
            }
        }
        return out;
    }
}
