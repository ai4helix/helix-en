package com.helix.console.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_engine_version")
public class EngineVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer engineId;

    private Integer version;

    private Integer subVersion;

    private Integer bootState;

    private Integer status;

    private Integer layout;

    private LocalDateTime createdTime;
}
