package com.helix.console.platform.controller;

import com.helix.console.common.Result;
import com.helix.console.platform.dto.EngineStatVO;
import com.helix.console.platform.dto.RoleStatVO;
import com.helix.console.platform.dto.TenantStatVO;
import com.helix.console.platform.service.PlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Platform operator APIs (platform admin users only):
 * tenant list, role list, per-tenant engine run statistics.
 * The platform side offers no engine configuration; all endpoints here are read-only queries.
 */
@Tag(name = "Platform Operator")
@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    @Resource
    private PlatformService platformService;

    @Operation(summary = "Operations overview: tenants / users / decisions / batch runs")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(platformService.overview());
    }

    @Operation(summary = "Tenant list (with scale and engine run stats)")
    @GetMapping("/tenants")
    public Result<List<TenantStatVO>> tenants() {
        return Result.ok(platformService.tenantStats());
    }

    @Operation(summary = "Role list (with owning organization and bound user count)")
    @GetMapping("/roles")
    public Result<List<RoleStatVO>> roles() {
        return Result.ok(platformService.roleStats());
    }

    @Operation(summary = "Per-tenant engine run statistics (decisions + batch runs, aggregated by organization)")
    @GetMapping("/engine-stats")
    public Result<List<EngineStatVO>> engineStats() {
        return Result.ok(platformService.engineStats());
    }
}
