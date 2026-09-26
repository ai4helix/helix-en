package com.helix.engine.node.impl;

import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.node.NodeExecutor;
import com.helix.engine.node.NodeTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Black/white list node executor.
 *
 * <p>Two matching semantics (auto-selected by {@code node_json} config;
 * when both present, the list DB wins):</p>
 * <ol>
 *   <li><b>List DB sets</b>: {@code list_db_ids} references list DBs (array);
 *       each variable of {@code matchFields/matchField} is tested for membership
 *       (value ∈ the valid list-entry set loaded in the snapshot means hit). Zero DB
 *       queries at decision time; list changes take effect after console triggers
 *       a snapshot rebuild;</li>
 *   <li><b>External flags (fallback semantics)</b>: when {@code list_db_ids} is absent,
 *       a variable value of {@code 1} means hit (precomputed upstream); legacy behavior preserved.</li>
 * </ol>
 *
 * <p>Hit semantics: blacklist hit -> reject; whitelist hit -> pass. The parent class
 * distinguishes via {@link #supportType()}; whitelist see {@link WhitelistNodeExecutor}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListDbNodeExecutor implements NodeExecutor {

    private final ObjectMapper objectMapper;
    private final com.helix.engine.config.EngineSnapshotHolder snapshotHolder;

    @Override
    public int supportType() {
        return NodeTypes.BLACKLIST;
    }

    @Override
    public void execute(DecisionContext ctx, EngineNode node) {
        doExecute(ctx, node, false, objectMapper, snapshotHolder);
    }

    /**
     * Whitelist node executor.
     */
    @Component
    @RequiredArgsConstructor
    public static class WhitelistNodeExecutor implements NodeExecutor {

        private final ObjectMapper objectMapper;
        private final com.helix.engine.config.EngineSnapshotHolder snapshotHolder;

        @Override
        public int supportType() {
            return NodeTypes.WHITELIST;
        }

        @Override
        public void execute(DecisionContext ctx, EngineNode node) {
            doExecute(ctx, node, true, objectMapper, snapshotHolder);
        }
    }

    static void doExecute(DecisionContext ctx, EngineNode node, boolean white, ObjectMapper mapper,
                          com.helix.engine.config.EngineSnapshotHolder holder) {
        DecisionContext.NodeTrace trace = new DecisionContext.NodeTrace();
        trace.setNodeId(node.getNodeId());
        trace.setNodeCode(node.getNodeCode());
        trace.setNodeName(node.getNodeName());
        trace.setNodeType(node.getNodeType());

        List<String> matchFields = parseMatchFields(node, mapper);
        List<Integer> listDbIds = parseListDbIds(node, mapper);
        boolean hit = false;

        if (!listDbIds.isEmpty()) {
            // List DB set matching: variable value ∈ any referenced list's valid entry set.
            // Prefer the snapshot generation pinned in the context (same generation as the node graph),
            // avoiding list entries from another generation after a mid-flight reload;
            // fall back to the current snapshot when the context has none.
            com.helix.engine.config.EngineSnapshot snapshot = ctx.getSnapshot() != null
                    ? ctx.getSnapshot() : holder.get();
            List<Integer> loadedIds = new ArrayList<>();
            int totalEntries = 0;
            for (Integer listId : listDbIds) {
                int size = snapshot.getListEntries(listId).size();
                totalEntries += size;
                if (size > 0) {
                    loadedIds.add(listId);
                }
            }
            if (loadedIds.isEmpty()) {
                trace.setMessage("List DBs not loaded or no valid entries (referencing " + listDbIds.size() + " list DB(s))");
            } else {
                outer:
                for (String field : matchFields) {
                    Object v = ctx.getVar(field);
                    if (v == null || StringUtils.isBlank(String.valueOf(v))) {
                        continue;
                    }
                    String norm = String.valueOf(v).trim();
                    for (Integer listId : loadedIds) {
                        if (snapshot.getListEntries(listId).contains(norm)) {
                            hit = true;
                            trace.getHitDetails().add(field + "=" + norm + " ∈ " + snapshot.getListName(listId));
                            break outer;
                        }
                    }
                }
                if (!hit) {
                    trace.setMessage("No list hit (compared " + matchFields.size() + " field(s) x "
                            + loadedIds.size() + " list DB(s), " + totalEntries + " entries in total)");
                }
            }
        } else {
            // Fallback semantics: variable value 1 means hit (upstream precomputed flag)
            for (String field : matchFields) {
                Object v = ctx.getVar(field);
                if (v != null && "1".equals(String.valueOf(v).trim())) {
                    hit = true;
                    trace.getHitDetails().add(field + "=1");
                }
            }
            if (!hit) {
                trace.setMessage(matchFields.isEmpty() ? "No match fields configured" : "No list hit");
            }
        }

        trace.setHit(hit);
        if (hit) {
            if (white) {
                ctx.setWhitelisted(true);
                ctx.setResultType("1");
                trace.setMessage("Whitelist hit, direct pass");
            } else {
                ctx.setRejected(true);
                ctx.setResultType("2");
                trace.setMessage("Blacklist hit, direct reject");
            }
        }
        ctx.getTraces().add(trace);
    }

    /** Parse match fields: tolerates both matchFields array and matchField single value */
    private static List<String> parseMatchFields(EngineNode node, ObjectMapper mapper) {
        List<String> fields = new ArrayList<>();
        String json = node.getNodeJson();
        if (StringUtils.isBlank(json)) {
            return fields;
        }
        try {
            JsonNode cfg = mapper.readTree(json);
            JsonNode arr = cfg.path("matchFields");
            if (arr.isArray()) {
                arr.forEach(n -> {
                    if (StringUtils.isNotBlank(n.asText())) {
                        fields.add(n.asText());
                    }
                });
            }
            String single = cfg.path("matchField").asText(null);
            if (StringUtils.isNotBlank(single)) {
                fields.add(single);
            }
        } catch (Exception e) {
            log.warn("List node {} config parse failed", node.getNodeCode());
        }
        return fields;
    }

    /** Parse referenced list DB ids: tolerates [2,4] array and "2,4" string forms */
    private static List<Integer> parseListDbIds(EngineNode node, ObjectMapper mapper) {
        List<Integer> ids = new ArrayList<>();
        String json = node.getNodeJson();
        if (StringUtils.isBlank(json)) {
            return ids;
        }
        try {
            JsonNode cfg = mapper.readTree(json);
            JsonNode arr = cfg.path("list_db_ids");
            if (arr.isArray()) {
                arr.forEach(n -> {
                    if (n.canConvertToInt()) {
                        ids.add(n.asInt());
                    }
                });
            } else {
                String single = cfg.path("list_db_id").asText(null);
                if (StringUtils.isNotBlank(single)) {
                    for (String p : single.split(",")) {
                        String t = p.trim();
                        if (!t.isEmpty()) {
                            try {
                                ids.add(Integer.parseInt(t));
                            } catch (NumberFormatException ignore) {
                                // Invalid id ignored
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("List node {} list DB reference parse failed", node.getNodeCode());
        }
        return ids;
    }
}
