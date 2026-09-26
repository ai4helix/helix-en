package com.helix.console.datamanage.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class FieldSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fieldEn;

    private String fieldCn;

    private Integer fieldTypeid;

    private Integer catalogId;

    private Integer valueType;

    private String valueScope;

    private Integer isDerivative;

    private Integer isOutput;
}
