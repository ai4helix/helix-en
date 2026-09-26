package com.helix.console.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_feedback")
public class Feedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer organId;

    private Long userId;

    private String username;

    private Integer category;

    private String content;

    private String images;

    private Integer status;

    private String reply;

    private String handledBy;

    private LocalDateTime handledTime;

    private LocalDateTime createdTime;
}
