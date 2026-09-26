package com.helix.engine.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.engine.entity.FlowPublishEntity;
import com.helix.engine.entity.engine.model.EngineVersion;
import com.helix.engine.mapper.EngineVersionMapper;
import com.helix.engine.mapper.FlowPublishMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Snapshot self-heal job: solves "publish callback failure leaving configuration ineffective for a long time".
 *
 * <h3>Background</h3>
 * <p>Configuration takes effect via an active callback from console to {@code /engineApi/publish/{id}} or
 * {@code /engineApi/update}; if the callback fails and there is no reconciliation fallback, console may show
 * "published" while the runtime keeps using the old snapshot.</p>
 *
 * <h3>Reconciliation strategy</h3>
 * <p>Periodically compares "latest configuration-side change time" against "current snapshot load time", and
 * triggers a rebuild only when configuration <b>has indeed been updated</b> (not a blind scheduled full reload,
 * avoiding repeated rescans of large tables such as list DBs):</p>
 * <ul>
 *   <li>Take the max {@code updated_time} of effective publish artifacts in {@code t_flow_publish} (status=1, deleted=0);</li>
 *   <li>Take the max {@code updated_time} of deployed versions in {@code t_engine_version} (boot_state=1);</li>
 *   <li>If either is greater than the snapshot load time → there is an ineffective configuration change → rebuild the snapshot.</li>
 * </ul>
 *
 * <p>To disable: {@code helix.snapshot.self-heal.enabled=false}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "helix.snapshot.self-heal.enabled", havingValue = "true",
        matchIfMissing = true)
public class SnapshotSelfHealJob {

    private final FlowPublishMapper flowPublishMapper;
    private final EngineVersionMapper engineVersionMapper;
    private final EngineSnapshotLoader snapshotLoader;
    private final EngineSnapshotHolder snapshotHolder;

    /**
     * Scheduled reconciliation: rebuild the snapshot when a configuration change is detected but not loaded.
     *
     * <p>Execution failure only warns and never throws, preventing the scheduler from marking the job as failed
     * and interrupting subsequent scheduling.</p>
     */
    @Scheduled(fixedDelayString = "${helix.snapshot.self-heal.interval-ms:60000}",
            initialDelayString = "${helix.snapshot.self-heal.initial-delay-ms:60000}")
    public void heal() {
        try {
            LocalDateTime lastConfigChange = latestConfigChangeTime();
            if (lastConfigChange == null) {
                return;
            }
            // Use the "load watermark" (the moment the rebuild started reading the DB) rather than "replacement finished":
            // the latter would permanently judge changes committed within the "DB read start ~ replacement finish" window as loaded.
            long watermark = snapshotHolder.getLoadWatermark();
            if (watermark <= 0) {
                // Snapshot never loaded successfully (startup failure): try rebuilding directly
                log.warn("Snapshot not yet loaded successfully, triggering self-heal rebuild");
                snapshotLoader.reload();
                return;
            }
            long configChangeAt = lastConfigChange
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (configChangeAt > watermark) {
                log.warn("Detected configuration change not yet effective (config updated at {}, load watermark {}), triggering self-heal rebuild",
                        lastConfigChange, toLocalDateTime(watermark));
                snapshotLoader.reload();
            }
        } catch (Exception e) {
            log.error("Snapshot self-heal reconciliation failed (current snapshot keeps serving; will retry next round)", e);
        }
    }

    /**
     * Latest configuration-side change time: the max of the effective publish artifact's updated_time and the
     * deployed version's created_time.
     *
     * <p>t_engine_version has no updated_time column, so created_time (version creation time) is used;
     * the publish action itself writes t_flow_publish.updated_time, which is a more sensitive signal.</p>
     */
    private LocalDateTime latestConfigChangeTime() {
        LocalDateTime publishAt = null;
        LocalDateTime versionAt = null;
        try {
            FlowPublishEntity latestPublish = flowPublishMapper.selectOne(
                    new LambdaQueryWrapper<FlowPublishEntity>()
                            .eq(FlowPublishEntity::getStatus, 1)
                            .eq(FlowPublishEntity::getDeleted, 0)
                            .orderByDesc(FlowPublishEntity::getUpdatedTime)
                            .last("LIMIT 1"));
            publishAt = latestPublish == null ? null : latestPublish.getUpdatedTime();
        } catch (Exception e) {
            log.debug("Failed to query publish artifact update time: {}", e.getMessage());
        }
        try {
            EngineVersion latestVersion = engineVersionMapper.selectOne(
                    new LambdaQueryWrapper<EngineVersion>()
                            .eq(EngineVersion::getBootState, (short) 1)
                            .orderByDesc(EngineVersion::getCreatedTime)
                            .last("LIMIT 1"));
            versionAt = latestVersion == null ? null : latestVersion.getCreatedTime();
        } catch (Exception e) {
            log.debug("Failed to query deployed version creation time: {}", e.getMessage());
        }
        if (publishAt == null) {
            return versionAt;
        }
        if (versionAt == null) {
            return publishAt;
        }
        return publishAt.isAfter(versionAt) ? publishAt : versionAt;
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis),
                ZoneId.systemDefault());
    }
}
