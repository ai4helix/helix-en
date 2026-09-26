package com.helix.engine.rule.ast;

import com.helix.engine.entity.RuleConditionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PolarityWideningTest {

    private static RuleConditionEntity group(int id, Integer parent, int type) {
        RuleConditionEntity e = new RuleConditionEntity();
        e.setId((long) id);
        e.setParentId(parent == null ? null : parent.longValue());
        e.setNodeType(type);
        e.setSortNo(id);
        e.setRuleId(1);
        return e;
    }

    private static RuleConditionEntity leaf(int id, Integer parent, String field, String op, String value) {
        RuleConditionEntity e = group(id, parent, 1);
        e.setFieldCode(field);
        e.setOperator(op);
        e.setValue(value);
        return e;
    }

    private static RuleConditionEntity broken(int id, Integer parent) {
        return leaf(id, parent, "f_BROKEN", "NO_SUCH_OP", "x");
    }

    private static Map<String, Object> vars(boolean a, boolean b) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("f_A", a ? 1 : 0);
        m.put("f_B", b ? 1 : 0);
        return m;
    }

    private static void assertNeverWidens(Condition assembled, boolean[] origWhenBrokenFalse,
                                          boolean[] origWhenBrokenTrue, String scene) {
        if (assembled == null) {
            return;
        }
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                boolean got = ConditionEvaluator.match(assembled, vars(a == 1, b == 1));
                if (!got) {
                    continue;
                }
                boolean origFalse = origWhenBrokenFalse[a * 2 + b];
                boolean origTrue = origWhenBrokenTrue[a * 2 + b];
                if (!origFalse || !origTrue) {
                    throw new AssertionError(scene + ": assembled condition was widened - "
                            + "with f_A=" + (a == 1) + " f_B=" + (b == 1)
                            + " the assembled result hits, but under original semantics (broken condition as "
                            + (origFalse ? "" : "false=no-hit/") + (origTrue ? "" : "true=no-hit")
                            + ") not all hit");
                }
            }
        }
    }

    // =============================================================== AND

    @Test
    @DisplayName("positive AND: dropping a broken child widens - must be abandoned entirely")
    void positiveAnd() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 2));
        rows.add(leaf(2, 1, "f_A", "EQ", "1"));
        rows.add(broken(3, 1));

        Condition c = AstAssembler.assemble(rows);
        assertNeverWidens(c,
                new boolean[]{false, false, false, true},
                new boolean[]{false, false, true, true},
                "positive AND");
    }

    // =============================================================== OR

    @Test
    @DisplayName("positive OR: dropping a broken child only narrows - keep allowed (must not widen)")
    void positiveOr() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 3));
        rows.add(leaf(2, 1, "f_A", "EQ", "1"));
        rows.add(broken(3, 1));

        Condition c = AstAssembler.assemble(rows);
        assertNeverWidens(c,
                new boolean[]{false, true, true, true},
                new boolean[]{true, true, true, true},
                "positive OR");
    }

    // =============================================================== NOT(OR)

    @Test
    @DisplayName("NOT(OR): dropping widens - must be abandoned entirely")
    void negatedOr() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 4));   // NOT
        rows.add(group(2, 1, 3));      //   OR
        rows.add(leaf(3, 2, "f_A", "EQ", "1"));
        rows.add(broken(4, 2));

        Condition c = AstAssembler.assemble(rows);
        assertNeverWidens(c,
                new boolean[]{true, false, false, false},
                new boolean[]{false, false, false, false},
                "NOT(OR)");
    }


    @Test
    @DisplayName("NOT(AND): dropping a broken child has uncertain direction (may widen) - must be abandoned entirely")
    void negatedAnd() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 4));   // NOT
        rows.add(group(2, 1, 2));      //   AND
        rows.add(leaf(3, 2, "f_A", "EQ", "1"));
        rows.add(broken(4, 2));

        Condition c = AstAssembler.assemble(rows);
        assertNeverWidens(c,
                new boolean[]{true, true, false, false},
                new boolean[]{true, false, false, false},
                "NOT(AND)");
    }


    @Test
    @DisplayName("NOT(NOT(AND)): polarity back to positive AND - dropping widens, must be abandoned entirely")
    void doubleNegationAnd() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 4));
        rows.add(group(2, 1, 4));
        rows.add(group(3, 2, 2));
        rows.add(leaf(4, 3, "f_A", "EQ", "1"));
        rows.add(broken(5, 3));

        Condition c = AstAssembler.assemble(rows);
        assertNeverWidens(c,
                new boolean[]{false, false, false, true},
                new boolean[]{false, false, true, true},
                "NOT(NOT(AND))");
    }

    @Test
    @DisplayName("NOT(NOT(OR)): polarity back to positive OR - keep allowed (must not widen)")
    void doubleNegationOr() {
        List<RuleConditionEntity> rows = new ArrayList<RuleConditionEntity>();
        rows.add(group(1, null, 4));
        rows.add(group(2, 1, 4));
        rows.add(group(3, 2, 3));
        rows.add(leaf(4, 3, "f_A", "EQ", "1"));
        rows.add(broken(5, 3));

        Condition c = AstAssembler.assemble(rows);
        assertNeverWidens(c,
                new boolean[]{false, true, true, true},
                new boolean[]{true, true, true, true},
                "NOT(NOT(OR))");
    }
}
