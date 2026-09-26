package com.helix.console.engine.controller;

import com.helix.console.common.Result;
import com.helix.console.engine.dto.DecisionTableSaveDTO;
import com.helix.console.engine.service.DecisionTableService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Decision table management APIs (v3).
 *
 * <p>A decision table is a matrix-style rule of "condition columns x rule rows" (DMN pattern).
 * Decision nodes reference it via {@code decision_table_id} in node_json;
 * after publishing, the engine loads it from the published artifacts for execution.</p>
 */
@Tag(name = "Decision Table Management")
@RestController
@RequestMapping("/api/engine/dtable")
@RequiredArgsConstructor
public class DecisionTableController {

    private final DecisionTableService decisionTableService;

    @Operation(summary = "Save a decision table (create/edit, whole-table submission)")
    @PostMapping("/save")
    public Result<Integer> save(@RequestBody DecisionTableSaveDTO dto) {
        return Result.ok(decisionTableService.save(dto));
    }

    @Operation(summary = "Decision table detail")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Integer id) {
        return Result.ok(decisionTableService.detail(id));
    }

    @Operation(summary = "Decision table list")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(decisionTableService.list(keyword));
    }

    @Operation(summary = "Delete a decision table")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        decisionTableService.delete(id);
        return Result.ok(null);
    }
}
