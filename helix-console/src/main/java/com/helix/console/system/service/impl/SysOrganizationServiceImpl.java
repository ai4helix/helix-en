package com.helix.console.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.system.entity.SysOrganization;
import com.helix.console.system.entity.SysUser;
import com.helix.console.system.mapper.SysOrganizationMapper;
import com.helix.console.system.mapper.SysUserMapper;
import com.helix.console.system.security.UserContext;
import com.helix.console.system.service.SysOrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Organization management service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOrganizationServiceImpl implements SysOrganizationService {

    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    private final SysOrganizationMapper sysOrganizationMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public List<SysOrganization> listAll() {
        return sysOrganizationMapper.selectList(new LambdaQueryWrapper<SysOrganization>()
                .orderByAsc(SysOrganization::getId));
    }

    @Override
    public SysOrganization detail(Long organId) {
        SysOrganization org = sysOrganizationMapper.selectById(organId);
        if (org == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Organization does not exist");
        }
        return org;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysOrganization org) {
        if (StringUtils.isBlank(org.getName()) || StringUtils.isBlank(org.getCode())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Organization name and code must not be blank");
        }
        Long exists = sysOrganizationMapper.selectCount(new LambdaQueryWrapper<SysOrganization>()
                .eq(SysOrganization::getCode, org.getCode()));
        if (exists != null && exists > 0) {
            throw BizException.of(ResultCode.DATA_DUPLICATE, "Organization code already exists: " + org.getCode());
        }
        org.setId(null);
        org.setStatus(STATUS_ENABLED);
        org.setBirth(LocalDateTime.now());
        org.setAuthor(UserContext.currentAccount());
        // Organization-level unique identifier for external system integration
        org.setToken(UUID.randomUUID().toString().replace("-", ""));
        sysOrganizationMapper.insert(org);
        return org.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysOrganization org) {
        if (org.getId() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Organization id must not be blank");
        }
        SysOrganization exist = sysOrganizationMapper.selectById(org.getId());
        if (exist == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Organization does not exist");
        }
        if (StringUtils.isNotBlank(org.getCode())) {
            Long dup = sysOrganizationMapper.selectCount(new LambdaQueryWrapper<SysOrganization>()
                    .eq(SysOrganization::getCode, org.getCode())
                    .ne(SysOrganization::getId, org.getId()));
            if (dup != null && dup > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Organization code already exists: " + org.getCode());
            }
        }
        sysOrganizationMapper.updateById(org);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long organId) {
        SysOrganization org = sysOrganizationMapper.selectById(organId);
        if (org == null) {
            return;
        }
        if (organId != null && organId == 1L) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Root organization cannot be deleted");
        }
        // Cannot delete while the organization still has users
        Long userCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getOrganId, organId)
                .ne(SysUser::getStatus, -1));
        if (userCount != null && userCount > 0) {
            throw BizException.of(ResultCode.DATA_IN_USE,
                    "This organization still has " + userCount + " users; transfer or delete them first");
        }
        sysOrganizationMapper.deleteById(organId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long organId, Integer status) {
        if (organId == null || status == null) {
            return;
        }
        if (organId == 1L && status == STATUS_DISABLED) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Root organization cannot be disabled");
        }
        SysOrganization org = new SysOrganization();
        org.setId(organId);
        org.setStatus(status);
        sysOrganizationMapper.updateById(org);
    }
}
