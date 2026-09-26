package com.helix.console.engine.service;

import com.helix.console.engine.dto.GraphVO;
import com.helix.console.engine.dto.NodeMoveDTO;
import com.helix.console.engine.dto.NodeSaveDTO;
import com.helix.console.engine.dto.VersionSaveDTO;

/**
 * Decision flow (AntV X6 canvas) service. Replaces all capabilities of the legacy helix-rules {@code DecisionFlowController}.
 */
public interface DecisionFlowService {

    /**
     * Load the complete decision flow graph of the given version for the frontend {@code graph.fromJSON} rendering.
     */
    GraphVO loadGraph(Integer versionId);

    /**
     * Whole-graph save. The server performs incremental updates against existing nodes and validates the graph structure.
     */
    void saveGraph(VersionSaveDTO dto);

    /**
     * Create or update a single node.
     *
     * @return node id
     */
    Integer saveNode(NodeSaveDTO dto);

    /**
     * Delete a node, and clean up other nodes' downstream references to it and its knowledge relations.
     */
    void removeNode(Integer versionId, Integer nodeId);

    /**
     * Batch delete nodes.
     */
    void removeNodes(Integer versionId, java.util.List<Integer> nodeIds);

    /**
     * Node position and edge changes (drag/edge ops; the frontend submits after debouncing).
     */
    void moveAndLink(NodeMoveDTO dto);

    /**
     * Copy a node.
     *
     * @return new node id
     */
    Integer copyNode(Integer nodeId);

    /**
     * Validate the decision flow structure: exactly one start node required, no cycles, no dangling references, connected.
     */
    void validateGraph(Integer versionId);

    /**
     * Publish a version: set the given version to running state and set other versions of the same engine to undeployed.
     */
    void publishVersion(Integer versionId);

    /**
     * Save a version as a draft (copies nodes and edges).
     *
     * @return new version id
     */
    Integer saveAsDraft(Integer versionId);

    /**
     * Delete a version. A running (boot_state=1) version cannot be deleted;
     * cascades cleanup of nodes, edges, node-knowledge relations and publish artifacts.
     */
    void removeVersion(Integer versionId);
}
