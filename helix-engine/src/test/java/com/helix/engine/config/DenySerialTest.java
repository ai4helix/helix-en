package com.helix.engine.config;

import com.helix.engine.entity.NodeJson;
import com.helix.engine.entity.RuleEntity;
import com.helix.engine.rule.ast.Condition;
import com.helix.engine.rule.ast.Logic;
import com.helix.engine.rule.ast.Operator;
import com.helix.engine.rule.ast.ResultType;
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

class DenySerialTest {

    private static EngineSnapshot.RulePlan denyPlan(int id, String code) {
        Condition.Leaf leaf = new Condition.Leaf("f_A", Operator.EQ,
                Collections.singletonList("1"));
        return new EngineSnapshot.RulePlan(id, code, "Rule" + id, leaf,
                ResultType.DENY, null, 100);
    }

    private static NodeJson denySerialNodeJson() {
        NodeJson json = new NodeJson();
        NodeJson.DenyRules den = new NodeJson.DenyRules();
        den.setIsSerial(1);
        List<NodeJson.RuleRef> refs = new ArrayList<NodeJson.RuleRef>();
        for (String code : Arrays.asList("r1", "r2", "r3")) {
            NodeJson.RuleRef ref = new NodeJson.RuleRef();
            ref.setCode(code);
            refs.add(ref);
        }
        den.setRules(refs);
        json.setDenyRules(den);
        return json;
    }

    private static Map<String, Object> hitVars() {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("f_A", 1);
        return m;
    }

    @Test
    @DisplayName("denyTotal takes the actually loaded count (not the configured count)")
    void denyTotalCountsLoadedOnly() {
        List<EngineSnapshot.RulePlan> plans = new ArrayList<EngineSnapshot.RulePlan>();
        plans.add(denyPlan(1, "r1"));
        EngineSnapshot.PolicyConfig cfg = new EngineSnapshot.PolicyConfig(
                denySerialNodeJson(), plans, new HashMap<Integer, RuleEntity>());
        assertEquals(1, cfg.getDenyTotal(),
                "configured 3 but only 1 loaded, denyTotal should be 1 (avoids serial never satisfied)");
        assertTrue(cfg.isDenySerial());
    }

    @Test
    @DisplayName("denyTotal shares the source of hit count: with any DENY hit the total is >= 1 (guard never disabled)")
    void denyTotalAlwaysCoversHits() {
        List<EngineSnapshot.RulePlan> plans = new ArrayList<EngineSnapshot.RulePlan>();
        plans.add(denyPlan(1, "r1"));
        EngineSnapshot.PolicyConfig cfg = new EngineSnapshot.PolicyConfig(
                denySerialNodeJson(), plans, new HashMap<Integer, RuleEntity>());

        assertTrue(cfg.getDenyTotal() >= 1,
                "with a DENY rule present denyTotal must be >= 1, otherwise serial judgment is skipped");
    }

    @Test
    @DisplayName("with no deny rules loaded denyTotal=0, so no DENY hit is possible (blocked by the outer layer)")
    void noDenyRulesMeansNoDenyHits() {
        EngineSnapshot.PolicyConfig cfg = new EngineSnapshot.PolicyConfig(
                denySerialNodeJson(), new ArrayList<EngineSnapshot.RulePlan>(),
                new HashMap<Integer, RuleEntity>());
        assertEquals(0, cfg.getDenyTotal());
        assertTrue(cfg.getRulePlans().isEmpty());
    }

    @Test
    @DisplayName("serial mode: hits below total - no reject (core semantics)")
    void serialNotEnoughHits() {
        List<EngineSnapshot.RulePlan> plans = new ArrayList<EngineSnapshot.RulePlan>();
        plans.add(denyPlan(1, "r1"));
        plans.add(denyPlan(2, "r2"));
        EngineSnapshot.PolicyConfig cfg = new EngineSnapshot.PolicyConfig(
                denySerialNodeJson(), plans, new HashMap<Integer, RuleEntity>());

        int hitCount = 1;
        assertTrue(hitCount < cfg.getDenyTotal(), "1 < 2, serial not satisfied");
        boolean rejected = hitCount >= cfg.getDenyTotal();
        assertFalse(rejected, "serial mode with 1/2 hits should not reject");
    }

    @Test
    @DisplayName("serial mode: hits reach total - reject")
    void serialEnoughHits() {
        List<EngineSnapshot.RulePlan> plans = new ArrayList<EngineSnapshot.RulePlan>();
        plans.add(denyPlan(1, "r1"));
        plans.add(denyPlan(2, "r2"));
        EngineSnapshot.PolicyConfig cfg = new EngineSnapshot.PolicyConfig(
                denySerialNodeJson(), plans, new HashMap<Integer, RuleEntity>());

        int hitCount = 2;
        assertTrue(hitCount >= cfg.getDenyTotal(), "2 >= 2, serial satisfied - reject");
    }

    @Test
    @DisplayName("add/sub threshold defaults to 0 (no threshold = always counted)")
    void addSubThresholdDefaultsToZero() {
        EngineSnapshot.PolicyConfig cfg = new EngineSnapshot.PolicyConfig(
                new NodeJson(), Collections.<EngineSnapshot.RulePlan>emptyList(),
                new HashMap<Integer, RuleEntity>());
        assertEquals(0D, cfg.getAddSubThreshold(), 1e-9,
                "default threshold 0 - Math.abs(delta) >= 0 is always true, i.e. no threshold");
    }

    @Test
    @DisplayName("empty group is false (avoids accidental pass)")
    void emptyGroupIsFalse() {
        Condition.Group empty = new Condition.Group(Logic.AND,
                new ArrayList<Condition>());
        assertFalse(com.helix.engine.rule.ast.ConditionEvaluator.match(empty, hitVars()),
                "empty condition group should be false, avoiding accidental pass");
    }

    @Test
    @DisplayName("matchAll on an empty set is true (rules without conditions apply)")
    void matchAllEmptyIsTrue() {
        assertTrue(com.helix.engine.rule.ast.ConditionEvaluator.matchAll(
                new ArrayList<Condition>(), hitVars()),
                "empty condition set is treated as unconditional (different from single-condition match(null)=false)");
    }
}
