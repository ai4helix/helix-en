package com.helix.engine.api;

import com.helix.engine.common.Result;
import com.helix.engine.config.EngineSnapshotHolder;
import com.helix.engine.config.EngineSnapshotLoader;
import com.helix.engine.entity.EngineApiReq;
import com.helix.engine.entity.EngineApiRsp;
import com.helix.engine.core.DecisionOrchestrator;
import com.helix.engine.rule.ast.FlatConditions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine execution endpoints.
 *
 * <ul>
 *   <li>{@code POST /engineApi/decision} -- execute a decision;</li>
 *   <li>{@code POST /engineApi/update} -- rebuild the snapshot (called by the config side after publishing);</li>
 *   <li>{@code POST /engineApi/eval} -- rule dry-run (editor-side per-condition evaluation, not persisted);</li>
 *   <li>{@code GET  /engineApi/status} -- snapshot status.</li>
 *   <li>Gray release: {@code GET /publish/list}, {@code POST /publish/{versionId}/gray},
 *       {@code POST /publish/track/{publishId}/weight|shadow|promote},
 *       {@code GET /routing/{versionId}}.</li>
 * </ul>
 * CRUD of configuration data belongs to helix-console (the rule config side); this service only executes.
 */
@Slf4j
@RestController
@RequestMapping("/engineApi")
@RequiredArgsConstructor
@Validated
public class EngineApiController {

    private final DecisionOrchestrator orchestrator;
    private final EngineSnapshotLoader snapshotLoader;
    private final EngineSnapshotHolder snapshotHolder;

    /** Rule dry-run request body */
    public static class EvalReq {
        public List<FlatConditions.Flat> conditions;
        public Integer isNon;
        public Map<String, Object> variables;
    }

    /** Execute a decision */
    @PostMapping("/decision")
    public Result<EngineApiRsp> decision(@Valid @RequestBody EngineApiReq req) {
        return Result.ok(orchestrator.execute(req));
    }

    /**
     * Reload engine config: rebuild the snapshot (prefetch condition ASTs / scorecards / rule names).
     * The old snapshot keeps serving during the rebuild; it is atomically replaced once built.
     *
     * <p>v3 load order: deployed versions are restored from publish artifacts (t_flow_publish)
     * first; without an artifact they are collected from the live DB and self-healed.
     * Afterwards, config changes take effect only via re-publishing.</p>
     */
    @PostMapping("/update")
    public Result<Map<String, Object>> update() {
        long start = System.currentTimeMillis();
        snapshotLoader.reload();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engineCount", snapshotHolder.getEngineCount());
        data.put("nodeCount", snapshotHolder.getNodeCount());
        data.put("policyNodeCount", snapshotHolder.getPolicyNodeCount());
        data.put("rulePlanCount", snapshotHolder.getRulePlanCount());
        data.put("reloadCount", snapshotHolder.getReloadCount());
        data.put("costMs", System.currentTimeMillis() - start);
        return Result.ok(data, "Engine snapshot rebuilt");
    }

    /**
     * Lightweight list reload: rebuilds only the list sets, reusing engines/versions/channels.
     *
     * <p>List DB CRUD does not change the decision-flow structure, so using it instead of
     * {@code /update} avoids "rescanning all publish artifacts + fully reloading lists" --
     * the right entry point for high-frequency list changes.</p>
     */
    @PostMapping("/reload-lists")
    public Result<Map<String, Object>> reloadLists() {
        long start = System.currentTimeMillis();
        snapshotLoader.reloadLists();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("listDbCount", snapshotHolder.getListDbCount());
        data.put("listEntryCount", snapshotHolder.getListEntryCount());
        data.put("reloadCount", snapshotHolder.getReloadCount());
        data.put("costMs", System.currentTimeMillis() - start);
        return Result.ok(data, "Lists lightly reloaded");
    }

    /**
     * Engine-granularity incremental reload: rebuilds only the given engine's versions and
     * channels; other engines reuse the current snapshot.
     *
     * <p>Suitable for single-engine config adjustments; a missing or disabled engine is
     * removed from the snapshot.</p>
     */
    @PostMapping("/reload-engine/{engineCode}")
    public Result<Map<String, Object>> reloadEngine(@PathVariable String engineCode) {
        long start = System.currentTimeMillis();
        snapshotLoader.reloadEngine(engineCode);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engineCode", engineCode);
        data.put("engineCount", snapshotHolder.getEngineCount());
        data.put("nodeCount", snapshotHolder.getNodeCount());
        data.put("reloadCount", snapshotHolder.getReloadCount());
        data.put("costMs", System.currentTimeMillis() - start);
        return Result.ok(data, "Engine " + engineCode + " incrementally reloaded");
    }

