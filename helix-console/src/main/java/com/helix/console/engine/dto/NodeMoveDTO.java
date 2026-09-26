package com.helix.console.engine.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request for canvas position/edge changes.
 *
 * <p>X6 dragging and edge operations are frequent; the frontend should debounce (300ms recommended) before batch submission.</p>
 */
@Data
public class NodeMoveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "Version ID must not be empty")
    private Integer versionId;

    private List<NodePosition> nodes;

    private List<EdgeChange> edges;

    @Data
    public static class NodePosition implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer nodeId;
        private BigDecimal nodeX;
        private BigDecimal nodeY;
    }

    /**
     * Edge change. {@code action = ADD} adds an edge, {@code REMOVE} deletes an edge.
     *
     * <p>{@code label}/{@code kind} — branch semantics (Pass/Reject/Manual Review)
     * are persisted via the automatic persistence channel, no longer relying solely on the full-save fallback.</p>
     */
    @Data
    public static class EdgeChange implements Serializable {
        private static final long serialVersionUID = 1L;
        private String action;
        private String sourceNodeCode;
        private String targetNodeCode;
        /** Edge label: Pass/Reject/Manual Review (default = Pass) */
        private String label;
        /** Edge type: 1 = main chain (Pass), 0 = conditional branch */
        private Integer kind;
    }
}
