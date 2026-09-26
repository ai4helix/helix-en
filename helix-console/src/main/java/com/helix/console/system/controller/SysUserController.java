package com.helix.console.system.controller;

import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import com.helix.console.system.dto.UserSaveDTO;
import com.helix.console.system.dto.UserVO;
import com.helix.console.system.service.AuthService;
import com.helix.console.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * User management API. Replaces the original JSP {@code system/user/*}.
 */
@Tag(name = "User Management")
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
@Validated
public class SysUserController {

    private final SysUserService sysUserService;
    private final AuthService authService;

    @Operation(summary = "User page list")
    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long organId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(sysUserService.page(keyword, organId, status, pageNo, pageSize));
    }

    @Operation(summary = "User detail")
    @GetMapping("/{userId}")
    public Result<UserVO> detail(@PathVariable Long userId) {
        return Result.ok(sysUserService.detail(userId));
    }

    @Operation(summary = "Create user")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserSaveDTO dto) {
        return Result.ok(sysUserService.create(dto));
    }

    @Operation(summary = "Update user")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody UserSaveDTO dto) {
        sysUserService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "Batch delete users")
    @DeleteMapping
    public Result<Void> remove(@RequestBody List<Long> userIds) {
        sysUserService.remove(userIds);
        return Result.ok();
    }

    @Operation(summary = "Enable/Disable")
    @PostMapping("/status")
    public Result<Void> changeStatus(@RequestParam List<Long> userIds, @RequestParam Integer status) {
        sysUserService.changeStatus(userIds, status);
        return Result.ok();
    }

    @Operation(summary = "Bind roles")
    @PostMapping("/{userId}/roles")
    public Result<Void> bindRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        sysUserService.bindRoles(userId, roleIds);
        return Result.ok();
    }

    @Operation(summary = "Reset password")
    @PostMapping("/{userId}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        authService.resetPassword(userId, body.get("newPassword"));
        return Result.ok();
    }
}
