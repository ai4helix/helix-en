package com.helix.engine.node.impl;

import com.helix.engine.core.DecisionContext;
import com.helix.engine.entity.engine.model.EngineNode;
import com.helix.engine.node.NodeExecutor;
import com.helix.engine.node.NodeTypes;
import org.springframework.stereotype.Component;

/**
 * Start node: only records the trace.
 */
@Component
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public int supportType() {
        return NodeTypes.START;
    }

    @Override
    public void execute(DecisionContext ctx, EngineNode node) {
        DecisionContext.NodeTrace t = DecisionContext.newTrace(node);
        t.setMessage("Flow started");
        ctx.getTraces().add(t);
    }
}
