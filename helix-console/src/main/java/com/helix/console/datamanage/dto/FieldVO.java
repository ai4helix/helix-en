package com.helix.console.datamanage.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class FieldVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    private String fieldEn;

    private String fieldCn;

    private Integer fieldTypeid;

    private String fieldTypeName;

    private Integer catalogId;

    private String catalogName;

    private Integer valueType;

    private String valueScope;

    private Integer status;

    private Integer isDerivative;

    private Integer isOutput;

    public String toFieldKey() {
        return id + "|" + fieldEn;
    }
}
