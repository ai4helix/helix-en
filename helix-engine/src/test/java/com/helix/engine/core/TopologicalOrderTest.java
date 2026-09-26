package com.helix.engine.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TopologicalOrderTest {

    private static Map<String, List<String>> adj(Object... kv) {
        Map<String, List<String>> m = new HashMap<String, List<String>>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) kv[i + 1];
            m.put((String) kv[i], list);
        }
        return m;
    }

    private static List<String> list(String... codes) {
        return new ArrayList<String>(Arrays.asList(codes));
    }

    private static int pos(List<String> order, String code) {
        return order.indexOf(code);
    }

    @Test
    public void testLinearChain() {
        Map<String, List<String>> m = adj("A", list("B"), "B", list("C"), "C", list());
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");
        assertEquals(Arrays.asList("A", "B", "C"), order);
    }

    @Test
    public void testUnequalLengthConvergence() {
        Map<String, List<String>> m = adj(
                "A", list("B", "C"),
                "B", list("X"),
                "C", list("D"),
                "D", list("X"),
                "X", list());
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");

        assertEquals("should contain all 5 reachable nodes", 5, order.size());
        assertEquals("start node must run first", 0, pos(order, "A"));
        assertEquals("convergence node X must run last", 4, pos(order, "X"));
        assertTrue("X must run after D on the longer branch", pos(order, "X") > pos(order, "D"));
        assertTrue("X must run after B on its branch", pos(order, "X") > pos(order, "B"));
    }

    @Test
    public void testSelfLoopNotCounted() {
        Map<String, List<String>> m = adj(
                "A", list("B"),
                "B", list("B"));
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");
        assertEquals(Arrays.asList("A", "B"), order);
    }

    @Test
    public void testStartSelfLoop() {
        Map<String, List<String>> m = adj("A", list("A"));
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");
        assertEquals(Collections.singletonList("A"), order);
    }

    @Test
    public void testBackEdgeToStartNotCounted() {
        Map<String, List<String>> m = adj(
                "A", list("X"),
                "X", list("A"));
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");
        assertEquals(Arrays.asList("A", "X"), order);
    }

    @Test
    public void testCycleFallbackAppendsRemaining() {
        Map<String, List<String>> m = adj(
                "A", list("B"),
                "B", list("C"),
                "C", list("B"));
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");

        assertEquals("nodes must not be lost even in a cycle", 3, order.size());
        assertEquals("A must run first", 0, pos(order, "A"));
        assertTrue("B should precede C (BFS fallback order)", pos(order, "B") < pos(order, "C"));
    }

    @Test
    public void testUnreachableExcluded() {
        Map<String, List<String>> m = adj(
                "A", list("B"),
                "B", list(),
                "C", list("D"),
                "D", list());
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");
        assertEquals(Arrays.asList("A", "B"), order);
        assertFalse("unreachable nodes must not appear", order.contains("C"));
        assertFalse("unreachable nodes must not appear", order.contains("D"));
    }

    @Test
    public void testDiamond() {
        Map<String, List<String>> m = adj(
                "A", list("B", "C"),
                "B", list("D"),
                "C", list("D"),
                "D", list());
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");
        assertEquals(4, order.size());
        assertEquals(0, pos(order, "A"));
        assertEquals(3, pos(order, "D"));
        assertTrue("D must run after B", pos(order, "D") > pos(order, "B"));
        assertTrue("D must run after C", pos(order, "D") > pos(order, "C"));
    }

    @Test
    public void testBlankAndNullEdgesIgnored() {
        Map<String, List<String>> m = adj(
                "A", list("", "B", null),
                "B", list());
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");
        assertEquals(Arrays.asList("A", "B"), order);
    }

    @Test
    public void testSingleNode() {
        Map<String, List<String>> m = adj("A", list());
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");
        assertEquals(Collections.singletonList("A"), order);
    }

    @Test
    public void testStartAbsentFromMap() {
        Map<String, List<String>> m = Collections.emptyMap();
        List<String> order = DecisionOrchestrator.topologicalOrder(m, "A");
        assertEquals(Collections.singletonList("A"), order);
    }
}
