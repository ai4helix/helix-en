package com.helix.engine.config;

import com.helix.engine.entity.RuleConditionEntity;
import com.helix.engine.entity.RuleEntity;
import com.helix.engine.entity.ScorecardEntity;
import com.helix.engine.entity.engine.model.EngineNode;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All raw inputs required to build the snapshot for a single version.
 *
 * <p>The key contract of "publish means freeze": {@code EngineSnapshotLoader} can either collect it
 * from the <b>live database</b> (also serving as raw material for the publish artifact) or
 * deserialize it from the <b>published artifact JSON</b> (the main path). Both paths produce
 * exactly the same object, and the subsequent snapshot assembly code is fully shared — guaranteeing
 * that "load from artifact" and "load from DB" behave byte-for-byte identically.</p>
 *
 * <p>All fields are raw rows "resolved within the version":</p>
 * <ul>
 *   <li>{@code nodes}: all nodes in the version (nodeCode → row);</li>
 *   <li>{@code rulesById}: enabled rules referenced by this version (from both the node_json direct link and the relation-table fallback);</li>
 *   <li>{@code condByRule}: condition AST rows of the above rules (adjacency list);</li>
 *   <li>{@code scorecardById}: scorecards referenced by scorecard nodes of this version;</li>
 *   <li>{@code relRuleIdsByNode}: relation-table fallback bindings (nodeId → rule id list), used when node_json has no rules.</li>
 * </ul>
 */
@Data
public class VersionInputs {

    /** Nodes in the version (nodeCode → row) */
    private Map<String, EngineNode> nodes = new LinkedHashMap<>();

    /** Enabled rules referenced by this version */
    private Map<Integer, RuleEntity> rulesById = new LinkedHashMap<>();

    /** Rule condition AST rows (ruleId → row list) */
    private Map<Integer, List<RuleConditionEntity>> condByRule = new LinkedHashMap<>();

    /** Scorecards referenced by scorecard nodes (cardId → row) */
    private Map<Integer, ScorecardEntity> scorecardById = new LinkedHashMap<>();

    /** Relation-table fallback bindings (nodeId → rule id list) */
    private Map<Integer, List<Integer>> relRuleIdsByNode = new LinkedHashMap<>();

    /** Decision tables referenced by decision nodes (tableId → full table data) */
    private Map<Integer, DecisionTableBundle> decisionTableById = new LinkedHashMap<>();

    /**
     * Version flow topology (fromCode → ordered toCode list).
     *
     * <p>The source of truth is t_flow_edge (one row per edge), frozen with the artifact at publish time;
     * when null/empty, snapshot assembly falls back to deriving it from the nextNodes comma string of nodes
     * (compatible with legacy artifacts), with unchanged navigation behavior.</p>
     */
    private Map<String, List<String>> edges;

    /**
     * Full decision table data bundle: main table + column definitions + rule rows + cells.
     *
     * <p>Frozen as a whole as part of the publish artifact; at load time each row's condition
     * expression is compiled from it.</p>
     */
    @Data
    public static class DecisionTableBundle {

        private com.helix.engine.entity.DecisionTableEntity table;

        private List<com.helix.engine.entity.DtColumnEntity> columns = new java.util.ArrayList<>();

        private List<com.helix.engine.entity.DtRowEntity> rows = new java.util.ArrayList<>();

        /** rowId → (colId → cellValue) */
        private Map<Integer, Map<Integer, String>> cellsByRow = new LinkedHashMap<>();
    }
}
