package com.helix.console.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class DryRunReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<RuleConditionDTO> conditions;

    private Integer isNon;

    private Map<String, Object> variables;
}
