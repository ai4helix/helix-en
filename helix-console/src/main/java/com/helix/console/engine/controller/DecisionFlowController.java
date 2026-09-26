package com.helix.console.engine.controller;

import com.helix.console.common.Result;
import com.helix.console.engine.dto.GraphVO;
import com.helix.console.engine.dto.NodeMoveDTO;
import com.helix.console.engine.dto.NodeSaveDTO;
import com.helix.console.engine.dto.VersionSaveDTO;
import com.helix.console.engine.service.DecisionFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * Decision flow (AntV X6 canvas) APIs.
 *
 * <p>Replaces the legacy JSP version {@code /decision_flow/*}; everything returns JSON.</p>
 */
@Tag(name = "Decision Flow Canvas")
@RestController
@RequestMapping("/api/engine/flow")
@RequiredArgsConstructor
@Validated
public class DecisionFlowController {

    private final DecisionFlowService decisionFlowService;

    @Operation(summary = "Load the decision flow graph")
    @GetMapping("/graph/{versionId}")
    public Result<GraphVO> loadGraph(@PathVariable Integer versionId) {
        return Result.ok(decisionFlowService.loadGraph(versionId));
    }

    @Operation(summary = "Save the whole graph")
    @PostMapping("/graph/save")
    public Result<Void> saveGraph(@Valid @RequestBody VersionSaveDTO dto) {
        decisionFlowService.saveGraph(dto);
        return Result.ok();
    }

    @Operation(summary = "Create or update a node")
    @PostMapping("/node")
    public Result<Integer> saveNode(@Valid @RequestBody NodeSaveDTO dto) {
        return Result.ok(decisionFlowService.saveNode(dto));
    }

    @Operation(summary = "Delete a node")
    @DeleteMapping("/node/{versionId}/{nodeId}")
    public Result<Void> removeNode(@PathVariable Integer versionId, @PathVariable Integer nodeId) {
        decisionFlowService.removeNode(versionId, nodeId);
        return Result.ok();
    }

    @Operation(summary = "Batch delete nodes")
    @DeleteMapping("/node/{versionId}")
    public Result<Void> removeNodes(@PathVariable Integer versionId,
                                    @RequestBody List<Integer> nodeIds) {
        decisionFlowService.removeNodes(versionId, nodeIds);
        return Result.ok();
    }

    @Operation(summary = "Node position and edge changes")
    @PostMapping("/node/move")
    public Result<Void> moveAndLink(@Valid @RequestBody NodeMoveDTO dto) {
        decisionFlowService.moveAndLink(dto);
        return Result.ok();
    }

    @Operation(summary = "Copy a node")
    @PostMapping("/node/{nodeId}/copy")
    public Result<Integer> copyNode(@PathVariable Integer nodeId) {
        return Result.ok(decisionFlowService.copyNode(nodeId));
    }

    @Operation(summary = "Validate the decision flow structure")
    @PostMapping("/graph/{versionId}/validate")
    public Result<Void> validateGraph(@PathVariable Integer versionId) {
        decisionFlowService.validateGraph(versionId);
        return Result.ok();
    }

    @Operation(summary = "Publish a version")
    @PostMapping("/version/{versionId}/publish")
    public Result<Void> publishVersion(@PathVariable Integer versionId) {
        decisionFlowService.publishVersion(versionId);
        return Result.ok();
    }

    @Operation(summary = "Save as draft")
    @PostMapping("/version/{versionId}/draft")
    public Result<Integer> saveAsDraft(@PathVariable Integer versionId) {
        return Result.ok(decisionFlowService.saveAsDraft(versionId));
    }

    @Operation(summary = "Delete a version (running versions cannot be deleted; cascades cleanup of nodes/edges/relations/artifacts)")
    @DeleteMapping("/version/{versionId}")
    public Result<Void> removeVersion(@PathVariable Integer versionId) {
        decisionFlowService.removeVersion(versionId);
        return Result.ok();
    }
}
