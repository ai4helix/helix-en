package com.helix.engine.rule;

import com.helix.engine.rule.ast.Condition;
import com.helix.engine.rule.ast.ConditionEvaluator;
import com.helix.engine.rule.ast.Logic;
import com.helix.engine.rule.ast.Operator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConditionEvaluatorDiffTest {

    // ==================================================================
    // ==================================================================

    @Test
    public void testNumericComparisons() {
        assertAligned("f_AGE", "<", "18",
                vars("f_AGE", 17), vars("f_AGE", 18), vars("f_AGE", 19),
                vars("f_AGE", "20"), vars("f_AGE", 20.5D));
        assertAligned("f_AGE", "<=", "18", vars("f_AGE", 17), vars("f_AGE", 18), vars("f_AGE", 19));
        assertAligned("f_AGE", ">", "18", vars("f_AGE", 17), vars("f_AGE", 18), vars("f_AGE", 19));
        assertAligned("f_AGE", ">=", "18", vars("f_AGE", 17), vars("f_AGE", 18), vars("f_AGE", 19));
        assertAligned("f_AGE", "==", "18", vars("f_AGE", 18), vars("f_AGE", 19), vars("f_AGE", "18"));
        assertAligned("f_AGE", "!=", "18", vars("f_AGE", 18), vars("f_AGE", 19));
    }

    @Test
    public void testMixedTypeComparison() {
        assertAligned("f_X", ">", "5",
                vars("f_X", 10), vars("f_X", "10"), vars("f_X", "abc"), vars("f_X", "3"));
        assertAligned("f_X", "<", "5",
                vars("f_X", 10), vars("f_X", "10"), vars("f_X", "abc"), vars("f_X", "3"));
        assertAligned("f_X", "==", "5", vars("f_X", 5), vars("f_X", "5"), vars("f_X", "05"));
    }

    @Test
    public void testMissingVariable() {
        Map<String, Object> empty = new HashMap<String, Object>();
        String[] ops = {"==", "!=", ">", ">=", "<", "<=", "in", "notIn", "contains",
                "notContains", "startsWith", "endsWith", "isNull", "notNull"};
        for (String op : ops) {
            assertAligned("f_MISSING", op, "1", empty);
        }
    }

    @Test
    public void testExplicitNullVariable() {
        List<Map<String, Object>> cases = new ArrayList<Map<String, Object>>();
        cases.add(vars("f_N", null));
        cases.add(vars("f_N", ""));
        for (String op : new String[]{"==", "!=", ">", "<", "in", "notIn", "isNull", "notNull"}) {
            for (Map<String, Object> v : cases) {
                assertAligned("f_N", op, "1", v);
            }
        }
    }

    // ==================================================================
    // ==================================================================

    @Test
    public void testInNotIn() {
        assertAligned("f_CITY", "in", "Beijing,Shanghai",
                vars("f_CITY", "Beijing"), vars("f_CITY", "Shanghai"), vars("f_CITY", "Shenzhen"),
                vars("f_CITY", " Beijing "));
        assertAligned("f_CITY", "notIn", "Beijing,Shanghai",
                vars("f_CITY", "Beijing"), vars("f_CITY", "Shenzhen"));
        assertAligned("f_LV", "in", "1,2,3",
                vars("f_LV", 1), vars("f_LV", "2"), vars("f_LV", 4));
        assertAligned("f_CITY", "in", " Beijing , Shanghai ",
                vars("f_CITY", "Beijing"), vars("f_CITY", "Shanghai"));
    }

    // ==================================================================
    // ==================================================================

    @Test
    public void testStringOperators() {
        assertAligned("f_NAME", "contains", "Zh",
                vars("f_NAME", "Zhang San"), vars("f_NAME", "Li Si"), vars("f_NAME", ""));
        assertAligned("f_NAME", "notContains", "Zh",
                vars("f_NAME", "Zhang San"), vars("f_NAME", "Li Si"));
        assertAligned("f_CODE", "startsWith", "A",
                vars("f_CODE", "ABC"), vars("f_CODE", "BAC"));
        assertAligned("f_CODE", "endsWith", "C",
                vars("f_CODE", "ABC"), vars("f_CODE", "ABD"));
    }

    // ==================================================================
    // ==================================================================

    @Test
    public void testNullOrEmpty() {
        assertAligned("f_A", "isNull", "", vars("f_A", "x"), vars("f_A", ""));
        assertAligned("f_A", "notNull", "", vars("f_A", "x"), vars("f_A", ""));
    }

    // ==================================================================
    // ==================================================================

    @Test
    public void testOrGrouping() {
        List<RuleConditionEval.Cond> v1Conds = new ArrayList<RuleConditionEval.Cond>();
        v1Conds.add(cond("f_AGE", "<", "18", "and"));
        v1Conds.add(cond("f_CITY", "in", "Beijing", "or"));
        v1Conds.add(cond("f_LV", "==", "1", "and"));

        List<Condition> g1 = new ArrayList<Condition>();
        g1.add(new Condition.Leaf("f_AGE", Operator.LT, single("18")));
        List<Condition> g2 = new ArrayList<Condition>();
        g2.add(new Condition.Leaf("f_CITY", Operator.IN, single("Beijing")));
        g2.add(new Condition.Leaf("f_LV", Operator.EQ, single("1")));
        List<Condition> ors = new ArrayList<Condition>();
        ors.add(new Condition.Group(Logic.AND, g1));
        ors.add(new Condition.Group(Logic.AND, g2));
        Condition v2Ast = new Condition.Group(Logic.OR, ors);

        Map<String, Object>[] cases = new Map[]{
                vars("f_AGE", 17, "f_CITY", "Shenzhen", "f_LV", "9"),
                vars("f_AGE", 20, "f_CITY", "Beijing", "f_LV", "1"),
                vars("f_AGE", 20, "f_CITY", "Beijing", "f_LV", "2"),
                vars("f_AGE", 20, "f_CITY", "Shenzhen", "f_LV", "1"),
                new HashMap<String, Object>()
        };
        for (Map<String, Object> v : cases) {
            boolean v1 = RuleConditionEval.eval(v1Conds, v);
            boolean v2 = ConditionEvaluator.match(v2Ast, v);
            assertEquals("grouping semantics mismatch: " + v, v1, v2);
        }
    }

    @Test
    public void testAllAnd() {
        List<RuleConditionEval.Cond> v1Conds = new ArrayList<RuleConditionEval.Cond>();
        v1Conds.add(cond("f_A", ">", "1", "and"));
        v1Conds.add(cond("f_B", ">", "1", "and"));

        List<Condition> children = new ArrayList<Condition>();
        children.add(new Condition.Leaf("f_A", Operator.GT, single("1")));
        children.add(new Condition.Leaf("f_B", Operator.GT, single("1")));
        Condition v2Ast = new Condition.Group(Logic.AND, children);

        Map<String, Object>[] cases = new Map[]{
                vars("f_A", 2, "f_B", 2),
                vars("f_A", 2, "f_B", 0),
                vars("f_A", 0, "f_B", 2)
        };
        for (Map<String, Object> v : cases) {
            assertEquals("AND semantics mismatch: " + v,
                    RuleConditionEval.eval(v1Conds, v), ConditionEvaluator.match(v2Ast, v));
        }
    }

    // ==================================================================
    // ==================================================================

    @Test
    public void testNullCondition() {
        assertEquals(false, ConditionEvaluator.match(null, new HashMap<String, Object>()));
    }

    @Test
    public void testIsNullWithEmptyString() {
        assertAligned("f_A", "isNull", "", vars("f_A", ""));
        assertAligned("f_A", "notNull", "", vars("f_A", ""));
        assertAligned("f_A", "isNull", "", vars("f_A", "x"));
    }

    // ==================================================================
    // ==================================================================

    private void assertAligned(String field, String op, String value, Map<String, Object>... cases) {
        List<RuleConditionEval.Cond> v1Conds = new ArrayList<RuleConditionEval.Cond>();
        v1Conds.add(cond(field, op, value, "and"));

        Condition v2Ast = toAst(field, op, value);

        for (Map<String, Object> v : cases) {
            boolean expected = RuleConditionEval.eval(v1Conds, v);
            boolean actual = ConditionEvaluator.match(v2Ast, v);
            assertEquals(
                    "evaluation mismatch [field=" + field + ", op=" + op + ", value=" + value + ", vars=" + v + "]",
                    expected, actual);
        }
    }

    private Condition toAst(String field, String op, String value) {
        Operator operator = Operator.parse(op);
        if (operator == null) {
            operator = Operator.EQ;
        }
        return new Condition.Leaf(field, operator, single(value));
    }

    private RuleConditionEval.Cond cond(String field, String op, String value, String logical) {
        RuleConditionEval.Cond c = new RuleConditionEval.Cond();
        c.setField(field);
        c.setOperator(op);
        c.setValue(value);
        c.setLogical(logical);
        return c;
    }

    private List<String> single(String v) {
        List<String> l = new ArrayList<String>();
        l.add(v);
        return l;
    }

    private Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new HashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    static void dump(String label, String field, String op, String value, Map<String, Object> v) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("case", label);
        out.put("vars", v);
        System.out.println(out);
    }
}
