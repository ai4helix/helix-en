package com.helix.console.knowledge.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RuleBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    private String name;

    private String code;

    private String content;

    private String resultTypeV2;

    private Integer scoreValue;

    private Integer priority;
}
