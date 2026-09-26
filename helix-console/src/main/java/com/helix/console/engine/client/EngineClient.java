package com.helix.console.engine.client;

import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.facade.engine.EngineApiReq;
import com.helix.facade.engine.EngineApiRsp;
import com.helix.facade.engine.EngineService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * helix-engine executor client (facade contract adapter layer).
 *
 * <p>Boundary of responsibility: helix-console only does configuration management; the <b>execution</b> of
 * decisions is uniformly handled by helix-engine. Therefore for scenarios that require real engine execution,
 * such as "batch test" and "trial run", this module no longer ships its own execution kernel but forwards
 * to helix-engine's {@code /engineApi/*} APIs.</p>
 *
 * <p>The transport layer uses helix-facade's {@link EngineService} (Feign,
 * nacos service name addressing + Ribbon load balancing). This class acts as the adapter layer:
 * envelope code checking, {@code FeignException -> BizException} translation, and limited
 * backoff retries based on "rebuild is idempotent so it is retryable". This guarantees the execution result
 * seen on the configuration side is fully consistent with the actual production execution result, avoiding
 * semantic drift between two implementations.</p>
 */
@Slf4j
@Component
public class EngineClient {

    private final EngineService engineService;

    public EngineClient(EngineService engineService) {
        this.engineService = engineService;
    }

    /**
     * Rule trial run: forwards to helix-engine's per-condition evaluation API.
     *
     * <p>Difference from {@link #decision}: no snapshot or decision flow is used; a single rule's conditions are
     * evaluated purely in memory, returning per-condition hit details (expected value / actual value / hit or not)
     * for the editor's "trial run" panel.</p>
     *
     * @param conditions flat condition list (field / operator / value / logical)
     * @param isNon      global negation flag
     * @param variables  variable table
     * @return evaluation result (matched / empty / inverted / leaves)
     */
    public Map<String, Object> eval(List<Map<String, Object>> conditions,
                                    Integer isNon, Map<String, Object> variables) {
        Map<String, Object> req = new HashMap<>();
        req.put("conditions", conditions == null ? new ArrayList<Map<String, Object>>() : conditions);
        req.put("isNon", isNon == null ? Integer.valueOf(0) : isNon);
        req.put("variables", variables == null ? new HashMap<String, Object>() : variables);

        Map<String, Object> envelope = call(() -> engineService.eval(req), "Rule trial run");
        Object data = envelope.get("data");
        return data instanceof Map ? (Map<String, Object>) data : new HashMap<String, Object>();
    }

    /**
     * Call helix-engine to execute one decision (runs the currently effective version).
     *
     * @param engineCode engine code
     * @param pid        application identifier
     * @param input      business input, keyed by field English name
     * @return execution result; throws {@link BizException} when the call fails
     */
    public EngineApiRsp decision(String engineCode, String pid, Map<String, Object> input) {
        return decision(engineCode, pid, null, input);
    }

    /**
     * Call helix-engine to execute one decision.
     *
     * @param engineCode engine code
     * @param pid        application identifier
     * @param versionId  force a specific version (trial calculation/replay scenarios; null means running the currently effective version)
     * @param input      business input, keyed by field English name
     * @return execution result (Result envelope, {@link EngineApiRsp#getData()} is the decision payload);
     *         throws {@link BizException} when the call fails
     */
    public EngineApiRsp decision(String engineCode, String pid, Integer versionId, Map<String, Object> input) {
        if (StringUtils.isBlank(engineCode)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Engine code must not be empty");
        }

        EngineApiReq req = new EngineApiReq();
        req.setCode(engineCode);
        req.setPid(pid);
        req.setVersionId(versionId);
        req.setData(input == null ? new HashMap<>() : input);

        EngineApiRsp body = callRsp(() -> engineService.callEngine(req), "Decision execution");
        if (!body.isSuccess()) {
            throw BizException.of(ResultCode.SYSTEM_ERROR,
                    StringUtils.defaultIfBlank(body.getMessage(), "Execution failed"));
        }
        return body;
    }

