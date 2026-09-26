package com.helix.engine.config;

import com.helix.engine.entity.RuleEntity;
import com.helix.engine.rule.ast.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultTypeResolveTest {

    private static RuleEntity rule(String v2, Short ruleType, Integer score, Short ruleAudit) {
        RuleEntity r = new RuleEntity();
        r.setId(1);
        r.setName("Test Rule");
        r.setResultTypeV2(v2);
        r.setRuleType(ruleType);
        r.setScoreValue(score);
        r.setRuleAudit(ruleAudit);
        return r;
    }


    @Test
    @DisplayName("unrecognized non-blank value returns null (rule skipped), never silently degrades to PASS")
    void illegalValueMustNotDegradeToPass() {
        assertNull(EngineSnapshotLoader.resolveResultType(rule("DENI", null, null, null)),
                "a misspelled result type must return null instead of degrading to PASS");
        assertNull(EngineSnapshotLoader.resolveResultType(rule("6", null, null, null)),
                "out-of-range code must return null");
        assertNull(EngineSnapshotLoader.resolveResultType(rule("PASSX", (short) 0, null, null)),
                "illegal enum name must return null");
    }


    @Test
    @DisplayName("ambiguous code \"1\" + rule_type=1 - score type, restores add/sub by score sign (score not lost)")
    void legacyAmbiguousCodeRestoresScore() {
        ResultType add = EngineSnapshotLoader.resolveResultType(rule("1", (short) 1, 10, null));
        assertEquals(ResultType.ADD_SCORE, add, "legacy add-score rule must restore to ADD_SCORE");
        assertTrue(add.isScore(), "isScore() must be true, otherwise the score is silently dropped");

        ResultType sub = EngineSnapshotLoader.resolveResultType(rule("1", (short) 1, -5, null));
        assertEquals(ResultType.SUB_SCORE, sub, "legacy sub-score rule must restore to SUB_SCORE");
        assertTrue(sub.isScore());

        assertEquals(ResultType.ADD_SCORE,
                EngineSnapshotLoader.resolveResultType(rule("1", (short) 1, null, null)));
    }

    @Test
    @DisplayName("result_type_v2 null + rule_type=1 - also falls back to score type")
    void nullV2WithScoreRuleType() {
        assertEquals(ResultType.ADD_SCORE,
                EngineSnapshotLoader.resolveResultType(rule(null, (short) 1, 20, null)));
    }

    @Test
    @DisplayName("blank string counts as unmigrated (fallback not only for null)")
    void blankV2IsNotMigrated() {
        assertEquals(ResultType.ADD_SCORE,
                EngineSnapshotLoader.resolveResultType(rule("   ", (short) 1, 5, null)));
    }


    @Test
    @DisplayName("unmigrated + hard type + rule_audit=3 - MANUAL (must not load as DENY and wrongly reject customers)")
    void legacyManualMustNotBecomeDeny() {
        ResultType rt = EngineSnapshotLoader.resolveResultType(rule(null, (short) 0, null, (short) 3));
        assertEquals(ResultType.MANUAL, rt,
                "loading a legacy manual-review rule as DENY turns manual review into direct reject = wrongly rejecting customers");
        assertFalse(rt.isBlocking(), "MANUAL must not be a final reject");
    }

    @Test
    @DisplayName("unmigrated + hard type + rule_audit!=3 (incl. null) - DENY (conservative: prefer under-release over wrong release)")
    void legacyDenyFallback() {
        assertEquals(ResultType.DENY,
                EngineSnapshotLoader.resolveResultType(rule(null, (short) 0, null, (short) 2)));
        assertEquals(ResultType.DENY,
                EngineSnapshotLoader.resolveResultType(rule(null, (short) 0, null, null)),
                "unset rule_audit is treated as reject (conservative direction for hard type)");
        assertEquals(ResultType.DENY,
                EngineSnapshotLoader.resolveResultType(rule("1", (short) 0, null, (short) 1)));
    }

    @Test
    @DisplayName("fallback never yields PASS (otherwise legacy reject/manual rules silently pass)")
    void fallbackNeverPass() {
        Short[] audits = {null, (short) 1, (short) 2, (short) 3};
        for (Short audit : audits) {
            ResultType rt = EngineSnapshotLoader.resolveResultType(rule(null, (short) 0, null, audit));
            if (rt == ResultType.PASS) {
                throw new AssertionError("fallback result must not be PASS (rule_audit=" + audit + ")");
            }
        }
        ResultType scoreRt = EngineSnapshotLoader.resolveResultType(rule(null, (short) 1, 10, null));
        assertFalse(scoreRt == ResultType.PASS, "score-type fallback must not be PASS either");
    }


    @Test
    @DisplayName("migrated: v2 enum names resolve normally (case-insensitive)")
    void v2EnumNames() {
        assertEquals(ResultType.DENY, EngineSnapshotLoader.resolveResultType(rule("DENY", null, null, null)));
        assertEquals(ResultType.MANUAL, EngineSnapshotLoader.resolveResultType(rule("manual", null, null, null)));
        assertEquals(ResultType.SUB_SCORE, EngineSnapshotLoader.resolveResultType(rule("SUB_SCORE", null, null, null)));
        assertEquals(ResultType.ADD_SCORE, EngineSnapshotLoader.resolveResultType(rule("Add_Score", null, null, null)));
    }

    @Test
    @DisplayName("migrated: unambiguous v1 codes 2/3/4/5 resolve normally")
    void v1ExplicitCodes() {
        assertEquals(ResultType.DENY, EngineSnapshotLoader.resolveResultType(rule("2", null, null, null)));
        assertEquals(ResultType.MANUAL, EngineSnapshotLoader.resolveResultType(rule("3", null, null, null)));
        assertEquals(ResultType.ADD_SCORE, EngineSnapshotLoader.resolveResultType(rule("4", null, null, null)));
        assertEquals(ResultType.SUB_SCORE, EngineSnapshotLoader.resolveResultType(rule("5", null, null, null)));
    }

    @Test
    @DisplayName("migrated value wins over rule_type/rule_audit (not overridden by fallback)")
    void migratedValueWins() {
        assertEquals(ResultType.DENY,
                EngineSnapshotLoader.resolveResultType(rule("DENY", (short) 1, 10, (short) 3)));
    }

    @Test
    @DisplayName("neither v2 type nor rule_type - null (no semantically ambiguous rule produced)")
    void noTypeInfoAtAll() {
        assertNull(EngineSnapshotLoader.resolveResultType(rule(null, null, null, (short) 3)),
                "a rule with no type info at all returns null for the caller to skip");
    }
}
