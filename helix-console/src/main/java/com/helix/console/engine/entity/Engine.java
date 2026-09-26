package com.helix.console.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_engine")
public class Engine implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String code;

    private String name;

    private String description;

    private Integer status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Integer createdBy;

    private Integer organId;
}
