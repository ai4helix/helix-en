package com.helix.console.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.system.entity.SysRole;
import com.helix.console.system.entity.SysUser;
import com.helix.console.system.mapper.SysRelationMapper;
import com.helix.console.system.mapper.SysRoleMapper;
import com.helix.console.system.mapper.SysUserMapper;
import com.helix.console.system.security.TenantScope;
import com.helix.console.system.security.UserContext;
import com.helix.console.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Role management service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private static final int STATUS_DELETED = -1;
    private static final int STATUS_ENABLED = 1;

    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRelationMapper sysRelationMapper;

    @Override
    public PageResult<SysRole> page(String keyword, Long organId, long pageNo, long pageSize) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        qw.ne(SysRole::getStatus, STATUS_DELETED);
        // v5 tenant register: org users only see their own organization's roles (admin users can filter by the given organId)
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(SysRole::getOrganId, organs);
        } else if (organId != null) {
            qw.eq(SysRole::getOrganId, organId);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like(SysRole::getRoleName, keyword).or().like(SysRole::getRoleCode, keyword));
        }
        qw.orderByAsc(SysRole::getId);
        Page<SysRole> page = sysRoleMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public List<SysRole> listAll() {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, STATUS_ENABLED);
        // v5 tenant register: the role dropdown (assigning roles to new users) only offers roles visible to the organization
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(SysRole::getOrganId, organs);
        }
        qw.orderByAsc(SysRole::getId);
        return sysRoleMapper.selectList(qw);
    }

    @Override
    public Map<String, Object> detail(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Role does not exist");
        }
        // v5 tenant register: viewing roles of other organizations is forbidden
        TenantScope.checkVisible(role.getOrganId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", role);
        result.put("resourceIds", sysRoleMapper.selectResourceIds(roleId));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysRole role, List<Long> resourceIds) {
        // Role model: tenant roles are configured centrally by platform administrators; tenant users cannot create roles
        requirePlatformAdmin();
        validateDuplicate(role.getRoleName(), role.getRoleCode(), null);
        role.setId(null);
        role.setStatus(STATUS_ENABLED);
        role.setBirth(LocalDateTime.now());
        role.setAuthor(UserContext.currentAccount());
        if (role.getOrganId() == null) {
            role.setOrganId(UserContext.currentOrganId());
        }
        // v5 tenant register: roles created by org users are forced to their own organization
        role.setOrganId(TenantScope.writeOrgan(role.getOrganId()));
        sysRoleMapper.insert(role);
        bindResources(role.getId(), resourceIds);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysRole role, List<Long> resourceIds) {
        if (role.getId() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Role id must not be blank");
        }
        // Role model: only platform administrators can maintain roles
        requirePlatformAdmin();
        SysRole exist = sysRoleMapper.selectById(role.getId());
        if (exist == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Role does not exist");
        }
        // v5 tenant register: modifying roles of other organizations is forbidden
        TenantScope.checkVisible(exist.getOrganId());
        validateDuplicate(role.getRoleName(), role.getRoleCode(), role.getId());
        sysRoleMapper.updateById(role);
        if (resourceIds != null) {
            bindResources(role.getId(), resourceIds);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        // Role model: only platform administrators can maintain roles
        requirePlatformAdmin();
        // Only roles visible to the organization can be deleted
        checkAllVisible(roleIds);
        // Roles still referenced by users cannot be deleted, avoiding dangling permissions.
        // One aggregate query fetches bound user counts per role, avoiding N+1 per-role user queries.
        Map<Long, Integer> boundCounts = sysRelationMapper.countUsersByRoleIds(roleIds);
        for (Long roleId : roleIds) {
            Integer count = boundCounts.get(roleId);
            if (count != null && count > 0) {
                SysRole role = sysRoleMapper.selectById(roleId);
                throw BizException.of(ResultCode.DATA_IN_USE,
                        "Role \"" + (role == null ? roleId : role.getRoleName())
                                + "\" is assigned to " + count + " users; unbind them first");
            }
        }
        roleIds.forEach(id -> {
            SysRole role = new SysRole();
            role.setId(id);
            role.setStatus(STATUS_DELETED);
            sysRoleMapper.updateById(role);
            sysRelationMapper.deleteRoleResources(id);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(List<Long> roleIds, Integer status) {
        if (roleIds == null || roleIds.isEmpty() || status == null) {
            return;
        }
        // Role model: only platform administrators can maintain roles
        requirePlatformAdmin();
        // Only roles visible to the organization can be operated on
        checkAllVisible(roleIds);
        roleIds.forEach(id -> {
            SysRole role = new SysRole();
            role.setId(id);
            role.setStatus(status);
            sysRoleMapper.updateById(role);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindResources(Long roleId, List<Long> resourceIds) {
        if (roleId == null) {
            return;
        }
        sysRelationMapper.deleteRoleResources(roleId);
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        for (Long resId : new LinkedHashSet<>(resourceIds)) {
            if (resId != null) {
                sysRelationMapper.insertRoleResource(roleId, resId);
            }
        }
    }

    /** Role model: role maintenance is limited to platform admin users (tenant users can only use/assign roles) */
    private void requirePlatformAdmin() {
        if (!TenantScope.isAdmin()) {
            throw BizException.of(ResultCode.FORBIDDEN, "Roles are configured centrally by platform administrators; tenant users cannot maintain roles");
        }
    }

    /** v5 tenant register: verify all target roles belong to organizations visible to the current user */
    private void checkAllVisible(List<Long> roleIds) {
        if (TenantScope.isAdmin() || roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysRole role = sysRoleMapper.selectById(roleId);
            if (role == null) {
                continue;
            }
            TenantScope.checkVisible(role.getOrganId());
        }
    }

    private void validateDuplicate(String name, String code, Long excludeId) {
        // v5 tenant register: duplicate check is limited to organizations visible to the current user (organizations may reuse role names; platform common does not conflict)
        List<Long> organs = TenantScope.visibleOrgans();
        if (StringUtils.isNotBlank(name)) {
            LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleName, name).ne(SysRole::getStatus, STATUS_DELETED);
            if (organs != null) {
                qw.in(SysRole::getOrganId, organs);
            }
            if (excludeId != null) {
                qw.ne(SysRole::getId, excludeId);
            }
            if (sysRoleMapper.selectCount(qw) > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Role name already exists: " + name);
            }
        }
        if (StringUtils.isNotBlank(code)) {
            LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, code).ne(SysRole::getStatus, STATUS_DELETED);
            if (organs != null) {
                qw.in(SysRole::getOrganId, organs);
            }
            if (excludeId != null) {
                qw.ne(SysRole::getId, excludeId);
            }
            if (sysRoleMapper.selectCount(qw) > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Role code already exists: " + code);
            }
        }
    }
}
