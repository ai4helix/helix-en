package com.helix.engine.node.impl;

import com.helix.engine.config.EngineSnapshot;
import com.helix.engine.config.EngineSnapshotHolder;
import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.node.NodeTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ListDbNodeExecutorTest {

    private static final int LIST_ID = 2;

    private final ObjectMapper mapper = new ObjectMapper();
    private EngineSnapshotHolder holder;

    @Before
    public void setUp() {
        holder = new EngineSnapshotHolder();
        Map<Integer, java.util.Set<String>> entries = new HashMap<>();
        entries.put(LIST_ID, new HashSet<>(Arrays.asList("Shanghai", "Beijing", "Shenzhen")));
        Map<Integer, String> names = new HashMap<>();
        names.put(LIST_ID, "Residence City Blacklist");
        holder.replace(new EngineSnapshot(Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), entries, names), System.currentTimeMillis());
    }

    private EngineNode node(String json) {
        EngineNode node = new EngineNode();
        node.setNodeId(1);
        node.setNodeCode("N_LIST");
        node.setNodeName("List node");
        node.setNodeType(NodeTypes.BLACKLIST);
        node.setNodeJson(json);
        return node;
    }

    private DecisionContext ctx(String city) {
        DecisionContext ctx = new DecisionContext();
        if (city != null) {
            ctx.getVariables().put("f_CITY", city);
        }
        return ctx;
    }

    @Test
    public void testListMembershipHitRejects() {
        DecisionContext ctx = ctx("Shanghai");
        ListDbNodeExecutor.doExecute(ctx, node("{\"list_db_ids\":[" + LIST_ID + "],\"matchFields\":[\"f_CITY\"]}"),
                false, mapper, holder);
        assertTrue(ctx.isRejected());
        assertEquals("2", ctx.getResultType());
        assertTrue(ctx.getTraces().get(0).isHit());
        assertTrue(ctx.getTraces().get(0).getHitDetails().get(0).contains("Residence City Blacklist"));
    }

    @Test
    public void testValueTrimmedBeforeMatch() {
        DecisionContext ctx = ctx("  Beijing  ");
        ListDbNodeExecutor.doExecute(ctx, node("{\"list_db_ids\":[" + LIST_ID + "],\"matchFields\":[\"f_CITY\"]}"),
                false, mapper, holder);
        assertTrue(ctx.isRejected());
    }

    @Test
    public void testListMembershipMiss() {
        DecisionContext ctx = ctx("Hangzhou");
        ListDbNodeExecutor.doExecute(ctx, node("{\"list_db_ids\":[" + LIST_ID + "],\"matchFields\":[\"f_CITY\"]}"),
                false, mapper, holder);
        assertFalse(ctx.isRejected());
        assertFalse(ctx.getTraces().get(0).isHit());
        assertTrue(ctx.getTraces().get(0).getMessage().contains("3 entries in total"));
    }

    @Test
    public void testWhitelistHitPasses() {
        DecisionContext ctx = ctx("Shenzhen");
        ListDbNodeExecutor.doExecute(ctx, node("{\"list_db_ids\":[" + LIST_ID + "],\"matchFields\":[\"f_CITY\"]}"),
                true, mapper, holder);
        assertTrue(ctx.isWhitelisted());
        assertEquals("1", ctx.getResultType());
    }

    @Test
    public void testLegacyFlagSemanticsStillWorks() {
        DecisionContext ctx = ctx(null);
        ctx.getVariables().put("f_IN_BLACKLIST", "1");
        ListDbNodeExecutor.doExecute(ctx, node("{\"matchFields\":[\"f_IN_BLACKLIST\"]}"),
                false, mapper, holder);
        assertTrue(ctx.isRejected());
    }

    @Test
    public void testUnloadedListDbTreatedAsMiss() {
        DecisionContext ctx = ctx("Shanghai");
        ListDbNodeExecutor.doExecute(ctx, node("{\"list_db_ids\":[999],\"matchFields\":[\"f_CITY\"]}"),
                false, mapper, holder);
        assertFalse(ctx.isRejected());
        assertTrue(ctx.getTraces().get(0).getMessage().contains("not loaded"));
    }
}