    /**
     * Incremental reload by engine list (with lists): rebuilds only the given engines +
     * refreshes the list sets.
     *
     * <p>Designed for "list DB changes": console, knowing which engines reference a list DB,
     * notifies only the related engines, avoiding a platform-wide full rebuild. Request body:
     * {@code {"engineCodes": ["A","B"], "refreshLists": true}};
     * an empty {@code engineCodes} degrades to lists-only refresh.</p>
     */
    @PostMapping("/reload-engines")
    public Result<Map<String, Object>> reloadEngines(@RequestBody(required = false) Map<String, Object> body) {
        long start = System.currentTimeMillis();
        List<String> codes = new ArrayList<>();
        boolean refreshLists = true;
        if (body != null) {
            Object raw = body.get("engineCodes");
            if (raw instanceof List) {
                for (Object o : (List<?>) raw) {
                    if (o != null && !String.valueOf(o).trim().isEmpty()) {
                        codes.add(String.valueOf(o).trim());
                    }
                }
            }
            Object rl = body.get("refreshLists");
            if (rl instanceof Boolean) {
                refreshLists = (Boolean) rl;
            }
        }
        // Go through the batch entry -- one write lock, lists scanned once, one publish.
        // The early implementation looped per code (N full list scans + N separate write-lock sections).
        snapshotLoader.reloadEngines(codes, refreshLists);
        // Note: reloadCount is the "logical reload count" and replaceCount the "snapshot replacement count";
        // normally one reload replaces once, so the two should grow roughly in sync
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engineCodes", codes);
        data.put("reloaded", codes.size());
        data.put("engineCount", snapshotHolder.getEngineCount());
        data.put("listDbCount", snapshotHolder.getListDbCount());
        data.put("listEntryCount", snapshotHolder.getListEntryCount());
        data.put("reloadCount", snapshotHolder.getReloadCount());
        data.put("costMs", System.currentTimeMillis() - start);
        return Result.ok(data, "Incrementally reloaded by engine granularity");
    }

