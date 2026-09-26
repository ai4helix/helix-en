package com.helix.console.datamanage.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class LineageUsageItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    private String code;

    private String name;

    private Integer organId;

    private String refType;

    private String refDetail;

    private List<NodeRef> usedIn = new ArrayList<>();
}
