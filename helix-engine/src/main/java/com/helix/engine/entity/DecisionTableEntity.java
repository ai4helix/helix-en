package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Decision Table main table, corresponding to {@code t_decision_table} (v3, inspired by Camunda DMN).
 *
 * <p>Matrix-style rules of "condition columns × rule rows": each column defines one input dimension
 * (field + default operator), each row is one rule (cells in each column give the values; on hit
 * {@code resultValue} is output). Compared with the inline conditions array of decision nodes:
 * reusable, independently testable, explicit hit policy.</p>
 */
@Data
@TableName("t_decision_table")
public class DecisionTableEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String code;

    private String name;

    private String description;

    /** Hit policy: FIRST first hit / ALL all / PRIORITY by priority / COLLECT collect all */
    private String hitPolicy;

    private Integer engineId;

    private Integer parentId;

    /** 0 draft / 1 enabled / -1 deleted */
    private Integer status;

    private String remark;

    private Long createdBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Integer deleted;
}
