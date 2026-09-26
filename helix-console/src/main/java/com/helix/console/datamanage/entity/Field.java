package com.helix.console.datamanage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_field")
public class Field implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String fieldEn;

    private String fieldCn;

    private Integer fieldTypeid;

    private Integer catalogId;

    private Integer valueType;

    private String valueScope;

    private Integer isDerivative;

    private Integer isOutput;

    private Integer isCommon;

    private Integer organId;

    private Integer status;

    private String formula;

    private String formulaShow;

    private String usedFieldid;

    private String origFieldid;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
