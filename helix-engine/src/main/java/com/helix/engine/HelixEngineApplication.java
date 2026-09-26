package com.helix.engine;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Decision Engine (2026-09 redesign: snapshot is everything).
 *
 * <p>External contract: {@code POST /engineApi/decision},
 * {@code POST /engineApi/update} (rebuild snapshot + warm up), {@code GET /engineApi/status}.</p>
 *
 * <p>The datasource points to the dedicated {@code helix_engine} database (tables uniformly use the
 * t_ prefix, see db/CONVENTIONS.md). Configuration CRUD is owned by helix-console; after publishing
 * it calls back {@code /engineApi/update} to make the configuration take effect immediately.</p>
 *
 * <p>{@code @EnableScheduling}: provides a fallback for "post-publish callback failures" —
 * {@code SnapshotSelfHealJob} periodically reconciles the update time of t_flow_publish against
 * the current snapshot version, and automatically rebuilds when a change is detected but not yet
 * effective, preventing configuration that appears published from still running on an old snapshot.</p>
 */
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication
@MapperScan({"com.helix.engine.mapper", "com.helix.engine.result"})
public class HelixEngineApplication {

    protected static final Logger logger = LoggerFactory.getLogger(HelixEngineApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(HelixEngineApplication.class, args);
        logger.info("helix-engine started");
    }
}
