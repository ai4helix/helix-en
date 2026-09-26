package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Decision Flow publish artifact, corresponding to {@code t_flow_publish}.
 *
 * <p>Core v3 mechanism: <b>publish means freeze</b>. At publish time the version's executable configuration
 * (nodes/rules/condition ASTs/scorecards/relation fallback) is serialized into an immutable JSON artifact;
 * when the engine loads the snapshot it restores from the artifact instead of querying live data —
 * afterwards any modification to rules/scorecards in console must be re-published to take effect.</p>
 *
 * <p>The same version can be published multiple times ({@code publishSeq} increments), with older artifacts
 * set to status=0; {@code artifactSha256} is used for tamper protection and diffing between two publishes.</p>
 */
@Data
@TableName("t_flow_publish")
public class FlowPublishEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** The published version t_engine_version.id */
    private Integer versionId;

    /** Full executable configuration snapshot JSON */
    private String artifact;

    /** Artifact SHA-256 digest */
    private String artifactSha256;

    /** Which publish this is for the same version, incrementing */
    private Integer publishSeq;

    /** 1 effective / 0 superseded by a newer publish */
    private Integer status;

    /** Gray release traffic weight 0-100; when multiple tracks coexist, traffic is split normalized by weight */
    private Integer trafficWeight;

    /**
     * Shadow mode: 1 evaluates and records decision logs only, returns no result and does not participate
     * in traffic splitting. After shadow is turned off, splitting resumes by traffic_weight.
     */
    private Integer shadow;

    private String remark;

    private Long publishedBy;

    private LocalDateTime publishedTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Integer deleted;
}
