package com.helix.console.result.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_decision_log")
public class DecisionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Integer engineId;

    private String engineCode;

    private Long organId;

    private Integer versionId;

    private Long publishId;

    private Integer shadow;

    private String shadowOf;

    private String pid;

    private String uid;

    private Integer resultType;

    private Integer totalScore;

    private Integer rejected;

    private Integer costMs;

    private String inputJson;

    private String hitsJson;

    private String tracesJson;

    private LocalDateTime createdTime;

    private Integer deleted;
}
