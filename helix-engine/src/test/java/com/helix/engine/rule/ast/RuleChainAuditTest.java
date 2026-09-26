package com.helix.engine.rule.ast;

import com.helix.engine.entity.RuleConditionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleChainAuditTest {


    private static RuleConditionEntity leaf(int id, Integer parent, String field, String op, String value) {
        RuleConditionEntity e = new RuleConditionEntity();
        e.setId((long) id);
        e.setParentId(parent == null ? null : parent.longValue());
        e.setNodeType(1);
        e.setFieldCode(field);
        e.setOperator(op);
        e.setValue(value);
        e.setSortNo(id);
        e.setRuleId(1);
        return e;
    }

    private static RuleConditionEntity group(int id, Integer parent, int nodeType) {
        RuleConditionEntity e = new RuleConditionEntity();
        e.setId((long) id);
        e.setParentId(parent == null ? null : parent.longValue());
        e.setNodeType(nodeType);
        e.setSortNo(id);
        e.setRuleId(1);
        return e;
    }

    private static Map<String, Object> vars(String k, Object v) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put(k, v);
        return m;
    }

    // ---------------------------------------------------------------- P0① BETWEEN

    @Test
    @DisplayName("P0-1 BETWEEN should split bounds by comma and judge the range correctly")
    void betweenShouldSplitBounds() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(leaf(1, null, "f_AGE", "BETWEEN", "18,60"));
        Condition c = AstAssembler.assemble(rows);
        assertEquals(2, ((Condition.Leaf) c).getValues().size(),
                "BETWEEN should be split into [lower, upper] two values at load time");

        assertTrue(ConditionEvaluator.match(c, vars("f_AGE", 30)), "30 should be within [18,60]");
        assertTrue(ConditionEvaluator.match(c, vars("f_AGE", 18)), "18 should hit (closed interval)");
        assertTrue(ConditionEvaluator.match(c, vars("f_AGE", 60)), "60 should hit (closed interval)");
        assertFalse(ConditionEvaluator.match(c, vars("f_AGE", 17)), "17 should not hit");
        assertFalse(ConditionEvaluator.match(c, vars("f_AGE", 61)), "61 should not hit");
    }


    @Test
    @DisplayName("P0-2 STARTS_WITH/CONTAINS on numeric text must use string semantics")
    void stringOperatorsOnNumericText() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(leaf(1, null, "f_PHONE", "STARTS_WITH", "138"));
        Condition c = AstAssembler.assemble(rows);
        assertTrue(ConditionEvaluator.match(c, vars("f_PHONE", "13800001238")),
                "phone 13800001238 should start with 138");

        List<RuleConditionEntity> rows2 = new ArrayList<RuleConditionEntity>();
        rows2.add(leaf(1, null, "f_CODE", "CONTAINS", "00"));
        Condition c2 = AstAssembler.assemble(rows2);
        assertTrue(ConditionEvaluator.match(c2, vars("f_CODE", "1001")),
                "1001 should contain 00");

        List<RuleConditionEntity> rows3 = new ArrayList<RuleConditionEntity>();
        rows3.add(leaf(1, null, "f_CODE", "NOT_CONTAINS", "00"));
        Condition c3 = AstAssembler.assemble(rows3);
        assertFalse(ConditionEvaluator.match(c3, vars("f_CODE", "1001")),
                "1001 contains 00, so NOT_CONTAINS should be false");
    }

    @Test
    @DisplayName("P0-2 pure numeric equality (EQ) must not be affected by string semantics")
    void numericEqualityStillWorks() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(leaf(1, null, "f_AGE", "EQ", "30"));
        Condition c = AstAssembler.assemble(rows);
        assertTrue(ConditionEvaluator.match(c, vars("f_AGE", 30)));
        assertTrue(ConditionEvaluator.match(c, vars("f_AGE", "30")));
        assertFalse(ConditionEvaluator.match(c, vars("f_AGE", 31)));
    }


    @Test
    @DisplayName("P0-4 illegal child operator in an AND group invalidates the rule instead of widening it")
    void brokenChildShouldNotWeakenAndGroup() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 2));                       // AND
        rows.add(leaf(2, 1, "f_AGE", "GT", "18"));
        rows.add(leaf(3, 1, "f_CITY", "WRONG_OP", "Shenzhen"));

        Condition c = AstAssembler.assemble(rows);
        Map<String, Object> v = new HashMap<String, Object>();
        v.put("f_AGE", 20);
        v.put("f_CITY", "Shenzhen");

        assertFalse(ConditionEvaluator.match(c, v),
                "AND group reduced to only f_AGE>18 then falsely hits - condition tree was widened");
    }

    @Test
    @DisplayName("P0-4 with an illegal child, assembly must never widen (core AND safety property)")
    void brokenChildMustNeverWidenAnd() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 2));                        // AND
        rows.add(leaf(2, 1, "f_AGE", "GT", "18"));
        rows.add(leaf(3, 1, "f_CITY", "WRONG_OP", "Shenzhen"));

        Condition c = AstAssembler.assemble(rows);
        Map<String, Object> v = new HashMap<String, Object>();
        v.put("f_AGE", 20);
        v.put("f_CITY", "Shenzhen");

        assertTrue(c == null || !ConditionEvaluator.match(c, v),
                "dropping an illegal child widens the AND group - may wrongly hurt customers");
    }

    @Test
    @DisplayName("P0-4 with an illegal child, assembly must never widen (OR group must not widen either)")
    void brokenChildMustNeverWidenOr() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 3));                        // OR
        rows.add(leaf(2, 1, "f_AGE", "GT", "18"));
        rows.add(leaf(3, 1, "f_CITY", "WRONG_OP", "Shenzhen"));

        Condition c = AstAssembler.assemble(rows);
        Map<String, Object> v = new HashMap<String, Object>();
        v.put("f_AGE", 10);
        v.put("f_CITY", "Shenzhen");
        assertTrue(c == null || !ConditionEvaluator.match(c, v),
                "an illegal child must not be treated as a hit branch, otherwise the OR group widens");
    }

    @Test
    @DisplayName("P0-4 all children valid: unaffected (regression)")
    void allChildrenValidUnaffected() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 2));
        rows.add(leaf(2, 1, "f_AGE", "GT", "18"));
        rows.add(leaf(3, 1, "f_CITY", "EQ", "Shenzhen"));

        Condition c = AstAssembler.assemble(rows);
        assertNotNull(c);
        Map<String, Object> v = new HashMap<String, Object>();
        v.put("f_AGE", 20);
        v.put("f_CITY", "Shenzhen");
        assertTrue(ConditionEvaluator.match(c, v));
        v.put("f_CITY", "Beijing");
        assertFalse(ConditionEvaluator.match(c, v));
    }


    @Test
    @DisplayName("IN splits by comma (existing correct behavior, unaffected by the BETWEEN fix)")
    void inStillWorks() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(leaf(1, null, "f_STATUS", "IN", "A,B,C"));
        Condition c = AstAssembler.assemble(rows);
        assertTrue(ConditionEvaluator.match(c, vars("f_STATUS", "B")));
        assertFalse(ConditionEvaluator.match(c, vars("f_STATUS", "D")));
    }

    @Test
    @DisplayName("single-value operator value containing a comma must not be split (boundary of the BETWEEN fix)")
    void singleValueWithComma() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(leaf(1, null, "f_NAME", "CONTAINS", "a,b"));
        Condition c = AstAssembler.assemble(rows);
        assertEquals(1, ((Condition.Leaf) c).getValues().size(),
                "CONTAINS value should be kept whole, not split for containing a comma");
        assertTrue(ConditionEvaluator.match(c, vars("f_NAME", "xa,by")));
    }

    @Test
    @DisplayName("missing NOT child invalidates the branch (no pass)")
    void notWithMissingChild() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 4));
        Condition c = AstAssembler.assemble(rows);
        assertTrue(c == null || !ConditionEvaluator.match(c, vars("x", 1)));
    }

    @Test
    @DisplayName("multi-value operators must declare multiValue (for loader-side validation)")
    void multiValueOperatorsDeclared() {
        assertTrue(Operator.BETWEEN.isMultiValue(), "BETWEEN should be a multi-value operator");
        assertFalse(Operator.EQ.isMultiValue(), "EQ should not be a multi-value operator");
        assertEquals(2, Arrays.asList("a", "b").size());
    }


    private static List<RuleConditionEntity> groupWithBrokenChild(int groupType) {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, groupType));
        rows.add(leaf(2, 1, "f_AGE", "GT", "18"));
        rows.add(leaf(3, 1, "f_CITY", "WRONG_OP", "Shenzhen"));
        return rows;
    }

    @Test
    @DisplayName("P0-4 positive OR dropping an illegal child only narrows - keep the valid child, do not drop the whole")
    void positiveOrKeepsValidChild() {
        Condition c = AstAssembler.assemble(groupWithBrokenChild(3));
        assertNotNull(c, "positive OR should not be dropped entirely, otherwise valid-child protection is needlessly lost");
        assertTrue(ConditionEvaluator.match(c, vars("f_AGE", 20)),
                "the kept valid child should participate in evaluation normally");
    }

    @Test
    @DisplayName("P0-4 negated OR dropping an illegal child widens - drop the whole (direction flips under NOT)")
    void negatedOrDropsWhole() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 4));   // NOT
        rows.add(group(2, 1, 3));
        rows.add(leaf(3, 2, "f_AGE", "GT", "18"));
        rows.add(leaf(4, 2, "f_CITY", "WRONG_OP", "Shenzhen"));

        Condition c = AstAssembler.assemble(rows);
        assertNull(c, "OR under NOT widens when dropping a child, must be abandoned entirely");
    }

    @Test
    @DisplayName("P0-4 negated AND (AND under NOT) has uncertain direction when dropping a child - must be abandoned entirely")
    void negatedAndMustAbandon() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 4));   // NOT
        rows.add(group(2, 1, 2));
        rows.add(leaf(3, 2, "f_AGE", "GT", "18"));
        rows.add(leaf(4, 2, "f_CITY", "WRONG_OP", "Shenzhen"));

        Condition c = AstAssembler.assemble(rows);
        assertNull(c, "AND under NOT has uncertain direction when dropping a child (may widen), must be abandoned entirely");
    }

    @Test
    @DisplayName("P0-4 double NOT returns to positive AND - dropping widens, drop the whole")
    void doubleNegationBackToPositiveAnd() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 4));   // NOT
        rows.add(group(2, 1, 4));
        rows.add(group(3, 2, 2));
        rows.add(leaf(4, 3, "f_AGE", "GT", "18"));
        rows.add(leaf(5, 3, "f_CITY", "WRONG_OP", "Shenzhen"));

        Condition c = AstAssembler.assemble(rows);
        assertNull(c, "even-level NOT equals positive AND; dropping a child widens, must be abandoned entirely");
    }
}
