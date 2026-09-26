package com.helix.engine.node.impl;

import com.helix.engine.config.EngineSnapshot;
import com.helix.engine.config.EngineSnapshotHolder;
import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.rule.ast.Condition;
import com.helix.engine.rule.ast.Operator;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DecisionNodeExecutorTest {

    private EngineSnapshotHolder holder;
    private DecisionNodeExecutor executor;
    private int versionId = 92;
    private String nodeCode = "ND_DT";

    @Before
    public void setUp() {
        holder = new EngineSnapshotHolder();
        executor = new DecisionNodeExecutor(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private void loadTable(EngineSnapshot.DecisionTableConfig dt) {
        Map<Integer, Map<String, EngineSnapshot.DecisionTableConfig>> dtConfigs =
                new HashMap<Integer, Map<String, EngineSnapshot.DecisionTableConfig>>();
        Map<String, EngineSnapshot.DecisionTableConfig> inner =
                new HashMap<String, EngineSnapshot.DecisionTableConfig>();
        inner.put(nodeCode, dt);
        dtConfigs.put(versionId, inner);
        holder.replace(new EngineSnapshot(
                Collections.<String, com.helix.engine.entity.engine.model.Engine>emptyMap(),
                Collections.<Integer, com.helix.engine.entity.engine.model.EngineVersion>emptyMap(),
                Collections.<Integer, Map<String, com.helix.engine.entity.engine.model.EngineNode>>emptyMap(),
                Collections.<Integer, Map<String, EngineSnapshot.PolicyConfig>>emptyMap(),
                Collections.<Integer, Map<String, EngineSnapshot.ScorecardConfig>>emptyMap(),
                Collections.<Integer, Map<String, String>>emptyMap(),
                dtConfigs), System.currentTimeMillis());
    }

    private EngineSnapshot.DecisionTableConfig.TableRow row(int no, String result,
                                                            Integer score, Condition... leaves) {
        List<Condition> list = new ArrayList<Condition>();
        Collections.addAll(list, leaves);
        Condition cond = list.size() == 1 ? list.get(0)
                : new Condition.Group(com.helix.engine.rule.ast.Logic.AND, list);
        String expr = com.helix.engine.rule.ast.ConditionExpressions.render(cond);
        return new EngineSnapshot.DecisionTableConfig.TableRow(no, cond, expr, result, null, score);
    }

    private Condition leaf(String field, Operator op, String value) {
        List<String> vs = new ArrayList<String>();
        vs.add(value);
        return new Condition.Leaf(field, op, vs);
    }

    private DecisionContext ctx(double age) {
        DecisionContext ctx = new DecisionContext();
        ctx.setVersionId(versionId);
        ctx.setChannel(holder.get().primary(versionId));
        ctx.getVariables().put("f_AGE", age);
        return ctx;
    }

    private EngineNode node(String outputField, Integer tableId) {
        EngineNode n = new EngineNode();
        n.setNodeId(1);
        n.setNodeCode(nodeCode);
        n.setNodeName("Decision node");
        n.setNodeType(9);
        n.setNodeJson("{\"output\":{\"field_code\":\"" + outputField + "\"},\"decision_table_id\":" + tableId + "}");
        return n;
    }

    @Test
    public void testFirstMatch() {
        loadTable(new EngineSnapshot.DecisionTableConfig(1, "DT_AGE", "Age tier", "FIRST",
                java.util.Arrays.asList(
                        row(1, "Senior tier", null, leaf("f_AGE", Operator.GE, "60")),
                        row(2, "Adult tier", null, leaf("f_AGE", Operator.GE, "18")),
                        row(3, "Minor tier", null, leaf("f_AGE", Operator.LT, "18")))));

        DecisionContext c70 = ctx(70);
        executor.execute(c70, node("f_LEVEL", 1));
        assertEquals("Senior tier", c70.getVar("f_LEVEL"));

        DecisionContext c30 = ctx(30);
        executor.execute(c30, node("f_LEVEL", 1));
        assertEquals("Adult tier", c30.getVar("f_LEVEL"));

        DecisionContext c10 = ctx(10);
        executor.execute(c10, node("f_LEVEL", 1));
        assertEquals("Minor tier", c10.getVar("f_LEVEL"));
    }

    @Test
    public void testNoMatch() {
        loadTable(new EngineSnapshot.DecisionTableConfig(1, "DT_AGE", "Age tier", "FIRST",
                Collections.singletonList(row(1, "Senior tier", null, leaf("f_AGE", Operator.GE, "60")))));

        DecisionContext ctx = ctx(30);
        executor.execute(ctx, node("f_LEVEL", 1));
        assertEquals(null, ctx.getVar("f_LEVEL"));
        boolean hasMiss = false;
        for (DecisionContext.NodeTrace t : ctx.getTraces()) {
            if (t.getMessage() != null && t.getMessage().contains("matched no rows")) {
                hasMiss = true;
            }
        }
        assertTrue("should have a no-match trace", hasMiss);
    }

    @Test
    public void testDimensionNotLimited() {
        loadTable(new EngineSnapshot.DecisionTableConfig(1, "DT_X", "Test", "FIRST",
                Collections.singletonList(row(1, "Hit", null, leaf("f_AGE", Operator.GE, "18")))));

        DecisionContext ctx = ctx(20);
        executor.execute(ctx, node("f_OUT", 1));
        assertEquals("Hit", ctx.getVar("f_OUT"));
    }

    @Test
    public void testAllPolicy() {
        loadTable(new EngineSnapshot.DecisionTableConfig(1, "DT_SCORE", "Accumulate", "ALL",
                java.util.Arrays.asList(
                        row(1, "Tier A", 5, leaf("f_AGE", Operator.GE, "18")),
                        row(2, "Tier B", 3, leaf("f_AGE", Operator.GE, "25")))));

        DecisionContext ctx = ctx(30);
        executor.execute(ctx, node("f_LEVEL", 1));
        assertEquals("Tier B", ctx.getVar("f_LEVEL"));
        assertEquals(8, ctx.getScore());
    }

    @Test
    public void testLegacyInlineStillWorks() {
        EngineNode n = new EngineNode();
        n.setNodeId(2);
        n.setNodeCode(nodeCode);
        n.setNodeType(9);
        n.setNodeJson("{\"output\":{\"field_code\":\"f_OUT\"},"
                + "\"conditions\":[{\"result\":\"1000,13\",\"formula\":"
                + "[{\"field_code\":\"f_LIMIT\",\"operator\":\">\",\"result\":\"0\",\"sign\":\"and\"}]}]}");

        EngineSnapshot.DecisionTableConfig compiled =
                com.helix.engine.config.EngineSnapshotLoader.compileInlineDecision(n);
        assertTrue("inline conditions should compile into a synthetic decision table", compiled != null);
        assertTrue("FIRST".equals(compiled.getHitPolicy()));

        loadTable(compiled);

        DecisionContext ctx = new DecisionContext();
        ctx.setVersionId(92);
        ctx.setChannel(holder.get().primary(92));
        ctx.getVariables().put("f_LIMIT", 500);
        executor.execute(ctx, n);
        assertEquals("1000,13", ctx.getVar("f_OUT"));

        DecisionContext miss = new DecisionContext();
        miss.setVersionId(92);
        miss.setChannel(holder.get().primary(92));
        miss.getVariables().put("f_LIMIT", 0);
        executor.execute(miss, n);
        assertTrue("no output should be written when condition not met", miss.getVar("f_OUT") == null);
    }

    @org.junit.Test
    public void denyRowOverridesEarlierManualResult() {
        Condition cond = leaf("f_A", Operator.EQ, "1");
        EngineSnapshot.DecisionTableConfig.TableRow denyRow =
                new EngineSnapshot.DecisionTableConfig.TableRow(1, cond,
                        com.helix.engine.rule.ast.ConditionExpressions.render(cond),
                        null, "DENY", null);
        loadTable(new EngineSnapshot.DecisionTableConfig(
                null, "T_DENY", "Reject table", "FIRST",
                Collections.singletonList(denyRow)));

        DecisionContext ctx = new DecisionContext();
        ctx.setVersionId(versionId);
        ctx.setChannel(holder.get().primary(versionId));
        ctx.getVariables().put("f_A", 1);
        ctx.setResultType("3");

        EngineNode n = new EngineNode();
        n.setNodeId(3);
        n.setNodeCode(nodeCode);
        n.setNodeType(9);
        n.setNodeJson("{\"output\":{\"field_code\":\"f_OUT\"}}");
        executor.execute(ctx, n);

        assertTrue("hitting a DENY row should set rejected", ctx.isRejected());
        assertEquals("conclusion must sync to reject instead of keeping the earlier manual review", "2", ctx.getResultType());
    }
}
