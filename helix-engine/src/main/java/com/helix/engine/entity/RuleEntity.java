package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Rule entity. Corresponds to {@code t_rule}.
 *
 * <p>Note the distinction from {@code engine.model.Rule}: the latter is the runtime result DTO of rule
 * execution ({@code refused/code/policyName/fields}), not a table entity.</p>
 *
 * <p>Named {@code RuleEntity} here rather than {@code Rule},
 * to distinguish it from the runtime result DTO.</p>
 */
@Data
@TableName("t_rule")
public class RuleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private String code;

    private Integer priority;

    private Integer parentId;

    private Integer createdBy;

    private Integer organId;

    private Integer engineId;

    /** 0 disabled 1 enabled -1 deleted */
    private Integer status;

    /** Logical delete 0/1 */
    private Integer deleted;

    /** 0 system 1 organization 2 engine */
    private Integer type;

    /** Negate the whole condition */
    private Integer isNon;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    /** 0 hard reject 1 add/subtract score */
    private Short ruleType;

    private Short ruleAudit;

    private Integer score;

    private String lastLogical;

    private String description;

    /** Original rule definition text (not used by current execution, kept for comparison) */
    private String content;

    /** Structured definition JSON (kept for comparison and rollback) */
    private String definition;

    // ===== Condition AST fields =====

    /** Strongly-typed result: PASS / DENY / MANUAL / ADD_SCORE / SUB_SCORE */
    private String resultTypeV2;

    /** Score value for scoring results (positive adds, negative subtracts) */
    private Integer scoreValue;

    /** Condition storage version: 1 early definition / 2 AST table */
    private Integer conditionVersion;
}