    /** Retry count for snapshot rebuild notifications (including the first attempt), 3 attempts in total */
    private static final int RELOAD_MAX_ATTEMPTS = 3;

    /** Base interval between retries (milliseconds), linear backoff by attempt count */
    private static final long RELOAD_RETRY_BASE_MS = 500L;

    /**
     * Notify the engine to rebuild its snapshot (POST /engineApi/update).
     *
     * <p>Used for immediate effect after non-versioned configuration changes such as list DBs. The rebuild action
     * is <b>idempotent</b> (the engine fully rebuilds and atomically swaps the snapshot), so it is safe to retry:
     * transient failures caused by network jitter / engine restart windows get better delivery rates through
     * limited backoff retries.</p>
     *
     * <p>If it still fails, an exception is thrown for the caller to decide whether to block the business; even if
     * the caller only logs a warning, the engine-side {@code SnapshotSelfHealJob} will rebuild as a fallback in
     * the next reconciliation round.</p>
     */
    public void refreshSnapshot() {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= RELOAD_MAX_ATTEMPTS; attempt++) {
            try {
                call(engineService::updateSnapshot, "Rebuild engine snapshot");
                if (attempt > 1) {
                    log.info("Engine snapshot rebuild notification succeeded on attempt {}", attempt);
                }
                return;
            } catch (RuntimeException e) {
                last = e;
                if (attempt < RELOAD_MAX_ATTEMPTS) {
                    long backoff = RELOAD_RETRY_BASE_MS * attempt;
                    log.warn("Engine snapshot rebuild notification failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, RELOAD_MAX_ATTEMPTS, backoff, e.getMessage());
                    sleepQuietly(backoff);
                }
            }
        }
        log.error("Engine snapshot rebuild notification still failed after {} retries, waiting for the engine-side scheduled reconciliation fallback", RELOAD_MAX_ATTEMPTS);
        throw last;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Per-engine incremental reload (including list refresh): rebuilds only the given engines, other engines reuse their snapshots.
     *
     * <p>Used for list DB changes - only notifies engines "referencing that list DB", avoiding one tenant's list change in a SaaS
     * environment triggering a rebuild of every engine across the platform. Degrades to list-only refresh when the engine list is empty.</p>
     *
     * <p>Has the same backoff retry as {@link #refreshSnapshot()} (rebuild is idempotent).</p>
     */
    public void refreshEngines(List<String> engineCodes) {
        Map<String, Object> body = new HashMap<>();
        body.put("engineCodes", engineCodes == null ? Collections.emptyList() : engineCodes);
        body.put("refreshLists", true);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= RELOAD_MAX_ATTEMPTS; attempt++) {
            try {
                call(() -> engineService.reloadEngines(body), "Per-engine incremental reload");
                if (attempt > 1) {
                    log.info("Engine incremental reload notification succeeded on attempt {} ({} engines)", attempt, engineCodes);
                }
                return;
            } catch (RuntimeException e) {
                last = e;
                if (attempt < RELOAD_MAX_ATTEMPTS) {
                    long backoff = RELOAD_RETRY_BASE_MS * attempt;
                    log.warn("Engine incremental reload notification failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, RELOAD_MAX_ATTEMPTS, backoff, e.getMessage());
                    sleepQuietly(backoff);
                }
            }
        }
        log.error("Engine incremental reload notification still failed after {} retries, waiting for the engine-side scheduled reconciliation fallback", RELOAD_MAX_ATTEMPTS);
        throw last;
    }

    // ===== Publish chain (used by DecisionFlowServiceImpl) =====

    /**
     * Only solidify publish artifacts without rebuilding the snapshot (mandatory step before console marks deployment).
     */
    public void publishArtifact(Integer versionId) {
        call(() -> engineService.publishArtifact(versionId), "Publish artifact solidification");
    }

    /**
     * Publish the given version and immediately rebuild the snapshot (publish takes effect immediately).
     */
    public void publishDeploy(Integer versionId) {
        call(() -> engineService.publish(versionId), "Version publish");
    }

    // ===== Gray release governance forwarding =====

    /** List of active publish tracks of a version (DB view, including routed/routable comparison) */
    public List<Map<String, Object>> listPublishTracks(Integer versionId) {
        return extractList(call(() -> engineService.listPublishTracks(versionId), "Query publish tracks"));
    }

    /** Routing view of the current snapshot (weight/shadow/routable), for verification after reload */
    public List<Map<String, Object>> routing(Integer versionId) {
        return extractList(call(() -> engineService.routing(versionId), "Query routing view"));
    }

    /** Gray release: solidify the current configuration into a new track, coexists with production and splits traffic by weight */
    @SuppressWarnings("unchecked")
    public Map<String, Object> grayPublish(Integer versionId, Integer weight) {
        Object data = call(() -> engineService.grayPublish(versionId, weight), "Gray release").get("data");
        return data instanceof Map ? (Map<String, Object>) data : new HashMap<>();
    }

    /** Adjust track weight (0 = pause the track) */
    public List<Map<String, Object>> setTrackWeight(Long publishId, Integer weight) {
        return extractList(call(() -> engineService.setTrackWeight(publishId, weight), "Adjust track weight"));
    }

    /** Shadow mode switch */
    public List<Map<String, Object>> setTrackShadow(Long publishId, boolean on) {
        return extractList(call(() -> engineService.setTrackShadow(publishId, on), "Shadow mode switch"));
    }

    /** Full switchover (instant rollback): target track 100, others of the same version set to 0 (artifacts kept) */
    public List<Map<String, Object>> promoteTrack(Long publishId) {
        return extractList(call(() -> engineService.promoteTrack(publishId), "Full switchover"));
    }

    // ===== Common envelope parsing and exception translation =====

    /**
     * Invoke the facade client and parse the {@code Result} envelope: code=0 returns the raw envelope,
     * otherwise a business exception is thrown; Feign transport exceptions are uniformly translated to
     * "unable to connect to the execution service".
     */
    private Map<String, Object> call(Supplier<Map<String, Object>> invocation, String action) {
        try {
            Map<String, Object> envelope = invocation.get();
            if (envelope == null) {
                throw BizException.of(ResultCode.SYSTEM_ERROR, "No response from the execution service: " + action);
            }
            Object code = envelope.get("code");
            if (!(code instanceof Number) || ((Number) code).intValue() != 0) {
                throw BizException.of(ResultCode.SYSTEM_ERROR,
                        action + " failed: " + String.valueOf(envelope.getOrDefault("message", "Unknown error")));
            }
            return envelope;
        } catch (feign.FeignException e) {
            log.error("Failed to call helix-engine, action={}", action, e);
            throw BizException.of(ResultCode.SYSTEM_ERROR,
                    "Unable to connect to the execution service (helix-engine): " + e.getMessage());
        }
    }

    /** Strongly-typed variant: {@link #callEngine} uses the contract DTO with only transport exception translation */
    private EngineApiRsp callRsp(Supplier<EngineApiRsp> invocation, String action) {
        try {
            EngineApiRsp body = invocation.get();
            if (body == null) {
                throw BizException.of(ResultCode.SYSTEM_ERROR, "No response from the execution service: " + action);
            }
            return body;
        } catch (feign.FeignException e) {
            log.error("Failed to call helix-engine, action={}", action, e);
            throw BizException.of(ResultCode.SYSTEM_ERROR,
                    "Unable to connect to the execution service (helix-engine): " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Map<String, Object> envelope) {
        Object data = envelope.get("data");
        return data instanceof List ? (List<Map<String, Object>>) data : new ArrayList<>();
    }
}
