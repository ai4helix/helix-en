package com.helix.console.datamanage.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class LineageGraphDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private GraphNode version;

    private List<GraphNode> nodes = new ArrayList<>();

    private List<GraphEdge> edges = new ArrayList<>();

    private Integer nodeCount = 0;

    private Integer knowledgeCount = 0;

    private Integer fieldCount = 0;
}
