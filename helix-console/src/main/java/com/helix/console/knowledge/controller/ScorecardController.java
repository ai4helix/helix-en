package com.helix.console.knowledge.controller;

import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import com.helix.console.knowledge.dto.ScorecardSaveDTO;
import com.helix.console.knowledge.entity.Scorecard;
import com.helix.console.knowledge.service.ScorecardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * Scorecard API. Replaces the original JSP {@code knowledge/scorecard/*}.
 */
@Tag(name = "Scorecard Management")
@RestController
@RequestMapping("/api/knowledge/scorecard")
@RequiredArgsConstructor
@Validated
public class ScorecardController {

    private final ScorecardService scorecardService;

    @Operation(summary = "Scorecard page list")
    @GetMapping("/page")
    public Result<PageResult<Scorecard>> page(
            @RequestParam(required = false) Integer parentId,
            @RequestParam(required = false) Integer engineId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "12") long pageSize) {
        return Result.ok(scorecardService.page(parentId, engineId, status, keyword, pageNo, pageSize));
    }

    @Operation(summary = "Scorecard detail (dimensions structured)")
    @GetMapping("/{id}")
    public Result<ScorecardSaveDTO> detail(@PathVariable Integer id) {
        return Result.ok(scorecardService.detail(id));
    }

    @Operation(summary = "Batch query by ids (for node knowledge selector)")
    @PostMapping("/listByIds")
    public Result<List<Scorecard>> listByIds(@RequestBody List<Integer> ids) {
        return Result.ok(scorecardService.listByIds(ids));
    }

    @Operation(summary = "Create scorecard")
    @PostMapping
    public Result<Integer> create(@Valid @RequestBody ScorecardSaveDTO dto) {
        return Result.ok(scorecardService.create(dto));
    }

    @Operation(summary = "Update scorecard")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody ScorecardSaveDTO dto) {
        scorecardService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "Copy scorecard")
    @PostMapping("/{id}/copy")
    public Result<Integer> copy(@PathVariable Integer id) {
        return Result.ok(scorecardService.copy(id));
    }

    @Operation(summary = "Enable/Disable")
    @PostMapping("/status")
    public Result<Void> changeStatus(@RequestParam List<Integer> ids, @RequestParam Integer status) {
        scorecardService.changeStatus(ids, status);
        return Result.ok();
    }

    @Operation(summary = "Move to recycle bin")
    @PostMapping("/recycle")
    public Result<Void> moveToRecycle(@RequestBody List<Integer> ids) {
        scorecardService.moveToRecycle(ids);
        return Result.ok();
    }

    @Operation(summary = "Restore from recycle bin")
    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody List<Integer> ids) {
        scorecardService.restore(ids);
        return Result.ok();
    }

    @Operation(summary = "Delete permanently")
    @DeleteMapping("/permanent")
    public Result<Void> removePermanently(@RequestBody List<Integer> ids) {
        scorecardService.removePermanently(ids);
        return Result.ok();
    }
}
