package com.helix.console.system.controller;

import com.helix.console.common.Result;
import com.helix.console.system.dto.LoginDTO;
import com.helix.console.system.dto.LoginResultVO;
import com.helix.console.system.dto.RegisterCaptchaDTO;
import com.helix.console.system.dto.RegisterDTO;
import com.helix.console.system.security.LoginUser;
import com.helix.console.system.security.UserContext;
import com.helix.console.system.service.AuthService;
import com.helix.console.system.service.TenantRegisterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

/**
 * Authentication API.
 */
@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final TenantRegisterService tenantRegisterService;

    @Operation(summary = "Login")
    @PostMapping("/login")
    public Result<LoginResultVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.ok(authService.login(dto, resolveIp(request)));
    }

    @Operation(summary = "Tenant register - send phone captcha")
    @PostMapping("/register/captcha")
    public Result<Map<String, Object>> registerCaptcha(@Valid @RequestBody RegisterCaptchaDTO dto) {
        return Result.ok(tenantRegisterService.sendCaptcha(dto.getPhone()));
    }

    @Operation(summary = "Tenant register: provision the organization and create the default tenant administrator user")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(tenantRegisterService.register(dto));
    }

    @Operation(summary = "Current logged-in user info")
    @GetMapping("/current-user")
    public Result<LoginUser> currentUser() {
        return Result.ok(UserContext.get());
    }

    @Operation(summary = "Change password")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(UserContext.currentUserId(),
                body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }

    /**
     * Resolve the client's real IP, preferring reverse proxy headers.
     */
    private String resolveIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String h : headers) {
            String ip = request.getHeader(h);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For may contain a multi-hop proxy chain; take the first
                int comma = ip.indexOf(',');
                return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
