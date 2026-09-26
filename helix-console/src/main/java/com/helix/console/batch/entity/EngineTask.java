package com.helix.console.batch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_engine_task")
public class EngineTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long organId;

    private String taskCode;

    private String taskName;

    private String engineCode;

    private String keyField;

    private String description;

    private Integer status;

    private Long createdBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Integer deleted;

    public String statusText() {
        return status != null && status == 0 ? "Disabled" : "Enabled";
    }
}
