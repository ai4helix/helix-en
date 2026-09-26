package com.helix.console.batch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_indicator_batch_item")
public class IndicatorBatchItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long organId;

    private Integer rowNo;

    private String bizKey;

    private String dataJson;

    private Integer status;

    private String traceId;

    private String resultText;

    private Integer totalScore;

    private String hitRules;

    private String errorMsg;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
