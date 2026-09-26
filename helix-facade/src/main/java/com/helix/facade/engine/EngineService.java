package com.helix.facade.engine;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import javax.validation.Valid;
import java.util.Map;

/**
 * Decision engine client (inter-service contract layer).
 *
 * <p>Addressed by service name {@code helix-engine} via nacos service discovery; on cluster
 * deployment Ribbon + nacos balance the load automatically. Retries, envelope code checks and
 * exception translation are the consumer's responsibility at the call layer (e.g. console's
 * EngineClient) — the engine's POST semantics are not idempotent, so no framework-level auto retry.</p>
 *
 * <p>Except for {@link #callEngine(EngineApiReq)} (hot path, strongly typed), the remaining
 * low-frequency governance endpoints pass through Maps of the {@code Result<T>} envelope; the
 * consumer checks the code and unwraps data.</p>
 */
@FeignClient("helix-engine")
public interface EngineService {

    /**
     * Executes one decision.
     */
    @PostMapping("/engineApi/decision")
    EngineApiRsp callEngine(@Valid @RequestBody EngineApiReq engineApiReq);

    /**
     * Rule dry run: evaluates conditions one by one without persisting (editor "dry run" panel).
     * Request body: {conditions, isNon, variables}.
     */
    @PostMapping("/engineApi/eval")
    Map<String, Object> eval(@RequestBody Map<String, Object> req);

    /**
     * Rebuilds the full snapshot (entry point for immediate effect after config changes; idempotent).
     */
    @PostMapping("/engineApi/update")
    Map<String, Object> updateSnapshot();

    /**
     * Incrementally reloads the given engines (including list DB refresh).
     * Request body: {engineCodes: [...], refreshLists: true}.
     */
    @PostMapping("/engineApi/reload-engines")
    Map<String, Object> reloadEngines(@RequestBody Map<String, Object> req);

    /**
     * Publishes the given version: freezes the artifact + immediately rebuilds the snapshot.
     */
    @PostMapping("/engineApi/publish/{versionId}")
    Map<String, Object> publish(@PathVariable("versionId") Integer versionId);

    /**
     * Only freezes the publish artifact without rebuilding the snapshot (mandatory step before console marks deployment).
     */
    @PostMapping("/engineApi/publish/{versionId}/artifact")
    Map<String, Object> publishArtifact(@PathVariable("versionId") Integer versionId);

    /**
     * Gray release: freezes the current config as a new track that coexists with the live one and splits traffic by weight.
     */
    @PostMapping("/engineApi/publish/{versionId}/gray")
    Map<String, Object> grayPublish(@PathVariable("versionId") Integer versionId,
                                    @RequestParam("weight") Integer weight);

    /**
     * Adjusts track weight (0 = pause the track).
     */
    @PostMapping("/engineApi/publish/track/{publishId}/weight")
    Map<String, Object> setTrackWeight(@PathVariable("publishId") Long publishId,
                                       @RequestParam("weight") Integer weight);

    /**
     * Shadow-mode switch.
     */
    @PostMapping("/engineApi/publish/track/{publishId}/shadow")
    Map<String, Object> setTrackShadow(@PathVariable("publishId") Long publishId,
                                       @RequestParam("on") boolean on);

    /**
     * Full cutover (instant switch-back rollback): target track to 100, all other tracks of the same version to 0.
     */
    @PostMapping("/engineApi/publish/track/{publishId}/promote")
    Map<String, Object> promoteTrack(@PathVariable("publishId") Long publishId);

    /**
     * Publish tracks in effect for the version (data source of the gray-release panel).
     */
    @GetMapping("/engineApi/publish/list")
    Map<String, Object> listPublishTracks(@RequestParam("versionId") Integer versionId);

    /**
     * Routing view of the version in the current snapshot (weight / shadow / whether traffic can be split).
     */
    @GetMapping("/engineApi/routing/{versionId}")
    Map<String, Object> routing(@PathVariable("versionId") Integer versionId);
}
