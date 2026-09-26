package com.helix.console.system.controller;

import com.helix.console.common.Result;
import com.helix.console.system.entity.SysOrganization;
import com.helix.console.system.service.SysOrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Organization (company) management API.
 */
@Tag(name = "Organization Management")
@RestController
@RequestMapping("/api/system/organization")
@RequiredArgsConstructor
public class SysOrganizationController {

    private final SysOrganizationService sysOrganizationService;

    @Operation(summary = "Organization list")
    @GetMapping("/list")
    public Result<List<SysOrganization>> listAll() {
        return Result.ok(sysOrganizationService.listAll());
    }

    @Operation(summary = "Organization detail")
    @GetMapping("/{organId}")
    public Result<SysOrganization> detail(@PathVariable Long organId) {
        return Result.ok(sysOrganizationService.detail(organId));
    }

    @Operation(summary = "Create organization")
    @PostMapping
    public Result<Long> create(@RequestBody SysOrganization org) {
        return Result.ok(sysOrganizationService.create(org));
    }

    @Operation(summary = "Update organization")
    @PutMapping
    public Result<Void> update(@RequestBody SysOrganization org) {
        sysOrganizationService.update(org);
        return Result.ok();
    }

    @Operation(summary = "Delete organization")
    @DeleteMapping("/{organId}")
    public Result<Void> remove(@PathVariable Long organId) {
        sysOrganizationService.remove(organId);
        return Result.ok();
    }

    @Operation(summary = "Enable/Disable")
    @PostMapping("/{organId}/status")
    public Result<Void> changeStatus(@PathVariable Long organId, @RequestParam Integer status) {
        sysOrganizationService.changeStatus(organId, status);
        return Result.ok();
    }
}
