package com.helix.console.engine.controller;

import com.helix.console.common.Result;
import com.helix.console.engine.entity.Engine;
import com.helix.console.engine.entity.EngineVersion;
import com.helix.console.engine.service.EngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "Engine Management")
@RestController
@RequestMapping("/api/engine")
@RequiredArgsConstructor
public class EngineController {

    private final EngineService engineService;

    @Operation(summary = "Engine list")
    @GetMapping("/list")
    public Result<List<Engine>> list(@RequestParam(required = false) Integer organId) {
        return Result.ok(engineService.listEngines(organId));
    }

    @Operation(summary = "Version list")
    @GetMapping("/{engineId}/versions")
    public Result<List<EngineVersion>> versions(@PathVariable Integer engineId) {
        return Result.ok(engineService.listVersions(engineId));
    }

    @Operation(summary = "Create an engine")
    @PostMapping("/create")
    public Result<Integer> create(@Valid @RequestBody Engine engine) {
        return Result.ok(engineService.createEngine(engine));
    }

    @Operation(summary = "Create a draft version")
    @PostMapping("/{engineId}/version")
    public Result<Integer> createVersion(@PathVariable Integer engineId) {
        return Result.ok(engineService.createVersion(engineId));
    }
}
