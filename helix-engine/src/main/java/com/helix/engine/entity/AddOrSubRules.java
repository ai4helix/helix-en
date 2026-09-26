package com.helix.engine.entity;

import lombok.Data;

import java.util.List;

@Data
public class AddOrSubRules {
    private Double threshold;
    private List<RuleItem> rules;
}
