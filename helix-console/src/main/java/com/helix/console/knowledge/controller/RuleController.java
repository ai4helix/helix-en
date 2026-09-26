package com.helix.console.knowledge.controller;

import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import com.helix.console.knowledge.dto.RuleConditionNodeDTO;
import com.helix.console.knowledge.dto.RuleSaveDTO;
import com.helix.console.knowledge.dto.RuleVO;
import com.helix.console.knowledge.service.RuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * Rule APIs. Replaces the legacy JSP version {@code knowledge/rule/*}.
 */
@Tag(name = "Rule Management")
@RestController
@RequestMapping("/api/knowledge/rule")
@RequiredArgsConstructor
@Validated
public class RuleController {

    private final RuleService ruleService;

    @Operation(summary = "Rule paged list")
    @GetMapping("/page")
    public Result<PageResult<RuleVO>> page(
            @RequestParam(required = false) Integer parentId,
            @RequestParam(required = false) Integer engineId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "12") long pageSize) {
        return Result.ok(ruleService.page(parentId, engineId, status, keyword, pageNo, pageSize));
    }

    @Operation(summary = "Rule detail (including condition details)")
    @GetMapping("/{id}")
    public Result<RuleVO> detail(@PathVariable Integer id) {
        return Result.ok(ruleService.detail(id));
    }

    @Operation(summary = "Rule condition AST tree (for the frontend X6 condition subtree visualization)")
    @GetMapping("/{id}/ast")
    public Result<List<RuleConditionNodeDTO>> ast(@PathVariable Integer id) {
        return Result.ok(ruleService.getRuleAst(id));
    }

    @Operation(summary = "Batch query by ids (for the node knowledge selector)")
    @PostMapping("/listByIds")
    public Result<List<RuleVO>> listByIds(@RequestBody List<Integer> ids) {
        return Result.ok(ruleService.listByIds(ids));
    }

    @Operation(summary = "Create a rule")
    @PostMapping
    public Result<Integer> create(@Valid @RequestBody RuleSaveDTO dto) {
        return Result.ok(ruleService.create(dto));
    }

    @Operation(summary = "Update a rule")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody RuleSaveDTO dto) {
        ruleService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "Copy a rule")
    @PutMapping("/{id}/parent")
    public Result<Void> moveCatalog(@PathVariable Integer id,
                                    @RequestBody com.helix.console.knowledge.dto.RuleMoveDTO dto) {
        ruleService.moveCatalog(id, dto.getParentId());
        return Result.ok();
    }

    @PostMapping("/{id}/copy")
    public Result<Integer> copy(@PathVariable Integer id) {
        return Result.ok(ruleService.copy(id));
    }

    @Operation(summary = "Rule trial run (evaluates conditions one by one, nothing persisted)")
    @PostMapping("/dry-run")
    public Result<java.util.Map<String, Object>> dryRun(
            @RequestBody com.helix.console.knowledge.dto.DryRunReq req) {
        return Result.ok(ruleService.dryRun(req));
    }

    @Operation(summary = "Batch rule briefs (rule logic details for the decision flow panel)")
    @GetMapping("/briefs")
    public Result<java.util.List<com.helix.console.knowledge.dto.RuleBriefVO>> briefs(
            @RequestParam("ids") java.util.List<Integer> ids) {
        return Result.ok(ruleService.listBriefs(ids));
    }

    @Operation(summary = "Enable/Disable")
    @PostMapping("/status")
    public Result<Void> changeStatus(@RequestParam List<Integer> ids, @RequestParam Integer status) {
        ruleService.changeStatus(ids, status);
        return Result.ok();
    }

    @Operation(summary = "Move to recycle bin")
    @PostMapping("/recycle")
    public Result<Void> moveToRecycle(@RequestBody List<Integer> ids) {
        ruleService.moveToRecycle(ids);
        return Result.ok();
    }

    @Operation(summary = "Restore from recycle bin")
    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody List<Integer> ids) {
        ruleService.restore(ids);
        return Result.ok();
    }

    @Operation(summary = "Delete permanently")
    @DeleteMapping("/permanent")
    public Result<Void> removePermanently(@RequestBody List<Integer> ids) {
        ruleService.removePermanently(ids);
        return Result.ok();
    }
}
