package com.helix.console.engine.controller;

import com.helix.console.common.Result;
import com.helix.console.engine.client.EngineClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Gray release panel: forwards to helix-engine's track governance APIs.
 *
 * <p>See db/ENGINE-SCHEMA-V3.md §5.1 for the track model: multiple active artifacts of the same version coexist
 * and split traffic by normalized traffic_weight; tracks with shadow=1 only evaluate and record, do not return and
 * do not participate in traffic splitting; promote performs a full switchover + keeps artifacts = instant rollback.</p>
 */
@Tag(name = "Gray Release")
@RestController
@RequestMapping("/api/engine/flow")
@RequiredArgsConstructor
@Validated
public class GrayPublishController {

    private final EngineClient engineClient;

    @Operation(summary = "Version publish track list (data source of the gray release panel)")
    @GetMapping("/version/{versionId}/tracks")
    public Result<List<Map<String, Object>>> tracks(@PathVariable Integer versionId) {
        return Result.ok(engineClient.listPublishTracks(versionId));
    }

    @Operation(summary = "Current snapshot routing view (verify after reload)")
    @GetMapping("/version/{versionId}/routing")
    public Result<List<Map<String, Object>>> routing(@PathVariable Integer versionId) {
        return Result.ok(engineClient.routing(versionId));
    }

    @Operation(summary = "Gray release: solidify the current configuration into a new track, coexists with production and splits traffic")
    @PostMapping("/version/{versionId}/gray")
    public Result<Map<String, Object>> grayPublish(@PathVariable Integer versionId,
                                                   @RequestBody GrayReq req) {
        return Result.ok(engineClient.grayPublish(versionId, req.getWeight()), "Gray release completed");
    }

    @Operation(summary = "Adjust track weight (0 = pause the track)")
    @PostMapping("/track/{publishId}/weight")
    public Result<List<Map<String, Object>>> setWeight(@PathVariable Long publishId,
                                                       @RequestBody GrayReq req) {
        return Result.ok(engineClient.setTrackWeight(publishId, req.getWeight()), "Weight adjusted");
    }

    @Operation(summary = "Shadow mode switch: only evaluate and record, do not return and do not participate in traffic splitting")
    @PostMapping("/track/{publishId}/shadow")
    public Result<List<Map<String, Object>>> setShadow(@PathVariable Long publishId,
                                                       @RequestBody ShadowReq req) {
        return Result.ok(engineClient.setTrackShadow(publishId, req.isOn()), "Shadow mode switched");
    }

    @Operation(summary = "Full switchover (instant rollback): target track 100, others set to 0 keeping artifacts")
    @PostMapping("/track/{publishId}/promote")
    public Result<List<Map<String, Object>>> promote(@PathVariable Long publishId) {
        return Result.ok(engineClient.promoteTrack(publishId), "All traffic taken over");
    }

    // ===== Request bodies =====

    @lombok.Data
    public static class GrayReq {
        /** Gray release weight 0-100 */
        private Integer weight;
    }

    @lombok.Data
    public static class ShadowReq {
        /** true enters shadow / false exits shadow */
        private boolean on;
    }
}
