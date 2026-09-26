package com.helix.engine.rule.ast;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlatConditionsTest {

    private static FlatConditions.Flat cond(String field, String op, String value, String logical) {
        FlatConditions.Flat f = new FlatConditions.Flat();
        f.field = field;
        f.operator = op;
        f.value = value;
        f.logical = logical;
        return f;
    }

    private static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new HashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @Test
    public void testAnd() {
        List<FlatConditions.Flat> conds = new ArrayList<FlatConditions.Flat>();
        conds.add(cond("f_A", ">", "1", "&&"));
        conds.add(cond("f_B", ">", "1", "-1"));

        assertFalse(FlatConditions.eval(conds, 0, vars("f_A", 5, "f_B", 0)).matched);
        assertTrue(FlatConditions.eval(conds, 0, vars("f_A", 5, "f_B", 9)).matched);
    }

    @Test
    public void testOr() {
        List<FlatConditions.Flat> conds = new ArrayList<FlatConditions.Flat>();
        conds.add(cond("f_A", ">", "54", "||"));
        conds.add(cond("f_GENDER", "==", "2", "-1"));

        FlatConditions.Result r = FlatConditions.eval(conds, 0, vars("f_A", 30, "f_GENDER", "2"));
        assertTrue(r.matched);
        assertFalse(r.leaves.get(0).hit);
        assertTrue(r.leaves.get(1).hit);
        assertFalse(FlatConditions.eval(conds, 0, vars("f_A", 30, "f_GENDER", "1")).matched);
    }

    @Test
    public void testMixedGroupingBoundary() {
        List<FlatConditions.Flat> conds = new ArrayList<FlatConditions.Flat>();
        conds.add(cond("f_A", ">", "1", "&&"));
        conds.add(cond("f_B", ">", "1", "||"));
        conds.add(cond("f_C", ">", "1", "-1"));

        assertTrue(FlatConditions.eval(conds, 0, vars("f_A", 5, "f_B", 0, "f_C", 9)).matched);
        assertFalse(FlatConditions.eval(conds, 0, vars("f_A", 0, "f_B", 5, "f_C", 0)).matched);
        assertTrue(FlatConditions.eval(conds, 0, vars("f_A", 5, "f_B", 5, "f_C", 0)).matched);
    }

    @Test
    public void testInversion() {
        List<FlatConditions.Flat> conds = new ArrayList<FlatConditions.Flat>();
        conds.add(cond("f_A", ">", "10", "-1"));

        FlatConditions.Result r = FlatConditions.eval(conds, 1, vars("f_A", 5));
        assertTrue(r.matched);
        assertTrue(r.inverted);
        assertFalse(r.leaves.get(0).hit);
    }

    @Test
    public void testMissingVariable() {
        List<FlatConditions.Flat> ne = new ArrayList<FlatConditions.Flat>();
        ne.add(cond("f_MISS", "!=", "1", "-1"));
        assertTrue(FlatConditions.eval(ne, 0, vars("f_A", 1)).matched);
        assertTrue(FlatConditions.eval(ne, 0, vars("f_A", 1)).leaves.get(0).hit);

        List<FlatConditions.Flat> gt = new ArrayList<FlatConditions.Flat>();
        gt.add(cond("f_MISS", ">", "1", "-1"));
        FlatConditions.Result r = FlatConditions.eval(gt, 0, vars("f_A", 1));
        assertFalse(r.matched);
        assertEquals(null, r.leaves.get(0).actual);
    }

    @Test
    public void testEmpty() {
        FlatConditions.Result r = FlatConditions.eval(new ArrayList<FlatConditions.Flat>(), 0, vars("f_A", 1));
        assertTrue(r.empty);
        assertFalse(r.matched);
    }

    @Test
    public void testUnknownOperator() {
        List<FlatConditions.Flat> conds = new ArrayList<FlatConditions.Flat>();
        conds.add(cond("f_A", "~=", "5", "-1"));
        FlatConditions.Result r = FlatConditions.eval(conds, 0, vars("f_A", 5));
        assertTrue(r.leaves.get(0).unknownOperator);
        assertTrue(r.leaves.get(0).hit);
    }
}
