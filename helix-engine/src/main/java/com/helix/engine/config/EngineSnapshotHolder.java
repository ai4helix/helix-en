package com.helix.engine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Engine snapshot holder: {@code volatile} reference + lock-free reads, zero synchronization overhead on the decision path.
 *
 * <h3>Why volatile instead of a read-write lock</h3>
 * <p>The access pattern of this class is <b>extremely frequent reads, extremely rare writes</b>:</p>
 * <ul>
 *   <li>Reads: every decision calls {@link #get()} (decision path); concurrency grows linearly with QPS;</li>
 *   <li>Writes: only one {@link #replace} when configuration changes, usually human-triggered.</li>
 * </ul>
 * <p>A read-write lock would pin the <b>high-frequency read path</b> onto the same lock's AQS state word — every
 * decision would need {@code readLock().lock()/unlock()}, which is pure pointless contention. Since the snapshot
 * object {@link EngineSnapshot} itself is <b>immutable</b> (internal collections are all wrapped unmodifiable),
 * replacement is just a reference assignment, therefore:</p>
 * <ul>
 *   <li>{@code volatile} guarantees the <b>visibility and atomicity</b> of the reference write (reference assignment
 *       cannot tear); the reference read can be used safely outside any lock;</li>
 *   <li>No "half-new half-old" state: a reader either gets the old reference or the new one, both are complete snapshots;</li>
 *   <li>Statistic fields use {@link AtomicLong}, updated independently, no need for strong consistency with the
 *       snapshot reference; log I/O never blocks the decision read path.</li>
 * </ul>
 */
@Slf4j
@Component
public class EngineSnapshotHolder {

    /**
     * Current snapshot: replaced as a whole, read path lock-free.
     *
     * <p>{@code volatile} is the right choice here — it only carries "visibility of reference publication"
     * and introduces no mutual exclusion; the immutable snapshot guarantees that any generation read is
     * self-consistent.</p>
     */
    private volatile EngineSnapshot snapshot = empty();

    /**
     * Load watermark: the moment the current rebuild <b>started reading the DB</b> (not the moment replacement finished).
     *
     * <p>Self-heal reconciliation relies on it to decide "has the configuration change been loaded", so its semantics
     * must be a <b>lower bound of the data snapshot point</b>: changes committed after the DB read started are
     * definitely not seen by this round and must be left to the next round.</p>
     *
     * <p>The semantics must be "DB read started" rather than "replacement finished" — otherwise changes committed
     * within the window "DB read start ~ replacement finish" (the slower the rebuild, the wider the window; a full
     * list-DB scan can take seconds) would be permanently judged as loaded and never retried.</p>
     */
    private final AtomicLong loadWatermark = new AtomicLong(0);

    /** Moment replacement finished (for observation display only, not used in self-heal decisions) */
    private final AtomicLong lastReloadAt = new AtomicLong(0);

    /**
     * Snapshot <b>replacement count</b> (not "reload count").
     *
     * <p>One logical reload normally replaces once, so this value ≈ reload count;
     * if it is clearly larger than the reload count, duplicate publishing exists.
     * When observing {@link #getReloadCount()}, cross-check with {@link #getReplaceCount()}.</p>
     */
    private final AtomicLong replaceCount = new AtomicLong(0);

    /** Logical reload count (+1 at each reload/reloadEngine* call entry, distinct from replacement count) */
    private final AtomicLong reloadCount = new AtomicLong(0);

    /**
     * Atomically replace the snapshot.
     *
     * <p>Replacement is only a reference assignment, lock-free and non-blocking; the log is printed <b>after the
     * swap completes</b>, occupying no critical section, so log I/O does not affect the decision read path.</p>
     *
     * @param snapshot      new snapshot
     * @param loadStartAt   moment this rebuild started reading the DB (watermark, see {@link #getLoadWatermark()})
     */
    public void replace(EngineSnapshot snapshot, long loadStartAt) {
        this.snapshot = snapshot;
        // The watermark increases monotonically (take max): semantics is "loaded up to which point", must only move forward, never backward.
        // Taking min would leave the watermark stuck at the moment of the first load, making the self-heal criterion
        // configChangeAt > watermark always true, triggering a full rebuild on every reconciliation round.
        //
        // Taking max is also correct under concurrent interleaving: if rounds A and B run in parallel, the one with the
        // earlier DB read start (A) publishes first and the later one (B) publishes second, the watermark advances to B's
        // start; changes committed afterwards are necessarily left to the next round, so nothing is missed
        // (B's DB read range only covers up to B's start).
        long candidate = loadStartAt > 0 ? loadStartAt : System.currentTimeMillis();
        loadWatermark.accumulateAndGet(candidate, Math::max);
        this.lastReloadAt.set(System.currentTimeMillis());
        this.replaceCount.incrementAndGet();
        log.info("Engine snapshot replaced: engines {}, nodes {}, policy nodes {}, rule plans {} (load watermark {}, replacement #{} )",
                snapshot.getEngineCount(), snapshot.getNodeCount(),
                snapshot.getPolicyNodeCount(), snapshot.getRulePlanCount(),
                java.time.Instant.ofEpochMilli(loadWatermark.get()), replaceCount.get());
    }

    // All call sites must explicitly pass this round's DB read start loadStartAt:
    // using "replacement finished" as the watermark would miss changes committed within the "DB read start ~ replacement finish" window.

    /**
     * Get the current snapshot (lock-free).
     *
     * <p>The returned reference can be used safely at any time — the snapshot is immutable;
     * subsequent {@link #replace} only switches the reference and never modifies existing instances.</p>
     */
    public EngineSnapshot get() {
        return snapshot;
    }

    public int getEngineCount() {
        return snapshot.getEngineCount();
    }

    public int getNodeCount() {
        return snapshot.getNodeCount();
    }

    public int getPolicyNodeCount() {
        return snapshot.getPolicyNodeCount();
    }

    public int getRulePlanCount() {
        return snapshot.getRulePlanCount();
    }

    /** Number of list DBs with loaded list data */
    public int getListDbCount() {
        return snapshot.getListDbCount();
    }

    /** Total number of valid list entries */
    public int getListEntryCount() {
        return snapshot.getListEntryCount();
    }

    public long getLastReloadAt() {
        return lastReloadAt.get();
    }

    /**
     * Load watermark (millisecond timestamp): the moment this rebuild started reading the DB.
     * Self-heal reconciliation uses "config change time &gt; watermark" to decide whether a rebuild is needed.
     */
    public long getLoadWatermark() {
        return loadWatermark.get();
    }

    /** Logical reload count (incremented by the Loader at each reload entry) */
    public long getReloadCount() {
        return reloadCount.get();
    }

    /** Snapshot replacement count (one logical reload normally +1; larger than reload count means duplicate publishing) */
    public long getReplaceCount() {
        return replaceCount.get();
    }

    /** Registered by the Loader as one logical reload at the reload entry */
    public void countReload() {
        reloadCount.incrementAndGet();
    }

    public boolean isEmpty() {
        return snapshot.getEngineCount() == 0;
    }

    private static EngineSnapshot empty() {
        return new EngineSnapshot(Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap());
    }
}
