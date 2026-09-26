package com.helix.console.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.system.dto.LoginDTO;
import com.helix.console.system.dto.LoginResultVO;
import com.helix.console.system.entity.SysOrganization;
import com.helix.console.system.entity.SysResource;
import com.helix.console.system.entity.SysUser;
import com.helix.console.system.mapper.SysOrganizationMapper;
import com.helix.console.system.mapper.SysResourceMapper;
import com.helix.console.system.mapper.SysUserMapper;
import com.helix.console.system.security.JwtTokenProvider;
import com.helix.console.system.security.LoginUser;
import com.helix.console.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Authentication service implementation.
 *
 * <p>Passwords are verified with BCrypt, not the original unsalted MD5.
 * Login failures always return the same "account or password is wrong"
 * message to prevent account enumeration.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Unified login failure message; does not distinguish "account missing" from "wrong password" */
    private static final String LOGIN_FAIL_MSG = "Account or password is wrong";
    /** Super administrator role code, used to decide full permissions */
    private static final String SUPER_ADMIN_ROLE = "ROLE_ADMIN";

    private final SysUserMapper sysUserMapper;
    private final SysResourceMapper sysResourceMapper;
    private final SysOrganizationMapper sysOrganizationMapper;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO login(LoginDTO dto, String clientIp) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUser::getAccount, dto.getAccount().trim());
        SysUser user = sysUserMapper.selectOne(qw);

        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("Login failed, account={}, ip={}", dto.getAccount(), clientIp);
            throw BizException.of(ResultCode.UNAUTHORIZED, LOGIN_FAIL_MSG);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.of(ResultCode.UNAUTHORIZED, "Account is disabled or deregistered");
        }

        // Record the latest login info
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLatestTime(LocalDateTime.now());
        update.setLatestIp(clientIp);
        sysUserMapper.updateById(update);

        LoginUser loginUser = loadLoginUser(user.getId());
        String token = tokenProvider.create(user.getId(), user.getAccount(), user.getOrganId());

        LoginResultVO vo = new LoginResultVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setAccount(user.getAccount());
        vo.setNickName(user.getNickName());
        vo.setOrganId(user.getOrganId());
        vo.setUserType(user.getUserType());
        if (user.getOrganId() != null) {
            SysOrganization org = sysOrganizationMapper.selectById(user.getOrganId());
            vo.setOrganName(org == null ? null : org.getName());
        }
        vo.setRoles(loginUser.getRoleCodes());
        vo.setPermissions(loginUser.getPermissions());
        return vo;
    }

    @Override
    public LoginUser loadLoginUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED, "User does not exist");
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setAccount(user.getAccount());
        loginUser.setNickName(user.getNickName());
        loginUser.setOrganId(user.getOrganId());
        loginUser.setUserType(user.getUserType());
        if (user.getOrganId() != null) {
            SysOrganization org = sysOrganizationMapper.selectById(user.getOrganId());
            loginUser.setOrganName(org == null ? null : org.getName());
        }

        List<String> roles = sysUserMapper.selectRoleCodes(userId);
        loginUser.setRoleCodes(roles == null ? new ArrayList<>() : roles);

        // Only "root organization + super administrator role" grants full permissions.
        // Deciding by organId=1 alone would let ordinary users in the root organization escalate privileges.
        boolean fullAccess = loginUser.isSuperAdmin() && loginUser.hasRole(SUPER_ADMIN_ROLE);
        if (fullAccess) {
            List<String> all = sysResourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                            .eq(SysResource::getStatus, 1))
                    .stream().map(SysResource::getCode)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            loginUser.setPermissions(all);
        } else {
            loginUser.setPermissions(sysResourceMapper.selectByUserId(userId).stream()
                    .map(SysResource::getCode)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList()));
        }
        return loginUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "User does not exist");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Old password is wrong");
        }
        validatePasswordStrength(newPassword);
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "User does not exist");
        }
        validatePasswordStrength(newPassword);
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(update);
    }

    /** Password strength: at least 8 characters with both letters and digits */
    private void validatePasswordStrength(String password) {
        if (StringUtils.isBlank(password) || password.length() < 8) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Password must be at least 8 characters");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Password must contain both letters and digits");
        }
    }
}
