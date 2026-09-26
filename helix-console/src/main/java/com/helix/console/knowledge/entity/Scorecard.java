package com.helix.console.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_scorecard")
public class Scorecard implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private String code;

    private String description;

    private String version;

    private Integer parentId;

    private Integer createdBy;

    private Integer organId;

    private Integer engineId;

    private Integer type;

    private Integer status;

    private String score;

    private LocalDateTime createdTime;
    private Integer deleted;

    private LocalDateTime updatedTime;
}
