package com.helix.console.datamanage.controller;

import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import com.helix.console.datamanage.entity.ListDb;
import com.helix.console.datamanage.service.ListDbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * List DB API. Used by list DB management and decision flow node selector.
 */
@Tag(name = "List DB Management")
@RestController
@RequestMapping("/api/datamanage/listdb")
@RequiredArgsConstructor
@Validated
public class ListDbController {

    private final ListDbService listDbService;

    @Operation(summary = "List DB page list")
    @GetMapping("/page")
    public Result<PageResult<ListDb>> page(
            @RequestParam(required = false) String listType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "12") long pageSize) {
        return Result.ok(listDbService.page(listType, status, keyword, pageNo, pageSize));
    }

    @Operation(summary = "Available list DBs (for node selector)")
    @GetMapping("/available")
    public Result<List<ListDb>> available(@RequestParam(required = false) String listType) {
        return Result.ok(listDbService.listAvailable(listType));
    }

    @Operation(summary = "Batch query by ids")
    @PostMapping("/listByIds")
    public Result<List<ListDb>> listByIds(@RequestBody List<Integer> ids) {
        return Result.ok(listDbService.listByIds(ids));
    }

    @Operation(summary = "List DB detail")
    @GetMapping("/{id}")
    public Result<ListDb> detail(@PathVariable Integer id) {
        return Result.ok(listDbService.detail(id));
    }

    @Operation(summary = "Create list DB")
    @PostMapping
    public Result<Integer> create(@Valid @RequestBody ListDb listDb) {
        return Result.ok(listDbService.create(listDb));
    }

    @Operation(summary = "Update list DB")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody ListDb listDb) {
        listDbService.update(listDb);
        return Result.ok();
    }

    @Operation(summary = "Copy list DB")
    @PostMapping("/{id}/copy")
    public Result<Integer> copy(@PathVariable Integer id) {
        return Result.ok(listDbService.copy(id));
    }

    @Operation(summary = "Enable/Disable")
    @PostMapping("/status")
    public Result<Void> changeStatus(@RequestParam List<Integer> ids, @RequestParam Integer status) {
        listDbService.changeStatus(ids, status);
        return Result.ok();
    }

    @Operation(summary = "Move to recycle bin")
    @PostMapping("/recycle")
    public Result<Void> moveToRecycle(@RequestBody List<Integer> ids) {
        listDbService.moveToRecycle(ids);
        return Result.ok();
    }

    @Operation(summary = "Restore from recycle bin")
    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody List<Integer> ids) {
        listDbService.restore(ids);
        return Result.ok();
    }

    @Operation(summary = "Delete permanently")
    @DeleteMapping("/permanent")
    public Result<Void> removePermanently(@RequestBody List<Integer> ids) {
        listDbService.removePermanently(ids);
        return Result.ok();
    }

    // ===== List entries (standard t_list_entry storage; engine loads them into snapshot for decisions) =====

    @Operation(summary = "List entry page")
    @GetMapping("/{id}/entries")
    public Result<PageResult<com.helix.console.datamanage.entity.ListEntry>> entries(
            @PathVariable Integer id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(listDbService.pageEntries(id, keyword, status, pageNo, pageSize));
    }

    @Operation(summary = "Batch add entries (split by line/comma, auto dedupe)")
    @PostMapping("/{id}/entries")
    public Result<Integer> addEntries(@PathVariable Integer id,
                                      @Valid @RequestBody EntryBatchReq req) {
        return Result.ok(listDbService.addEntries(id, req.getValues(), req.getRemark(),
                req.getEffectiveFrom(), req.getEffectiveTo()), "Added successfully");
    }

    @Operation(summary = "Enable/disable entries")
    @PostMapping("/{id}/entries/status")
    public Result<Void> changeEntryStatus(@PathVariable Integer id,
                                          @RequestBody EntryStatusReq req) {
        listDbService.changeEntryStatus(id, req.getEntryIds(), req.getStatus());
        return Result.ok();
    }

    @Operation(summary = "Delete entries (logical delete)")
    @DeleteMapping("/{id}/entries")
    public Result<Void> removeEntries(@PathVariable Integer id,
                                      @RequestBody List<Long> entryIds) {
        listDbService.removeEntries(id, entryIds);
        return Result.ok();
    }

    // ===== Request bodies =====

    @lombok.Data
    public static class EntryBatchReq {
        /** Entry text: split by line/comma/semicolon/whitespace */
        @javax.validation.constraints.NotBlank(message = "Entry content must not be blank")
        private String values;
        private String remark;
        private java.time.LocalDateTime effectiveFrom;
        private java.time.LocalDateTime effectiveTo;
    }

    @lombok.Data
    public static class EntryStatusReq {
        @javax.validation.constraints.NotEmpty(message = "entryIds must not be blank")
        private List<Long> entryIds;
        /** 1 enabled / 0 disabled */
        @javax.validation.constraints.NotNull(message = "status must not be blank")
        private Integer status;
    }
}
