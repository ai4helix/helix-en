package com.helix.console.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_rule_condition")
public class RuleCondition implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer ruleId;

    private Long parentId;

    private Integer nodeType;

    private String fieldCode;

    private String operator;

    private String value;

    private Integer sortNo;

    private Integer depth;

    private String remark;

    private Long createdBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Integer deleted;
}
