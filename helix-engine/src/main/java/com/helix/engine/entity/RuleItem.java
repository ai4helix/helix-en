package com.helix.engine.entity;

import lombok.Data;

@Data
public class RuleItem {
    private String code;
    private String name;
    private int id;
    private int priority;
    private int parentId;
}
