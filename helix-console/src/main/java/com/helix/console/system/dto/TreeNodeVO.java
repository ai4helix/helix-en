package com.helix.console.system.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class TreeNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String code;

    private Long parentId;

    private String url;

    private String icon;

    private String description;

    private Integer status;

    private List<TreeNodeVO> children = new ArrayList<>();
}
