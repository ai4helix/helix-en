package com.helix.console.system.controller;

import com.helix.console.common.Result;
import com.helix.console.system.service.DemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Workbench helper APIs.
 *
 * <p>Tenant demo data initialization: newly registered tenants
 * ({@code t_organization.demo_status=0}) see a "sample data not initialized"
 * guide bar on the workbench; clicking it has {@link DemoService} generate a
 * complete runnable consumer-loan demo and set the status.</p>
 */
@Tag(name = "Workbench")
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final DemoService demoService;

    @Operation(summary = "Current tenant demo data initialization status")
    @GetMapping("/demo-status")
    public Result<Map<String, Object>> demoStatus() {
        return Result.ok(demoService.status());
    }

    @Operation(summary = "Initialize current tenant demo data (idempotent)")
    @PostMapping("/demo-init")
    public Result<Map<String, Object>> demoInit() {
        return Result.ok(demoService.init());
    }
}
