package com.helix.engine.entity.engine.model;

import lombok.Data;

import java.util.Map;

@Data
public class Rule {
    private String refused;
    private String code;
    private String policyName;
    private String desc;
    private String Strtus;
    private Map<String, String> fields;
}
