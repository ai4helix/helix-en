package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Decision Table cell, corresponding to {@code t_dt_cell}. */
@Data
@TableName("t_dt_cell")
public class DtCellEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer tableId;

    private Integer rowId;

    private Integer colId;

    /** Cell value; condition columns hold values/ranges (comma-separated), result columns hold the output value */
    private String cellValue;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Integer deleted;
}
