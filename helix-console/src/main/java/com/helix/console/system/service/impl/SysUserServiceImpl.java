package com.helix.console.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.system.dto.UserSaveDTO;
import com.helix.console.system.dto.UserVO;
import com.helix.console.system.entity.SysOrganization;
import com.helix.console.system.entity.SysRole;
import com.helix.console.system.entity.SysUser;
import com.helix.console.system.mapper.SysOrganizationMapper;
import com.helix.console.system.mapper.SysRelationMapper;
import com.helix.console.system.mapper.SysRoleMapper;
import com.helix.console.system.mapper.SysUserMapper;
import com.helix.console.system.security.TenantScope;
import com.helix.console.system.security.UserContext;
import com.helix.console.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User management service implementation.
 *
 * <p>Differences from the original:
 * <ul>
 *   <li>passwords use BCrypt instead of unsalted MD5;</li>
 *   <li>accounts are no longer concatenated with the organization code
 *       (the original {@code account + "_" + code} broke logins after renaming);</li>
 *   <li>role binding uses delete-then-insert for idempotency (the original
 *       updated relation rows directly).</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private static final int STATUS_DELETED = -1;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    /** Default initial password for newly created users */
    private static final String DEFAULT_PASSWORD = "Init@1234";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysOrganizationMapper sysOrganizationMapper;
    private final SysRelationMapper sysRelationMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> page(String keyword, Long organId, Integer status, long pageNo, long pageSize) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.ne(SysUser::getStatus, STATUS_DELETED);
        if (organId != null) {
            qw.eq(SysUser::getOrganId, organId);
        }
        // Org users only see their own organization's accounts (admin users are unfiltered)
        if (!TenantScope.isAdmin()) {
            qw.eq(SysUser::getOrganId, UserContext.currentOrganId());
        }
        if (status != null) {
            qw.eq(SysUser::getStatus, status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like(SysUser::getAccount, keyword)
                    .or().like(SysUser::getNickName, keyword)
                    .or().like(SysUser::getEmployeeId, keyword));
        }
        qw.orderByDesc(SysUser::getId);

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<UserVO> vos = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        fillRelations(vos);
        return PageResult.of(vos, page.getTotal(), pageNo, pageSize);
    }

    @Override
    public UserVO detail(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "User does not exist");
        }
        // Viewing users of other organizations is forbidden
        TenantScope.checkVisible(user.getOrganId());
        UserVO vo = toVO(user);
        fillRelations(Collections.singletonList(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSaveDTO dto) {
        validateDuplicate(dto.getAccount(), dto.getEmployeeId(), null);
        // Org users cannot create admin users, nor create accounts across organizations
        if (!TenantScope.isAdmin()) {
            if (dto.getUserType() != null && dto.getUserType() == 1) {
                throw BizException.of(ResultCode.FORBIDDEN, "Not allowed to create platform admin users");
            }
            if (dto.getOrganId() != null && !dto.getOrganId().equals(UserContext.currentOrganId())) {
                throw BizException.of(ResultCode.FORBIDDEN, "Can only create users for the current organization");
            }
        }

        SysUser user = new SysUser();
        applyFields(user, dto);
        String rawPassword = StringUtils.isNotBlank(dto.getPassword())
                ? dto.getPassword() : DEFAULT_PASSWORD;
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(STATUS_ENABLED);
        user.setBirth(LocalDateTime.now());
        user.setAuthor(UserContext.currentAccount());
        sysUserMapper.insert(user);

        bindRoles(user.getId(), dto.getRoleIds());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserSaveDTO dto) {
        if (dto.getUserId() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "User id must not be blank");
        }
        SysUser exist = sysUserMapper.selectById(dto.getUserId());
        if (exist == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "User does not exist");
        }
        // Modifying users of other organizations is forbidden
        TenantScope.checkVisible(exist.getOrganId());
        validateDuplicate(dto.getAccount(), dto.getEmployeeId(), dto.getUserId());

        SysUser user = new SysUser();
        user.setId(dto.getUserId());
        applyFields(user, dto);
        // Blank password means no change
        if (StringUtils.isNotBlank(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        sysUserMapper.updateById(user);

        if (dto.getRoleIds() != null) {
            bindRoles(dto.getUserId(), dto.getRoleIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        // Cannot delete yourself, avoiding locking yourself out of the system
        Long current = UserContext.currentUserId();
        if (current != null && userIds.contains(current)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Cannot delete the currently logged-in user");
        }
        // Org users can only delete accounts of their own organization
        checkAllVisible(userIds);
        userIds.forEach(id -> {
            SysUser user = new SysUser();
            user.setId(id);
            user.setStatus(STATUS_DELETED);
            sysUserMapper.updateById(user);
            sysRelationMapper.deleteUserRoles(id);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(List<Long> userIds, Integer status) {
        if (userIds == null || userIds.isEmpty() || status == null) {
            return;
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Invalid status value");
        }
        Long current = UserContext.currentUserId();
        if (current != null && userIds.contains(current) && status == STATUS_DISABLED) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Cannot disable the currently logged-in user");
        }
        // Org users can only operate on their own organization's accounts
        checkAllVisible(userIds);
        userIds.forEach(id -> {
            SysUser user = new SysUser();
            user.setId(id);
            user.setStatus(status);
            sysUserMapper.updateById(user);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            return;
        }
        sysRelationMapper.deleteUserRoles(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        Long organId = user == null ? null : user.getOrganId();
        // v5 tenant register: org users can only assign roles visible to their organization (or platform common), preventing cross-tenant role binding
        checkRolesVisible(roleIds);
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            if (roleId != null) {
                sysRelationMapper.insertUserRole(userId, roleId, organId);
            }
        }
    }

    /** v5 tenant register: verify all roles to bind belong to organizations visible to the current user */
    private void checkRolesVisible(List<Long> roleIds) {
        if (TenantScope.isAdmin() || roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            if (roleId == null) {
                continue;
            }
            SysRole role = sysRoleMapper.selectById(roleId);
            if (role == null) {
                throw BizException.of(ResultCode.NOT_FOUND, "Role does not exist: " + roleId);
            }
            TenantScope.checkVisible(role.getOrganId());
        }
    }


    private void applyFields(SysUser user, UserSaveDTO dto) {
        user.setAccount(dto.getAccount().trim());
        user.setNickName(dto.getNickName());
        user.setEmployeeId(dto.getEmployeeId());
        user.setEmail(dto.getEmail());
        user.setCellphone(dto.getCellphone());
        user.setQq(dto.getQq());
        if (dto.getOrganId() != null) {
            user.setOrganId(dto.getOrganId());
        } else if (user.getOrganId() == null) {
            // Defaults to the operator's organization
            user.setOrganId(UserContext.currentOrganId());
        }
        // User type defaults by organization (platform = admin user, others = SaaS org user)
        if (dto.getUserType() != null) {
            user.setUserType(dto.getUserType());
        } else if (user.getUserType() == null) {
            user.setUserType(Long.valueOf(1L).equals(user.getOrganId()) ? 1 : 2);
        }
    }

    /** Verify all target users belong to organizations visible to the current user */
    private void checkAllVisible(List<Long> userIds) {
        if (TenantScope.isAdmin() || userIds == null || userIds.isEmpty()) {
            return;
        }
        for (SysUser u : sysUserMapper.selectBatchIds(userIds)) {
            TenantScope.checkVisible(u.getOrganId());
        }
    }

    private void validateDuplicate(String account, String employeeId, Long excludeId) {
        if (StringUtils.isNotBlank(account)) {
            LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getAccount, account.trim())
                    .ne(SysUser::getStatus, STATUS_DELETED);
            if (excludeId != null) {
                qw.ne(SysUser::getId, excludeId);
            }
            if (sysUserMapper.selectCount(qw) > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Account already exists: " + account);
            }
        }
        if (StringUtils.isNotBlank(employeeId)) {
            LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmployeeId, employeeId)
                    .ne(SysUser::getStatus, STATUS_DELETED);
            if (excludeId != null) {
                qw.ne(SysUser::getId, excludeId);
            }
            if (sysUserMapper.selectCount(qw) > 0) {
                throw BizException.of(ResultCode.DATA_DUPLICATE, "Employee ID already exists: " + employeeId);
            }
        }
    }

    /** Batch fill organization and role names, avoiding N+1 queries on the list page */
    private void fillRelations(List<UserVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Set<Long> organIds = vos.stream().map(UserVO::getOrganId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> organNames = organIds.isEmpty() ? Collections.emptyMap()
                : sysOrganizationMapper.selectBatchIds(organIds).stream()
                .collect(Collectors.toMap(SysOrganization::getId, SysOrganization::getName, (a, b) -> a));

        Set<Long> roleIds = vos.stream().flatMap(v -> v.getRoleIds().stream())
                .collect(Collectors.toSet());
        Map<Long, String> roleNames = roleIds.isEmpty() ? Collections.emptyMap()
                : sysRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName, (a, b) -> a));

        vos.forEach(v -> {
            v.setOrganName(organNames.get(v.getOrganId()));
            v.setRoleNames(v.getRoleIds().stream()
                    .map(roleNames::get).filter(Objects::nonNull).collect(Collectors.toList()));
        });
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setUserId(user.getId());
        vo.setOrganId(user.getOrganId());
        vo.setUserType(user.getUserType());
        vo.setEmployeeId(user.getEmployeeId());
        vo.setAccount(user.getAccount());
        vo.setNickName(user.getNickName());
        vo.setEmail(user.getEmail());
        vo.setCellphone(user.getCellphone());
        vo.setQq(user.getQq());
        vo.setStatus(user.getStatus());
        vo.setLatestTime(user.getLatestTime());
        vo.setLatestIp(user.getLatestIp());
        vo.setBirth(user.getBirth());
        vo.setAuthor(user.getAuthor());
        vo.setRoleIds(sysUserMapper.selectRoleIds(user.getId()));
        return vo;
    }
}
