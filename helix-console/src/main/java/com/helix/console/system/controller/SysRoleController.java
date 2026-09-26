package com.helix.console.system.controller;

import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import com.helix.console.system.entity.SysRole;
import com.helix.console.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Role management API.
 */
@Tag(name = "Role Management")
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
@Validated
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @Operation(summary = "Role page list")
    @GetMapping("/page")
    public Result<PageResult<SysRole>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long organId,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(sysRoleService.page(keyword, organId, pageNo, pageSize));
    }

    @Operation(summary = "All enabled roles (for dropdowns)")
    @GetMapping("/list")
    public Result<List<SysRole>> listAll() {
        return Result.ok(sysRoleService.listAll());
    }

    @Operation(summary = "Role detail (with granted resources)")
    @GetMapping("/{roleId}")
    public Result<Map<String, Object>> detail(@PathVariable Long roleId) {
        return Result.ok(sysRoleService.detail(roleId));
    }

    @Operation(summary = "Create role")
    @PostMapping
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        SysRole role = parseRole(body);
        return Result.ok(sysRoleService.create(role, parseIds(body.get("resourceIds"))));
    }

    @Operation(summary = "Update role")
    @PutMapping
    public Result<Void> update(@RequestBody Map<String, Object> body) {
        SysRole role = parseRole(body);
        role.setId(parseLong(body.get("roleId")));
        sysRoleService.update(role, parseIds(body.get("resourceIds")));
        return Result.ok();
    }

    @Operation(summary = "Batch delete roles")
    @DeleteMapping
    public Result<Void> remove(@RequestBody List<Long> roleIds) {
        sysRoleService.remove(roleIds);
        return Result.ok();
    }

    @Operation(summary = "Enable/Disable")
    @PostMapping("/status")
    public Result<Void> changeStatus(@RequestParam List<Long> roleIds, @RequestParam Integer status) {
        sysRoleService.changeStatus(roleIds, status);
        return Result.ok();
    }

    @Operation(summary = "Grant resources to role")
    @PostMapping("/{roleId}/resources")
    public Result<Void> bindResources(@PathVariable Long roleId, @RequestBody List<Long> resourceIds) {
        sysRoleService.bindResources(roleId, resourceIds);
        return Result.ok();
    }


    @SuppressWarnings("unchecked")
    private SysRole parseRole(Map<String, Object> body) {
        SysRole role = new SysRole();
        role.setRoleName((String) body.get("roleName"));
        role.setRoleCode((String) body.get("roleCode"));
        role.setRoleDesc((String) body.get("roleDesc"));
        role.setOrganId(parseLong(body.get("organId")));
        Object status = body.get("status");
        if (status != null) {
            role.setStatus(Integer.valueOf(String.valueOf(status)));
        }
        return role;
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseIds(Object o) {
        if (o instanceof List) {
            return ((List<Object>) o).stream()
                    .map(this::parseLong)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
        }
        return null;
    }

    private Long parseLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
