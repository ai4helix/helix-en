package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Decision Table rule row, corresponding to {@code t_dt_row}. */
@Data
@TableName("t_dt_row")
public class DtRowEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer tableId;

    /** Row number (starting at 1); FIRST policy adjudicates in this order */
    private Integer rowNo;

    /** Row conclusion (DENY/MANUAL/PASS/…, nullable = pure output row) */
    private String resultType;

    /** Row output value (business code, e.g. 1000,13) */
    private String resultValue;

    /** Readable expression of this row's conditions (generated on save) */
    private String expression;

    private Integer scoreValue;

    /** Row enabled 0/1 */
    private Integer enabled;

    private String remark;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Integer deleted;
}
