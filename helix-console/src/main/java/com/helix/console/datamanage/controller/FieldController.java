package com.helix.console.datamanage.controller;

import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import com.helix.console.datamanage.dto.FieldDetailVO;
import com.helix.console.datamanage.dto.FieldLineageDTO;
import com.helix.console.datamanage.dto.FieldSaveDTO;
import com.helix.console.datamanage.dto.FieldVO;
import com.helix.console.datamanage.dto.LineageGraphDTO;
import com.helix.console.datamanage.service.FieldLineageService;
import com.helix.console.datamanage.service.FieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Field APIs. Used by the rule condition editor and scorecard configuration.
 */
@Tag(name = "Field Management")
@RestController
@RequestMapping("/api/datamanage/field")
@RequiredArgsConstructor
public class FieldController {

    private final FieldService fieldService;
    private final FieldLineageService fieldLineageService;

    @Operation(summary = "Paged field query (catalogId filters by catalog including descendants; recycle=true queries the recycle bin)")
    @GetMapping("/page")
    public Result<PageResult<FieldVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer fieldTypeId,
            @RequestParam(required = false) Integer isOutput,
            @RequestParam(required = false) Integer catalogId,
            @RequestParam(required = false) Boolean recycle,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(fieldService.page(keyword, fieldTypeId, isOutput, catalogId, recycle, pageNo, pageSize));
    }

    @Operation(summary = "All fields (for dropdown selection)")
    @GetMapping("/list")
    public Result<List<FieldVO>> listAll() {
        return Result.ok(fieldService.listAll());
    }

    @Operation(summary = "Field type categories")
    @GetMapping("/types")
    public Result<List<Map<String, Object>>> types() {
        return Result.ok(fieldService.listFieldTypes());
    }

    @Operation(summary = "Batch query by ids")
    @PostMapping("/listByIds")
    public Result<List<FieldVO>> listByIds(@RequestBody List<Integer> ids) {
        return Result.ok(fieldService.listByIds(ids));
    }

    @Operation(summary = "Field detail (including derived dependencies and downstream reference overview)")
    @GetMapping("/{id}/detail")
    public Result<FieldDetailVO> detail(@PathVariable Integer id) {
        return Result.ok(fieldLineageService.detail(id));
    }

    @Operation(summary = "Field lineage lookup: lists all downstream rules/scorecards/decision tables/list DBs referencing this field and their owning nodes")
    @GetMapping("/lineage")
    public Result<FieldLineageDTO> lineage(@RequestParam Integer fieldId) {
        return Result.ok(fieldLineageService.lineage(fieldId));
    }

    @Operation(summary = "Decision-flow-level lineage graph: given a decision flow version, returns the full graph of field -> knowledge object -> node -> version")
    @GetMapping("/lineage/graph")
    public Result<LineageGraphDTO> graph(@RequestParam Integer versionId) {
        return Result.ok(fieldLineageService.graph(versionId));
    }


    // ---------------- Single-record maintenance (bulk import uses POST /api/batch/field/import) ----------------

    @Operation(summary = "Create field (this organization)")
    @PostMapping
    public Result<FieldVO> create(@RequestBody FieldSaveDTO dto) {
        return Result.ok(fieldService.create(dto));
    }

    @Operation(summary = "Update field (fields of this organization only)")
    @PutMapping("/{id}")
    public Result<FieldVO> update(@PathVariable Integer id, @RequestBody FieldSaveDTO dto) {
        return Result.ok(fieldService.update(id, dto));
    }

    @Operation(summary = "Delete field (this organization only; deletion breaks referencing expressions, caller must confirm)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        fieldService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "Move field to catalog (v5.6.35 drag-and-drop; empty catalogId = uncategorized)")
    @PutMapping("/{id}/catalog")
    public Result<Void> moveCatalog(@PathVariable Integer id,
                                    @RequestBody FieldSaveDTO dto) {
        fieldService.moveCatalog(id, dto.getCatalogId());
        return Result.ok();
    }


    // ---------------- Recycle bin (soft delete) ----------------

    @Operation(summary = "Move to recycle bin (batch)")
    @PostMapping("/recycle")
    public Result<Void> recycle(@RequestBody List<Integer> ids) {
        fieldService.recycle(ids);
        return Result.ok();
    }

    @Operation(summary = "Restore from recycle bin (batch)")
    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody List<Integer> ids) {
        fieldService.restore(ids);
        return Result.ok();
    }

    @Operation(summary = "Delete permanently (only recycled fields can be physically removed, batch)")
    @DeleteMapping("/permanent")
    public Result<Void> deletePermanent(@RequestBody List<Integer> ids) {
        fieldService.deletePermanently(ids);
        return Result.ok();
    }
}
