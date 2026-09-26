package com.helix.console.batch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_indicator_batch")
public class IndicatorBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long organId;

    private String name;

    private String engineCode;

    private String keyField;

    private Integer status;

    private Integer totalRows;

    private Integer successRows;

    private Integer failRows;

    private String fileName;

    private String errorMsg;

    private Long createdBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private LocalDateTime finishedTime;

    public String statusText() {
        if (status == null) {
            return "Unknown";
        }
        switch (status) {
            case 0:
                return "Pending";
            case 1:
                return "Running";
            case 2:
                return "Completed";
            case 3:
                return "Failed";
            default:
                return "Unknown";
        }
    }
}
