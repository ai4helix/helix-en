package com.helix.console.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.system.dto.RegisterDTO;
import com.helix.console.system.entity.SysOrganization;
import com.helix.console.system.entity.SysRole;
import com.helix.console.system.entity.SysUser;
import com.helix.console.system.mapper.SysOrganizationMapper;
import com.helix.console.system.mapper.SysRelationMapper;
import com.helix.console.system.mapper.SysRoleMapper;
import com.helix.console.system.mapper.SysUserMapper;
import com.helix.console.system.security.TenantScope;
import com.helix.console.system.service.TenantRegisterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tenant phone registration service implementation.
 *
 * <p>Captchas are staged in Redis (valid 5 minutes, 60-second send cooldown);
 * the SMS gateway is not integrated yet, so captchas are emitted to logs. Before
 * a real gateway is wired up in production, {@code helix.register.sms.mock-code}
 * can be configured as a fixed code for integration testing, and
 * {@code helix.register.sms.debug-return=true} echoes the captcha in the response.</p>
 *
 * <p>The registration transaction does two things: create the organization
 * (tenant) -> create the tenant administrator user (account = phone number,
 * user_type=2) and bind the tenant administrator role <b>pre-configured by
 * platform administrators</b>. Roles are not auto-created on registration: the
 * platform side has only administrators, and tenant roles are uniformly
 * configured by platform administrators as "platform common (organ_id=0)"
 * roles (default {@code ROLE_TENANT_ADMIN}); tenant administrators can only
 * add users for their own organization and have no role management rights.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRegisterServiceImpl implements TenantRegisterService {

    private static final String CAPTCHA_KEY = "console:register:captcha:";
    private static final String COOLDOWN_KEY = "console:register:captcha:cooldown:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    private final SysOrganizationMapper organizationMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysRelationMapper relationMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    /** Fixed captcha (for integration testing; no random generation when non-empty) */
    @Value("${helix.register.sms.mock-code:}")
    private String mockCode;

    /** Debug switch: echo the captcha in the response (development environments only) */
    @Value("${helix.register.sms.debug-return:false}")
    private boolean debugReturn;

    /** Role code bound by default on tenant registration (must be a platform common role pre-configured by platform administrators) */
    @Value("${helix.register.tenant-admin-role-code:ROLE_TENANT_ADMIN}")
    private String tenantAdminRoleCode;

    @Override
    public Map<String, Object> sendCaptcha(String phone) {
        String normalized = phone.trim();
        if (!normalized.matches("^1[3-9]\\d{9}$")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Invalid phone number format");
        }
        ensurePhoneAvailable(normalized);

        // 60-second send cooldown
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_KEY + normalized, "1", COOLDOWN_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Captchas are sent too frequently; try again in 1 minute");
        }

        String code = StringUtils.isNotBlank(mockCode)
                ? mockCode
                : String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(CAPTCHA_KEY + normalized, code, CAPTCHA_TTL);
        // SMS gateway not integrated: log instead of really sending
        log.info("[Tenant register] phone {} registration captcha: {} (valid for 5 minutes)", normalized, code);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sent", true);
        out.put("cooldown", COOLDOWN_TTL.getSeconds());
        if (debugReturn) {
            out.put("code", code);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(RegisterDTO dto) {
        String phone = dto.getPhone().trim();
        String code = dto.getCode().trim();
        validatePasswordStrength(dto.getPassword());

        // Validate and consume the captcha (one-time)
        String cached = stringRedisTemplate.opsForValue().get(CAPTCHA_KEY + phone);
        if (cached == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Captcha has expired; please request a new one");
        }
        if (!cached.equals(code)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Incorrect captcha");
        }
        stringRedisTemplate.delete(CAPTCHA_KEY + phone);

        // Idempotency guard: reject when the phone number is already registered
        ensurePhoneAvailable(phone);

        LocalDateTime now = LocalDateTime.now();
        // 1. Provision the organization (tenant)
        SysOrganization org = new SysOrganization();
        org.setName(dto.getOrgName().trim());
        org.setCode("T" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000));
        org.setToken(UUID.randomUUID().toString());
        org.setTelephone(phone);
        org.setStatus(1);
        org.setAuthor("register");
        org.setBirth(now);
        organizationMapper.insert(org);

        // 2. Bind the tenant administrator role pre-configured by platform administrators (registration does not auto-create roles)
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, tenantAdminRoleCode)
                .eq(SysRole::getOrganId, TenantScope.PLATFORM)
                .eq(SysRole::getStatus, 1)
                .last("limit 1"));
        if (role == null) {
            throw BizException.of(ResultCode.PARAM_INVALID,
                    "The platform has not configured the tenant administrator role (" + tenantAdminRoleCode
                            + "); ask a platform administrator to configure it before registering");
        }

        // 3. Create the tenant administrator user (account = phone number, user_type=2)
        SysUser user = new SysUser();
        user.setAccount(phone);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickName(StringUtils.defaultIfBlank(dto.getNickName(), dto.getOrgName().trim() + " Administrator"));
        user.setCellphone(phone);
        user.setOrganId(org.getId());
        user.setUserType(2);
        user.setStatus(1);
        user.setAuthor("register");
        user.setBirth(now);
        userMapper.insert(user);
        relationMapper.insertUserRole(user.getId(), role.getId(), org.getId());

        log.info("[Tenant register] organization {}({}) provisioned, tenant administrator account {}(userId={}), role {}({})",
                org.getName(), org.getId(), phone, user.getId(), role.getRoleName(), role.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", user.getId());
        out.put("organId", org.getId());
        out.put("organName", org.getName());
        out.put("account", phone);
        out.put("roleCode", role.getRoleCode());
        out.put("roleName", role.getRoleName());
        return out;
    }

    /** The phone number is not occupied by an enabled user (any conflict on account or cellphone counts as registered) */
    private void ensurePhoneAvailable(String phone) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<SysUser>()
                .ne(SysUser::getStatus, -1)
                .and(w -> w.eq(SysUser::getAccount, phone).or().eq(SysUser::getCellphone, phone));
        if (userMapper.selectCount(qw) > 0) {
            throw BizException.of(ResultCode.DATA_DUPLICATE, "This phone number is already registered");
        }
    }

    /** Password strength: at least 8 characters with both letters and digits (consistent with change-password on the login side) */
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
