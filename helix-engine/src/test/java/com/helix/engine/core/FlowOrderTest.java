package com.helix.engine.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowOrderTest {

    private static Map<String, List<String>> adjacency(Object... pairs) {
        Map<String, List<String>> m = new HashMap<String, List<String>>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> succ = (List<String>) pairs[i + 1];
            m.put((String) pairs[i], succ);
        }
        return m;
    }


    @Test
    @DisplayName("P1 unequal-length diamond: convergence node X must run after all predecessors (B and D)")
    void diamondConvergence() {
        Map<String, List<String>> next = adjacency(
                "A", Arrays.asList("B", "C"),
                "B", Collections.singletonList("X"),
                "C", Collections.singletonList("D"),
                "D", Collections.singletonList("X"));

        List<String> order = DecisionOrchestrator.topologicalOrder(next, "A");

        assertEquals(5, order.size(), "all 5 nodes should run exactly once");
        assertEquals("A", order.get(0), "start runs first");
        assertTrue(indexOf(order, "D") < indexOf(order, "X"),
                "D must precede X (old FIFO order was A,B,C,X,D - X ran before D and read empty variables)");
        assertTrue(indexOf(order, "B") < indexOf(order, "X"), "B must also precede X");
        assertEquals(1, countOccurrences(order, "X"), "X runs exactly once");
    }

    @Test
    @DisplayName("P1 equal-length diamond (X directly depends on both branches): converges correctly too")
    void equalArmsDiamond() {
        Map<String, List<String>> next = adjacency(
                "A", Arrays.asList("B", "C"),
                "B", Collections.singletonList("X"),
                "C", Collections.singletonList("X"));

        List<String> order = DecisionOrchestrator.topologicalOrder(next, "A");
        assertEquals(4, order.size());
        assertTrue(indexOf(order, "B") < indexOf(order, "X"));
        assertTrue(indexOf(order, "C") < indexOf(order, "X"));
    }


    @Test
    @DisplayName("linear flow: topological order matches the old FIFO order (regression guard)")
    void linearFlowUnchanged() {
        Map<String, List<String>> next = adjacency(
                "A", Collections.singletonList("B"),
                "B", Collections.singletonList("C"),
                "C", Collections.singletonList("D"));

        List<String> order = DecisionOrchestrator.topologicalOrder(next, "A");
        assertEquals(Arrays.asList("A", "B", "C", "D"), order);
    }

    @Test
    @DisplayName("branch tree (no convergence): branch order insensitive, but parent must precede child")
    void branchTree() {
        Map<String, List<String>> next = adjacency(
                "A", Arrays.asList("B", "C"),
                "B", Collections.singletonList("D"),
                "C", Collections.singletonList("E"));

        List<String> order = DecisionOrchestrator.topologicalOrder(next, "A");
        assertEquals(5, order.size());
        assertEquals("A", order.get(0));
        assertTrue(indexOf(order, "A") < indexOf(order, "B"));
        assertTrue(indexOf(order, "A") < indexOf(order, "C"));
        assertTrue(indexOf(order, "B") < indexOf(order, "D"));
        assertTrue(indexOf(order, "C") < indexOf(order, "E"));
    }


    @Test
    @DisplayName("self loop (A->A): node runs once, no infinite loop")
    void selfLoop() {
        Map<String, List<String>> next = adjacency(
                "A", Arrays.asList("A", "B"),
                "B", Collections.<String>emptyList());

        List<String> order = DecisionOrchestrator.topologicalOrder(next, "A");
        assertEquals(2, order.size());
        assertEquals(1, countOccurrences(order, "A"));
        assertEquals("A", order.get(0));
    }

    @Test
    @DisplayName("dirty-data cycle (A->B->A): no node lost, fallback append in reachable order (no worse than old FIFO)")
    void dirtyCycleFallsBack() {
        Map<String, List<String>> next = adjacency(
                "A", Collections.singletonList("B"),
                "B", Arrays.asList("A", "C"));

        List<String> order = DecisionOrchestrator.topologicalOrder(next, "A");
        assertEquals(3, order.size(), "nodes on the cycle must still run via fallback, none lost");
        assertEquals("A", order.get(0));
        assertEquals(1, countOccurrences(order, "C"), "C is outside the cycle, still runs in topological order");
        assertTrue(indexOf(order, "B") < indexOf(order, "C"), "B->C dependency preserved");
    }

    @Test
    @DisplayName("dirty back edge to start (B->A): no deadlock, all nodes still run")
    void backEdgeToStart() {
        Map<String, List<String>> next = adjacency(
                "A", Collections.singletonList("B"),
                "B", Arrays.asList("C", "A"));

        List<String> order = DecisionOrchestrator.topologicalOrder(next, "A");
        assertEquals(3, order.size());
        assertEquals("A", order.get(0));
        assertTrue(indexOf(order, "B") < indexOf(order, "C"));
    }

    @Test
    @DisplayName("ghost and blank successors: blanks ignored; ghosts kept in reachable order (skipped at runtime via nodeMap, same as old FIFO)")
    void ghostAndBlankSuccessors() {
        Map<String, List<String>> next = adjacency(
                "A", Arrays.asList("B", "GHOST", ""),
                "B", Collections.<String>emptyList());

        List<String> order = DecisionOrchestrator.topologicalOrder(next, "A");
        assertEquals(Arrays.asList("A", "B", "GHOST"), order);
        assertFalse(order.contains(""), "blank successor must not enter the execution order");
    }

    @Test
    @DisplayName("duplicate edges (two A->X edges): in-degree deduped by distinct predecessors, no double execution")
    void duplicateEdges() {
        Map<String, List<String>> next = adjacency(
                "A", Arrays.asList("X", "X"),
                "X", Collections.<String>emptyList());

        List<String> order = DecisionOrchestrator.topologicalOrder(next, "A");
        assertEquals(Arrays.asList("A", "X"), order);
    }

    @Test
    @DisplayName("no successors: only start runs")
    void noSuccessors() {
        Map<String, List<String>> next = adjacency(
                "A", Collections.<String>emptyList());

        assertEquals(Collections.singletonList("A"),
                DecisionOrchestrator.topologicalOrder(next, "A"));
    }


    private static int indexOf(List<String> order, String code) {
        return order.indexOf(code);
    }

    private static int countOccurrences(List<String> order, String code) {
        return new ArrayList<String>(order).stream()
                .filter(code::equals).mapToInt(x -> 1).sum();
    }

    private static void assertFalse(boolean b, String msg) {
        if (b) {
            throw new AssertionError(msg);
        }
    }

    private static void assertTrue(boolean b, String msg) {
        if (!b) {
            throw new AssertionError(msg);
        }
    }

    private static void assertTrue(boolean b) {
        assertTrue(b, "assertion failed");
    }
}
