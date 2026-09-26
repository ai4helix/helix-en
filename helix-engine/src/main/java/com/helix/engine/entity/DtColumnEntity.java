package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Decision Table column definition, corresponding to {@code t_dt_column}. */
@Data
@TableName("t_dt_column")
public class DtColumnEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer tableId;

    /** 1 condition column / 2 result column */
    private Integer colType;

    /** Condition column: input field code */
    private String fieldCode;

    /** Condition column: default operator (overridable per cell) */
    private String operator;

    private String title;

    private Integer seq;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Integer deleted;
}
