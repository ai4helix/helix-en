package com.helix.console.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.system.dto.TreeNodeVO;
import com.helix.console.system.entity.SysResource;
import com.helix.console.system.mapper.SysResourceMapper;
import com.helix.console.system.security.LoginUser;
import com.helix.console.system.security.UserContext;
import com.helix.console.system.service.SysResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resource (menu) management service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysResourceServiceImpl implements SysResourceService {

    private static final long ROOT_PARENT = 0L;
    private static final int STATUS_DELETED = -1;
    private static final int STATUS_ENABLED = 1;
    /** Platform operator menu code: platform admin users always see only this menu */
    private static final String PLATFORM_MENU_CODE = "menu:platform";
    /** Feedback management menu code: platform-side page, delivered to platform admin users together with the platform operator menu */
    private static final String FEEDBACK_MENU_CODE = "MENU_FEEDBACK";

    private final SysResourceMapper sysResourceMapper;

    @Override
    public List<TreeNodeVO> tree() {
        List<SysResource> all = sysResourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                .ne(SysResource::getStatus, STATUS_DELETED)
                .orderByAsc(SysResource::getParentId)
                .orderByAsc(SysResource::getId));
        return buildTree(all);
    }

    @Override
    public List<TreeNodeVO> currentUserTree() {
        LoginUser current = UserContext.get();
        // Platform admin users (user_type=1) only receive the "Platform Operator" menu:
        // the platform side does not configure engines; it only views tenant/role/batch run statistics.
        // "Feedback management" is delivered together with the platform menu --
        // platform administrators need to view/handle feedback submitted by all tenants
        // (this page is platform-side and is never granted to tenant roles).
        if (current != null && current.isAdminUser()) {
            List<SysResource> platform = sysResourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                    .in(SysResource::getCode, PLATFORM_MENU_CODE, FEEDBACK_MENU_CODE)
                    .eq(SysResource::getStatus, STATUS_ENABLED));
            return buildTree(platform);
        }
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            return new ArrayList<>();
        }
        List<SysResource> own = sysResourceMapper.selectByUserId(userId);
        if (own.isEmpty()) {
            return new ArrayList<>();
        }
        // Grants may only include child nodes; their parent chain must be completed, otherwise the frontend menu tree breaks
        Map<Long, SysResource> allById = sysResourceMapper.selectList(
                        new LambdaQueryWrapper<SysResource>().ne(SysResource::getStatus, STATUS_DELETED))
                .stream().collect(Collectors.toMap(SysResource::getId, r -> r, (a, b) -> a));
        Map<Long, SysResource> merged = new LinkedHashMap<>();
        for (SysResource r : own) {
            merged.put(r.getId(), r);
            Long pid = r.getParentId();
            int guard = 0;
            while (pid != null && pid != ROOT_PARENT && guard++ < 20) {
                SysResource parent = allById.get(pid);
                if (parent == null) {
                    break;
                }
                merged.put(parent.getId(), parent);
                pid = parent.getParentId();
            }
        }
        return buildTree(new ArrayList<>(merged.values()));
    }

    @Override
    public SysResource detail(Long resourceId) {
        SysResource res = sysResourceMapper.selectById(resourceId);
        if (res == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Resource does not exist");
        }
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysResource resource) {
        validate(resource, null);
        resource.setId(null);
        resource.setStatus(STATUS_ENABLED);
        resource.setBirth(LocalDateTime.now());
        if (resource.getParentId() == null) {
            resource.setParentId(ROOT_PARENT);
        }
        sysResourceMapper.insert(resource);
        return resource.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysResource resource) {
        if (resource.getId() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Resource id must not be blank");
        }
        SysResource exist = sysResourceMapper.selectById(resource.getId());
        if (exist == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Resource does not exist");
        }
        if (Objects.equals(resource.getId(), resource.getParentId())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Parent cannot be itself");
        }
        validate(resource, resource.getId());
        sysResourceMapper.updateById(resource);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long resourceId) {
        SysResource res = sysResourceMapper.selectById(resourceId);
        if (res == null) {
            return;
        }
        Long childCount = sysResourceMapper.selectCount(new LambdaQueryWrapper<SysResource>()
                .eq(SysResource::getParentId, resourceId)
                .ne(SysResource::getStatus, STATUS_DELETED));
        if (childCount != null && childCount > 0) {
            throw BizException.of(ResultCode.DATA_IN_USE, "This resource still has sub-resources; delete them first");
        }
        SysResource update = new SysResource();
        update.setId(resourceId);
        update.setStatus(STATUS_DELETED);
        sysResourceMapper.updateById(update);
    }


    // ------------------------------------------------------------------ Internal methods

    private void validate(SysResource resource, Long excludeId) {
        if (StringUtils.isBlank(resource.getName())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Resource name must not be blank");
        }
        if (StringUtils.isNotBlank(resource.getCode())) {
            LambdaQueryWrapper<SysResource> qw = new LambdaQueryWrapper<SysResource>()
                    .eq(SysResource::getCode, resource.getCode())
                    .ne(SysResource::getStatus, STATUS_DELETED);
            if (excludeId != null) {
                qw.ne(SysResource::getId, excludeId);
            }
            if (sysResourceMapper.selectCount(qw) > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Resource code already exists: " + resource.getCode());
            }
        }
    }

    private List<TreeNodeVO> buildTree(List<SysResource> flat) {
        Map<Long, TreeNodeVO> byId = new HashMap<>();
        for (SysResource r : flat) {
            TreeNodeVO vo = new TreeNodeVO();
            vo.setId(r.getId());
            vo.setName(r.getName());
            vo.setCode(r.getCode());
            vo.setParentId(r.getParentId());
            vo.setUrl(r.getUrl());
            vo.setIcon(r.getIcon());
            vo.setDescription(r.getDes());
            vo.setStatus(r.getStatus());
            byId.put(vo.getId(), vo);
        }
        List<TreeNodeVO> roots = new ArrayList<>();
        for (TreeNodeVO vo : byId.values()) {
            Long pid = vo.getParentId();
            if (pid == null || pid == ROOT_PARENT || !byId.containsKey(pid)) {
                roots.add(vo);
            } else {
                byId.get(pid).getChildren().add(vo);
            }
        }
        return roots;
    }
}
