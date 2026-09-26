package com.helix.console.knowledge.controller;

import com.helix.console.common.Result;
import com.helix.console.knowledge.dto.TreeNodeVO;
import com.helix.console.knowledge.service.KnowledgeTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Knowledge base catalog tree APIs.
 */
@Tag(name = "Knowledge Base Catalog")
@RestController
@RequestMapping("/api/knowledge/tree")
@RequiredArgsConstructor
public class KnowledgeTreeController {

    private final KnowledgeTreeService knowledgeTreeService;

    @Operation(summary = "Catalog tree")
    @GetMapping
    public Result<List<TreeNodeVO>> tree(
            @RequestParam(defaultValue = "0") Integer treeType,
            @RequestParam(required = false) Integer engineId) {
        return Result.ok(knowledgeTreeService.tree(treeType, engineId));
    }

    @Operation(summary = "Create a catalog")
    @PostMapping
    public Result<Integer> create(@RequestBody Map<String, Object> body) {
        return Result.ok(knowledgeTreeService.create(
                (String) body.get("name"),
                body.get("parentId") == null ? null : Integer.valueOf(body.get("parentId").toString()),
                body.get("treeType") == null ? null : Integer.valueOf(body.get("treeType").toString()),
                body.get("engineId") == null ? null : Integer.valueOf(body.get("engineId").toString()),
                body.get("organId") == null ? null : Integer.valueOf(body.get("organId").toString())));
    }

    @Operation(summary = "Rename a catalog")
    @PutMapping("/{id}/name")
    public Result<Void> rename(@PathVariable Integer id, @RequestParam String name) {
        knowledgeTreeService.rename(id, name);
        return Result.ok();
    }

    @Operation(summary = "Move a catalog")
    @PutMapping("/{id}/parent")
    public Result<Void> move(@PathVariable Integer id, @RequestParam(required = false) Integer parentId) {
        knowledgeTreeService.move(id, parentId);
        return Result.ok();
    }

    @Operation(summary = "Delete a catalog")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Integer id) {
        knowledgeTreeService.remove(id);
        return Result.ok();
    }
}
