package com.helix.engine.config;

import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.rule.ast.Condition;
import com.helix.engine.rule.ast.ConditionEvaluator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InlineDecisionCompileTest {

    private static EngineNode node(String nodeJson) {
        EngineNode n = new EngineNode();
        n.setNodeId(1);
        n.setNodeCode("ND_INLINE");
        n.setNodeName("Inline decision");
        n.setNodeType(9);
        n.setNodeJson(nodeJson);
        return n;
    }

    private static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new HashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }


    @Test
    public void mixedSignIsLeftAssociative() {
        EngineNode n = node("{\"conditions\":[{\"result\":\"R\",\"formula\":["
                + "{\"field_code\":\"fA\",\"operator\":\">\",\"result\":\"0\",\"sign\":\"and\"},"
                + "{\"field_code\":\"fB\",\"operator\":\">\",\"result\":\"0\",\"sign\":\"and\"},"
                + "{\"field_code\":\"fC\",\"operator\":\">\",\"result\":\"0\",\"sign\":\"or\"},"
                + "{\"field_code\":\"fD\",\"operator\":\">\",\"result\":\"0\",\"sign\":\"and\"}]}]}");
        EngineSnapshot.DecisionTableConfig dt = EngineSnapshotLoader.compileInlineDecision(n);

        assertTrue(dt != null && dt.getRows().size() == 1);
        Condition ast = dt.getRows().get(0).getCondition();

        assertEquals(false, ConditionEvaluator.match(ast, vars("fA", 1, "fB", 1, "fC", 0, "fD", 0)),
                "a and b or c and d must be left-associative as ((a&&b)||c)&&d");
        assertEquals(false, ConditionEvaluator.match(ast, vars("fA", 1, "fB", 1, "fC", 1, "fD", 0)));
        // T,T,T,T → ((T∧T)∨T)∧T = T
        assertEquals(true, ConditionEvaluator.match(ast, vars("fA", 1, "fB", 1, "fC", 1, "fD", 1)));
    }

    @Test
    public void allAndCollapses() {
        EngineNode n = node("{\"conditions\":[{\"result\":\"R\",\"formula\":["
                + "{\"field_code\":\"fA\",\"operator\":\">\",\"result\":\"0\",\"sign\":\"and\"},"
                + "{\"field_code\":\"fB\",\"operator\":\"<\",\"result\":\"100\",\"sign\":\"and\"}]}]}");
        EngineSnapshot.DecisionTableConfig dt = EngineSnapshotLoader.compileInlineDecision(n);
        Condition ast = dt.getRows().get(0).getCondition();
        assertEquals(true, ConditionEvaluator.match(ast, vars("fA", 50, "fB", 50)));
        assertEquals(false, ConditionEvaluator.match(ast, vars("fA", 50, "fB", 500)));
    }


    @Test
    public void rangeIsClosedInterval() {
        EngineNode n = node("{\"input\":[{\"field_code\":\"f_AGE\"}],"
                + "\"conditions\":["
                + "{\"result\":\"Young\",\"min\":\"18\",\"max\":\"60\"},"
                + "{\"result\":\"Default\"}]}");
        EngineSnapshot.DecisionTableConfig dt = EngineSnapshotLoader.compileInlineDecision(n);

        assertEquals(2, dt.getRows().size());
        Condition range = dt.getRows().get(0).getCondition();
        assertEquals(true, ConditionEvaluator.match(range, vars("f_AGE", 18)), "lower bound inclusive");
        assertEquals(true, ConditionEvaluator.match(range, vars("f_AGE", 60)), "upper bound inclusive");
        assertEquals(false, ConditionEvaluator.match(range, vars("f_AGE", 17)));
        assertEquals(false, ConditionEvaluator.match(range, vars("f_AGE", 61)));
        assertEquals(false, ConditionEvaluator.match(range, vars("f_AGE", "abc")),
                "non-numeric never hits per legacy semantics (evalRange toDoubleOrNull failure = false)");
    }


    @Test
    public void brokenRowSkippedOthersRemain() {
        EngineNode n = node("{\"conditions\":["
                + "{\"result\":\"R1\",\"formula\":[{\"field_code\":\"f_A\",\"operator\":\"WRONG\",\"result\":\"1\"}]},"
                + "{\"result\":\"R2\",\"formula\":[{\"field_code\":\"f_A\",\"operator\":\">\",\"result\":\"0\"}]}]}");
        EngineSnapshot.DecisionTableConfig dt = EngineSnapshotLoader.compileInlineDecision(n);

        assertEquals(1, dt.getRows().size());
        assertEquals("R2", dt.getRows().get(0).getResultValue());
        assertEquals(2, dt.getRows().get(0).getRowNo().intValue(), "row 2 is the one kept");
    }

    @Test
    public void allBrokenReturnsNull() {
        EngineNode n = node("{\"conditions\":["
                + "{\"result\":\"R1\",\"formula\":[{\"field_code\":\"f_A\",\"operator\":\"WRONG\",\"result\":\"1\"}]}]}");
        assertNull(EngineSnapshotLoader.compileInlineDecision(n),
                "all rows broken - null, executor treats as unconfigured (same as legacy: no output)");
    }


    @Test
    public void nullSemanticsAlignedWithV1() {
        EngineNode n = node("{\"conditions\":[{\"result\":\"R\",\"formula\":["
                + "{\"field_code\":\"f_MISSING\",\"operator\":\"!=\",\"result\":\"x\",\"sign\":\"and\"}]}]}");
        EngineSnapshot.DecisionTableConfig dt = EngineSnapshotLoader.compileInlineDecision(n);
        Condition ast = dt.getRows().get(0).getCondition();

        assertEquals(true, ConditionEvaluator.match(ast, vars()),
                "missing variable != x is always true (v1 semantics)");
    }

    @Test
    public void inOperatorMultiValue() {
        EngineNode n = node("{\"conditions\":[{\"result\":\"R\",\"formula\":["
                +                 "{\"field_code\":\"f_CITY\",\"operator\":\"in\",\"result\":\"Beijing,Shanghai\",\"sign\":\"and\"}]}]}");
        EngineSnapshot.DecisionTableConfig dt = EngineSnapshotLoader.compileInlineDecision(n);
        Condition ast = dt.getRows().get(0).getCondition();
        assertEquals(true, ConditionEvaluator.match(ast, vars("f_CITY", "Shanghai")));
        assertEquals(false, ConditionEvaluator.match(ast, vars("f_CITY", "Guangzhou")));
    }

    @Test
    public void firstHitWinsWithMultipleRows() {
        EngineNode n = node("{\"conditions\":["
                + "{\"result\":\"High\",\"formula\":[{\"field_code\":\"f_A\",\"operator\":\">=\",\"result\":\"100\",\"sign\":\"and\"}]},"
                + "{\"result\":\"Low\",\"formula\":[{\"field_code\":\"f_A\",\"operator\":\">\",\"result\":\"0\",\"sign\":\"and\"}]}]}");
        EngineSnapshot.DecisionTableConfig dt = EngineSnapshotLoader.compileInlineDecision(n);
        assertEquals("High", dt.getRows().get(0).getResultValue());
        assertEquals("High", hitValue(dt, 150));
        assertEquals("Low", hitValue(dt, 50));
    }

    private static Object hitValue(EngineSnapshot.DecisionTableConfig dt, int v) {
        for (EngineSnapshot.DecisionTableConfig.TableRow row : dt.getRows()) {
            if (ConditionEvaluator.match(row.getCondition(), vars("f_A", v))) {
                return row.getResultValue();
            }
        }
        return null;
    }
}
