package com.helix.console.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_rule")
public class Rule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private String code;

    private String description;

    private Integer priority;

    private Integer parentId;

    private Integer createdBy;

    private Integer organId;

    private Integer engineId;

    private Integer status;

    private Integer type;

    private Integer isNon;

    private String content;

    private String definition;

    private LocalDateTime createdTime;
    private Integer deleted;

    private LocalDateTime updatedTime;

    private Integer ruleType;

    private Integer ruleAudit;

    private Integer score;

    private String lastLogical;


    private String resultTypeV2;

    private Integer scoreValue;

    private Integer conditionVersion;
}
