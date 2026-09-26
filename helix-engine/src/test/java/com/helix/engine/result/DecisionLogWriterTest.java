package com.helix.engine.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.DecisionLogEntity;
import com.helix.engine.entity.engine.model.Result;
import com.helix.engine.mapper.DecisionLogMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DecisionLogWriterTest {

    @Test
    public void singleInsertAndJsonPayload() throws Exception {
        DecisionLogMapper logMapper = mock(DecisionLogMapper.class);
        DecisionLogWriter writer = new DecisionLogWriter(logMapper, new ObjectMapper());

        DecisionContext ctx = new DecisionContext();
        ctx.setTraceId("t-single-001");
        ctx.setEngineId(1);
        ctx.setEngineCode("ENG_T");
        ctx.setOrganId(2L);
        ctx.setVersionId(11);
        ctx.setPid("P001");
        ctx.setUid("U001");
        ctx.setRejected(true);
        ctx.setScore(15);
        Map<String, Object> vars = new HashMap<>();
        vars.put("f_AGE", 30);
        ctx.getVariables().putAll(vars);

        Result r = new Result();
        r.setId(7);
        r.setCode("R_DENY_HIGH");
        r.setName("High Amount Reject");
        r.setResultType("2");
        r.setValue("15");
        r.setExpression("f_LIMIT >= 1000");
        ctx.getNodeResults().put("n_policy", new ArrayList<>(Collections.singletonList(r)));

        DecisionContext.NodeTrace t1 = new DecisionContext.NodeTrace();
        t1.setNodeCode("n_policy");
        t1.setNodeName("Policy node");
        t1.setNodeType(2);
        t1.setHit(true);
        t1.setScoreDelta(15);
        t1.setMessage("Hit 1 reject rule(s)");
        ctx.getTraces().add(t1);
        DecisionContext.NodeTrace t2 = new DecisionContext.NodeTrace();
        t2.setNodeCode("n_decision");
        t2.setNodeName("Decision node");
        t2.setNodeType(9);
        ctx.getTraces().add(t2);

        writer.write(ctx, "Reject", 120);

        ArgumentCaptor<DecisionLogEntity> cap = ArgumentCaptor.forClass(DecisionLogEntity.class);
        verify(logMapper, times(1)).insert(any(DecisionLogEntity.class));
        verify(logMapper).insert(cap.capture());
        DecisionLogEntity row = cap.getValue();

        assertEquals("t-single-001", row.getTraceId());
        assertEquals(Integer.valueOf(2), row.getResultType());
        assertEquals(Integer.valueOf(1), row.getRejected());
        assertEquals(2L, row.getOrganId().longValue());

        ObjectMapper om = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode hits = om.readTree(row.getHitsJson());
        assertEquals(1, hits.size());
        assertEquals("R_DENY_HIGH", hits.get(0).get("ruleCode").asText());
        assertEquals("High Amount Reject", hits.get(0).get("ruleName").asText());
        assertEquals("2", hits.get(0).get("resultType").asText());
        assertEquals(15, hits.get(0).get("scoreValue").asInt());
        assertEquals("f_LIMIT >= 1000", hits.get(0).get("expression").asText());
        assertEquals("n_policy", hits.get(0).get("nodeCode").asText());

        com.fasterxml.jackson.databind.JsonNode traces = om.readTree(row.getTracesJson());
        assertEquals(2, traces.size());
        assertEquals("n_policy", traces.get(0).get("nodeCode").asText());
        assertEquals(true, traces.get(0).get("hit").asBoolean());
        assertEquals("n_decision", traces.get(1).get("nodeCode").asText());

        com.fasterxml.jackson.databind.JsonNode input = om.readTree(row.getInputJson());
        assertEquals(30, input.get("f_AGE").asInt());
    }
}
