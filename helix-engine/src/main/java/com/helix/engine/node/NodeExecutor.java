package com.helix.engine.node;

import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.engine.model.EngineNode;

/**
 * Node executor SPI: one implementation per node type, routed by {@code node_type}.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Executors never throw exceptions — failures are written to the trace (message), and the orchestrator decides whether to terminate;</li>
 *   <li>Configuration and knowledge (rule plans/scorecard/rule names) are obtained from prefetched {@code EngineSnapshot} content;
 *       the execution path neither queries the DB nor parses JSON.</li>
 * </ul>
 */
public interface NodeExecutor {

    /**
     * The node type supported by this executor ({@link NodeTypes} constant).
     */
    int supportType();

    /**
     * Execute the node.
     */
    void execute(DecisionContext ctx, EngineNode node);
}
