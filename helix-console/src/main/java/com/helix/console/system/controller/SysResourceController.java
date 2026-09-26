package com.helix.console.system.controller;

import com.helix.console.common.Result;
import com.helix.console.system.dto.TreeNodeVO;
import com.helix.console.system.entity.SysResource;
import com.helix.console.system.service.SysResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Resource (menu) management API.
 */
@Tag(name = "Resource Management")
@RestController
@RequestMapping("/api/system/resource")
@RequiredArgsConstructor
public class SysResourceController {

    private final SysResourceService sysResourceService;

    @Operation(summary = "Full resource tree")
    @GetMapping("/tree")
    public Result<List<TreeNodeVO>> tree() {
        return Result.ok(sysResourceService.tree());
    }

    @Operation(summary = "Menu tree visible to the current user")
    @GetMapping("/menu")
    public Result<List<TreeNodeVO>> menu() {
        return Result.ok(sysResourceService.currentUserTree());
    }

    @Operation(summary = "Resource detail")
    @GetMapping("/{resourceId}")
    public Result<SysResource> detail(@PathVariable Long resourceId) {
        return Result.ok(sysResourceService.detail(resourceId));
    }

    @Operation(summary = "Create resource")
    @PostMapping
    public Result<Long> create(@RequestBody SysResource resource) {
        return Result.ok(sysResourceService.create(resource));
    }

    @Operation(summary = "Update resource")
    @PutMapping
    public Result<Void> update(@RequestBody SysResource resource) {
        sysResourceService.update(resource);
        return Result.ok();
    }

    @Operation(summary = "Delete resource")
    @DeleteMapping("/{resourceId}")
    public Result<Void> remove(@PathVariable Long resourceId) {
        sysResourceService.remove(resourceId);
        return Result.ok();
    }
}
