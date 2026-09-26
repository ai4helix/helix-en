package com.helix.engine.config;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EngineSnapshotGrayRoutingTest {

    private static final int VERSION_ID = 92;

    private EngineSnapshot snapshotOf(EngineSnapshot.Channel... channels) {
        Map<Integer, List<EngineSnapshot.Channel>> channelsByVersion = new LinkedHashMap<>();
        List<EngineSnapshot.Channel> list = new ArrayList<>();
        Collections.addAll(list, channels);
        channelsByVersion.put(VERSION_ID, list);
        return new EngineSnapshot(Collections.emptyMap(), Collections.emptyMap(), channelsByVersion);
    }

    private EngineSnapshot.Channel channel(Long publishId, Integer seq, int weight, boolean shadow) {
        return new EngineSnapshot.Channel(VERSION_ID, publishId, seq, weight, shadow,
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap());
    }

    @Test
    public void testWeightedRoutingDistribution() {
        EngineSnapshot snapshot = snapshotOf(
                channel(1L, 1, 90, false),
                channel(2L, 2, 10, false));

        Random random = new Random(42);
        int mainHits = 0;
        int grayHits = 0;
        int total = 100_000;
        for (int i = 0; i < total; i++) {
            EngineSnapshot.Channel c = snapshot.route(VERSION_ID, random.nextDouble());
            if (c.getPublishId() == 1L) {
                mainHits++;
            } else if (c.getPublishId() == 2L) {
                grayHits++;
            }
        }
        assertEquals(total, mainHits + grayHits);
        double mainRatio = mainHits / (double) total;
        assertTrue("main track ratio should be near 0.9, actual " + mainRatio, mainRatio > 0.88 && mainRatio < 0.92);
        double grayRatio = grayHits / (double) total;
        assertTrue("gray track ratio should be near 0.1, actual " + grayRatio, grayRatio > 0.08 && grayRatio < 0.12);
    }

    @Test
    public void testShadowChannelNeverRouted() {
        EngineSnapshot snapshot = snapshotOf(
                channel(1L, 1, 100, false),
                channel(3L, 2, 100, true));

        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            EngineSnapshot.Channel c = snapshot.route(VERSION_ID, random.nextDouble());
            assertSame("traffic must land on the main track only", 1L, c.getPublishId());
        }
    }

    @Test
    public void testAllZeroWeightsFallbackToPrimary() {
        EngineSnapshot snapshot = snapshotOf(
                channel(9L, 9, 0, true),
                channel(1L, 1, 0, false),
                channel(2L, 2, 0, false));

        EngineSnapshot.Channel routed = snapshot.route(VERSION_ID, 0.5);
        assertSame("falls back to primary when no routable channel (first in order = newest non-shadow)", 2L, routed.getPublishId());
        EngineSnapshot.Channel primary = snapshot.primary(VERSION_ID);
        assertSame(2L, primary.getPublishId());
        assertSame("shadow channel must be ordered last", 9L, snapshot.getChannels(VERSION_ID).get(2).getPublishId());
    }

    @Test
    public void testSingleFullChannel() {
        EngineSnapshot snapshot = snapshotOf(channel(1L, 1, 100, false));
        assertSame(1L, snapshot.route(VERSION_ID, 0.0).getPublishId());
        assertSame(1L, snapshot.route(VERSION_ID, 0.999999).getPublishId());
    }

    @Test
    public void testLegacyConstructorWrapsSingleChannel() {
        Map<Integer, Map<String, EngineSnapshot.PolicyConfig>> policies = new LinkedHashMap<>();
        policies.put(VERSION_ID, new LinkedHashMap<String, EngineSnapshot.PolicyConfig>());
        EngineSnapshot snapshot = new EngineSnapshot(Collections.emptyMap(), Collections.emptyMap(),
                new LinkedHashMap<Integer, Map<String, com.helix.engine.entity.engine.model.EngineNode>>(),
                policies,
                new LinkedHashMap<Integer, Map<String, EngineSnapshot.ScorecardConfig>>(),
                new LinkedHashMap<Integer, Map<String, String>>());

        assertEquals(1, snapshot.getChannels(VERSION_ID).size());
        EngineSnapshot.Channel ch = snapshot.primary(VERSION_ID);
        assertNull(ch.getPublishId());
        assertEquals(100, ch.getTrafficWeight());
        assertTrue(ch.isRoutable());
        assertTrue(ch.isLiveFallback());
        assertSame(ch, snapshot.route(VERSION_ID, 0.7));
    }

    @Test
    public void testUnknownVersionReturnsNull() {
        EngineSnapshot snapshot = snapshotOf(channel(1L, 1, 100, false));
        assertNull(snapshot.route(999, 0.5));
    }

    @Test
    public void testCountersAggregateAcrossChannels() {
        EngineSnapshot snapshot = snapshotOf(
                channel(1L, 1, 90, false),
                channel(2L, 2, 10, false),
                channel(3L, 3, 0, true));
        assertEquals(3, snapshot.getChannels(VERSION_ID).size());
        assertEquals(0, snapshot.getNodeCount());
        assertEquals(0, snapshot.getRulePlanCount());
    }

    @Test
    public void testNextOfEdgesOverrideLegacyNextNodes() {
        com.helix.engine.entity.engine.model.EngineNode a = new com.helix.engine.entity.engine.model.EngineNode();
        a.setNodeCode("A");
        a.setNextNodes("B, C, null");
        com.helix.engine.entity.engine.model.EngineNode b = new com.helix.engine.entity.engine.model.EngineNode();
        b.setNodeCode("B");

        java.util.Map<String, java.util.List<String>> edges = new java.util.LinkedHashMap<>();
        edges.put("A", java.util.Arrays.asList("C"));

        EngineSnapshot.Channel withEdges = new EngineSnapshot.Channel(VERSION_ID, 1L, 1, 100, false,
                new java.util.LinkedHashMap<>(java.util.Collections.singletonMap("A", a)),
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), edges);
        assertEquals("explicit edges win (no union with comma string)", java.util.Collections.singletonList("C"),
                withEdges.nextOf("A"));
        assertEquals(0, withEdges.nextOf("B").size());

        EngineSnapshot.Channel legacy = new EngineSnapshot.Channel(VERSION_ID, 1L, 1, 100, false,
                new java.util.LinkedHashMap<>(java.util.Collections.singletonMap("A", a)),
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap());
        assertEquals("derived from nextNodes when no edges (trim + drop null literals)",
                java.util.Arrays.asList("B", "C"), legacy.nextOf("A"));
    }
}