    /**
     * Publish the given version: freeze the version's current config into a publish artifact
     * and rebuild the snapshot immediately.
     *
     * <p>After publishing, console-side changes to rules/scorecards no longer affect this
     * version's live behavior until the next publish. The artifact carries a SHA-256 digest
     * auditable in t_flow_publish.</p>
     */
    @PostMapping("/publish/{versionId}")
    public Result<Map<String, Object>> publish(@org.springframework.web.bind.annotation.PathVariable Integer versionId) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> data = snapshotLoader.publishNow(versionId);
            data.put("engineCount", snapshotHolder.getEngineCount());
            data.put("policyNodeCount", snapshotHolder.getPolicyNodeCount());
            data.put("costMs", System.currentTimeMillis() - start);
            return Result.ok(data, "Version " + versionId + " published and effective");
        } catch (Exception e) {
            log.error("Version {} publish failed", versionId, e);
            return Result.fail("Publish failed: " + e.getMessage());
        }
    }

    /**
     * Rule dry-run: evaluate the given conditions and variables one by one, returning hit details.
     *
     * <p>Used by the rule editor's "dry run" panel. Evaluation uses the production
     * {@code ConditionEvaluator}, so what the editor shows matches real engine execution;
     * pure in-memory computation, touching neither snapshot nor database.</p>
     */
    @PostMapping("/eval")
    public Result<FlatConditions.Result> eval(@RequestBody EvalReq req) {
        if (req == null) {
            return Result.ok(FlatConditions.eval(null, null, Collections.emptyMap()));
        }
        return Result.ok(FlatConditions.eval(req.conditions, req.isNon,
                req.variables == null ? Collections.emptyMap() : req.variables));
    }

    /** Snapshot status query */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engineCount", snapshotHolder.getEngineCount());
        data.put("nodeCount", snapshotHolder.getNodeCount());
        data.put("policyNodeCount", snapshotHolder.getPolicyNodeCount());
        data.put("rulePlanCount", snapshotHolder.getRulePlanCount());
        data.put("listDbCount", snapshotHolder.getListDbCount());
        data.put("listEntryCount", snapshotHolder.getListEntryCount());
        // Logical reload count (+1 per reload/reloadEngine* entry)
        data.put("reloadCount", snapshotHolder.getReloadCount());
        // Snapshot replacement count: normally one replacement per reload; greater than reloadCount implies duplicate publishes
        data.put("replaceCount", snapshotHolder.getReplaceCount());
        // Load watermark (DB read start of the rebuild): self-heal reconciliation baseline; config changes after it load next round
        data.put("loadWatermark", snapshotHolder.getLoadWatermark());
        data.put("lastReloadAt", snapshotHolder.getLastReloadAt());
        // Dropped shadow replay samples: >0 means shadow tracks saturated the queue, needs attention
        data.put("shadowDropped", orchestrator.getShadowDropped());
        // Dropped decision log entries (async persistence): >0 means DB write capacity is insufficient
        data.put("logDropped", orchestrator.getLogDropped());
        // Cumulative condition evaluation errors (fail-open observability): >0 indicates a config/data issue;
        // exceptions treated as no-hit equal pass-through for reject rules and must alert
        data.put("evalErrorCount", com.helix.engine.rule.ast.ConditionEvaluator.getEvalErrorCount());
        return Result.ok(data);
    }

    // ===== Gray release governance =====

    /**
     * Active publish tracks of a version (gray-release panel data source).
     * Each row carries weight/shadow flag/artifact digest, plus comparison against the
     * current snapshot routing (routed/routable).
     */
    @GetMapping("/publish/list")
    public Result<List<Map<String, Object>>> publishList(@RequestParam Integer versionId) {
        return Result.ok(snapshotLoader.listActives(versionId));
    }

    /**
     * Freeze the publish artifact only, without rebuilding the snapshot (mandatory step
     * before console marks the deployment).
     *
     * <p>Turns "artifact exists" into a precondition of "deployed": console must receive a
     * success response from this endpoint before setting boot_state=1, guaranteeing the
     * engine never falls back to live-DB drafts for lack of an artifact.</p>
     */
    @PostMapping("/publish/{versionId}/artifact")
    public Result<Map<String, Object>> materializeArtifact(@PathVariable Integer versionId) {
        try {
            String sha = snapshotLoader.materialize(versionId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("versionId", versionId);
            data.put("artifactSha256", sha.substring(0, Math.min(12, sha.length())));
            return Result.ok(data, "Version " + versionId + " publish artifact materialized");
        } catch (Exception e) {
            log.error("Version {} artifact materialization failed", versionId, e);
            return Result.fail("Artifact materialization failed: " + e.getMessage());
        }
    }

    /**
     * Gray release: freeze the version's current live-DB config as a <b>new</b> track,
     * coexisting with the live track and splitting traffic by weight.
     * If the original main track is still full (100), it is automatically lowered to 100-weight.
     */
    @PostMapping("/publish/{versionId}/gray")
    public Result<Map<String, Object>> publishGray(@PathVariable Integer versionId,
                                                   @RequestParam(defaultValue = "10") Integer weight) {
        try {
            return Result.ok(snapshotLoader.publishGray(versionId, weight), "Version " + versionId
                    + " gray release done, new track weight " + weight + "%");
        } catch (Exception e) {
            log.error("Version {} gray release failed", versionId, e);
            return Result.fail("Gray release failed: " + e.getMessage());
        }
    }

    /** Adjust track weight (0-100). 0 = pause the track; multiple tracks split traffic by normalized weight. */
    @PostMapping("/publish/track/{publishId}/weight")
    public Result<List<Map<String, Object>>> setTrackWeight(@PathVariable Long publishId,
                                                            @RequestParam Integer weight) {
        snapshotLoader.setWeight(publishId, weight);
        return Result.ok(snapshotLoader.listTracksOf(publishId), "Track " + publishId + " weight adjusted to " + weight);
    }

    /** Shadow mode switch: when on, evaluate and log shadow decisions only; no result, no traffic splitting. */
    @PostMapping("/publish/track/{publishId}/shadow")
    public Result<List<Map<String, Object>>> setTrackShadow(@PathVariable Long publishId,
                                                            @RequestParam(defaultValue = "true") boolean on) {
        snapshotLoader.setShadow(publishId, on);
        return Result.ok(snapshotLoader.listTracksOf(publishId),
                "Track " + publishId + (on ? " entered shadow mode" : " exited shadow mode"));
    }

    /**
     * Full switchover (second-level rollback): target track weight 100; other tracks of the
     * same version set to 0 (artifacts kept); promoting the old track again switches back instantly.
     */
    @PostMapping("/publish/track/{publishId}/promote")
    public Result<List<Map<String, Object>>> promoteTrack(@PathVariable Long publishId) {
        snapshotLoader.promote(publishId);
        return Result.ok(snapshotLoader.listTracksOf(publishId), "Track " + publishId + " took over all traffic");
    }

    /** Routing view of the version in the current snapshot (weight/shadow/routable), for verification after reload */
    @GetMapping("/routing/{versionId}")
    public Result<List<Map<String, Object>>> routing(@PathVariable Integer versionId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (com.helix.engine.config.EngineSnapshot.Channel c : snapshotHolder.get().getChannels(versionId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("publishId", c.getPublishId());
            m.put("publishSeq", c.getPublishSeq());
            m.put("trafficWeight", c.getTrafficWeight());
            m.put("shadow", c.isShadow());
            m.put("routable", c.isRoutable());
            m.put("liveFallback", c.isLiveFallback());
            m.put("nodeCount", c.getNodes().size());
            m.put("rulePlanCount", c.getRulePlanCount());
            out.add(m);
        }
        return Result.ok(out);
    }
}
