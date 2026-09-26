package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Scorecard entity, corresponding to {@code t_scorecard}.
 *
 * <p><b>Note the field-to-column mapping</b>: this table's column names differ from common conventions —
 * the creator column is {@code created_by} rather than {@code author}, and the time columns are
 * {@code created_time} / {@code updated_time} rather than {@code created} / {@code updated},
 * hence the explicit {@code @TableField} bindings.</p>
 */
@Data
@TableName("t_scorecard")
public class ScorecardEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private String code;

    private String description;

    private String version;

    private Integer parentId;

    /** Creator, column created_by (not author) */
    @TableField("created_by")
    private Integer createdBy;

    private Integer engineId;

    /** 0 system 1 organization 2 engine */
    private Integer type;

    /** 0 disabled 1 enabled -1 deleted */
    private Integer status;

    /** Score dimension configuration JSON */
    private String score;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    private LocalDateTime updatedTime;

    /** Logical delete, consistent with the project-wide convention */
    private Integer deleted;
}
