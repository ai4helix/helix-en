package com.helix.console.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;
    private String code;
    private String description;
    private Integer priority;
    private Integer parentId;
    private Integer type;
    private Integer engineId;
    private Integer organId;
    private Integer status;
    private Integer ruleType;
    private Integer ruleAudit;
    private Integer score;
    private Integer isNon;

    private String content;

    private java.time.LocalDateTime createdTime;

    private java.time.LocalDateTime updatedTime;

    private LocalDateTime created;
    private LocalDateTime updated;

    private String parentName;

    private List<String> engineNames = new ArrayList<>();

    private List<RuleConditionDTO> conditions = new ArrayList<>();

    private Integer showType = 0;

    private List<RuleVO> children = new ArrayList<>();
}
