package com.helix.console.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class TreeNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    private String name;

    private Integer parentId;

    private Integer type;

    private Integer treeType;

    private Integer engineId;

    private Integer count = 0;

    private Boolean system = false;

    private List<TreeNodeVO> children = new ArrayList<>();
}
