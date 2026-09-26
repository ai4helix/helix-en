package com.helix.engine.entity;

import lombok.Data;

import java.util.List;

@Data
public class DenyRules {
    private Integer isSerial;
    private List<RuleItem> rules;
}
