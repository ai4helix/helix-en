package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * Decision Flow edge (t_flow_edge, read-only): explicit edges between nodes within a version.
 *
 * <p>Source of truth for the flow topology: frozen into the artifact with {@code VersionInputs} at publish
 * time; engine navigation no longer parses the {@code t_engine_node.next_nodes} comma string
 * (dual-write kept as a rollback fallback).</p>
 */
@Data
@TableName("t_flow_edge")
public class FlowEdgeEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Integer versionId;

    /** Source node code (unique within the version) */
    private String fromCode;

    /** Target node code */
    private String toCode;

    /** Edge label (e.g. hit/miss/default) */
    private String label;

    /** Traversal priority when multiple edges share a source; smaller first */
    private Integer priority;

    /** Edge kind: 1 default / 2 condition hit / 3 condition miss */
    private Integer edgeKind;

    /** Logical delete 0/1 */
    private Integer deleted;
}
