package com.helix.console.knowledge.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RuleMoveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer parentId;
}
