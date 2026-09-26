package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * List DB data entry (t_list_entry): the data source hit by list nodes.
 *
 * <p>The legacy design stored data in the now-deleted organ_* dynamic wide tables; from v3 this is the
 * standard storage: one entry per row, with validity period and enable/disable support. Once loaded into
 * the snapshot, the decision path performs zero DB queries.</p>
 */
@Data
@TableName("t_list_entry")
public class ListEntryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Owning list t_list_db.id */
    private Integer listId;

    /** Entry value (ID card number/mobile number/device ID/region code, etc.) */
    private String entryValue;

    /** Value type (IDCARD/MOBILE/DEVICE/IP...), defaults to inheriting the list definition */
    private String entryType;

    /** Remark (e.g. blacklisting reason) */
    private String remark;

    /** Effective from (null = immediately) */
    private LocalDateTime effectiveFrom;

    /** Effective to (null = indefinitely) */
    private LocalDateTime effectiveTo;

    /** 1 enabled / 0 disabled */
    private Integer status;

    private Long createdBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    /** Logical delete 0/1 */
    private Integer deleted;
}
