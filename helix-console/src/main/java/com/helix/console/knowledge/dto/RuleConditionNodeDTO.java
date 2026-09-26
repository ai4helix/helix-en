package com.helix.console.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class RuleConditionNodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer nodeType;
    private String fieldCode;
    private String operator;
    private String value;
    private List<RuleConditionNodeDTO> children = new ArrayList<>();
}
