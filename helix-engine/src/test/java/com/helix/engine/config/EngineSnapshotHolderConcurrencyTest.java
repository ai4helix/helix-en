package com.helix.engine.config;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EngineSnapshotHolderConcurrencyTest {

    private EngineSnapshot snapshotWith(int nodeCount) {
        Map<Integer, Map<String, com.helix.engine.entity.engine.model.EngineNode>> nodes =
                new HashMap<Integer, Map<String, com.helix.engine.entity.engine.model.EngineNode>>();
        Map<String, com.helix.engine.entity.engine.model.EngineNode> inner =
                new HashMap<String, com.helix.engine.entity.engine.model.EngineNode>();
        for (int i = 0; i < nodeCount; i++) {
            com.helix.engine.entity.engine.model.EngineNode n =
                    new com.helix.engine.entity.engine.model.EngineNode();
            n.setNodeCode("N" + i);
            inner.put("N" + i, n);
        }
        nodes.put(1, inner);
        return new EngineSnapshot(Collections.<String, com.helix.engine.entity.engine.model.Engine>emptyMap(),
                Collections.<Integer, com.helix.engine.entity.engine.model.EngineVersion>emptyMap(),
                nodes,
                Collections.<Integer, Map<String, EngineSnapshot.PolicyConfig>>emptyMap(),
                Collections.<Integer, Map<String, EngineSnapshot.ScorecardConfig>>emptyMap(),
                Collections.<Integer, Map<String, String>>emptyMap());
    }

    @Test
    public void testConcurrentReadWrite() throws Exception {
        final EngineSnapshotHolder holder = new EngineSnapshotHolder();
        final int readers = 8;
        final int writes = 200;
        final int durationMs = 1500;

        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicInteger errors = new AtomicInteger(0);
        final AtomicLong reads = new AtomicLong(0);
        final CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);

        for (int i = 0; i < readers; i++) {
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        while (!stop.get()) {
                            EngineSnapshot s = holder.get();
                            if (s == null) {
                                errors.incrementAndGet();
                                continue;
                            }
                            s.getNodeCount();
                            s.getPolicyNodeCount();
                            s.getRulePlanCount();
                            reads.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                    }
                }
            });
        }

        pool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    for (int i = 0; i < writes && !stop.get(); i++) {
                        holder.replace(snapshotWith(i % 20), System.currentTimeMillis());
                        Thread.sleep(2);
                    }
                } catch (Throwable t) {
                    errors.incrementAndGet();
                }
            }
        });

        start.countDown();
        Thread.sleep(durationMs);
        stop.set(true);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals("exceptions during concurrent read/write", 0, errors.get());
        assertTrue("too few reads, test did not run effectively: " + reads.get(), reads.get() > 1000);
        System.out.println("Concurrent read/write done: " + reads.get() + " reads, " + holder.getReloadCount() + " replaces, " + errors.get() + " errors");
    }

    @Test
    public void testReplaceAtomicity() throws Exception {
        final EngineSnapshotHolder holder = new EngineSnapshotHolder();
        final EngineSnapshot a = snapshotWith(3);
        final EngineSnapshot b = snapshotWith(7);
        holder.replace(a, System.currentTimeMillis());

        final AtomicInteger mismatch = new AtomicInteger(0);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);

        for (int i = 0; i < 2; i++) {
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        while (!stop.get()) {
                            EngineSnapshot s = holder.get();
                            int n = s.getNodeCount();
                            if (n != 3 && n != 7) {
                                mismatch.incrementAndGet();
                            }
                        }
                    } catch (Throwable t) {
                        mismatch.incrementAndGet();
                    }
                }
            });
        }
        pool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    for (int i = 0; i < 500 && !stop.get(); i++) {
                        holder.replace(i % 2 == 0 ? a : b, System.currentTimeMillis());
                    }
                } catch (Throwable t) {
                    mismatch.incrementAndGet();
                }
            }
        });

        start.countDown();
        Thread.sleep(1200);
        stop.set(true);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals("read a snapshot that is neither A nor B (intermediate state exists)", 0, mismatch.get());
    }

    @Test
    public void testReloadCountAccuracy() throws Exception {
        final EngineSnapshotHolder holder = new EngineSnapshotHolder();
        final int threads = 6;
        final int perThread = 50;
        final EngineSnapshot s = snapshotWith(5);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        for (int j = 0; j < perThread; j++) {
                            holder.replace(s, System.currentTimeMillis());
                        }
                    } catch (InterruptedException ignore) {
                    }
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals("replaceCount does not match actual replacements (lost update exists)",
                threads * perThread, holder.getReplaceCount());
        assertSame("final snapshot should be the last written instance", s, holder.get());
        assertNotNull(holder.getLastReloadAt());
    }
}
