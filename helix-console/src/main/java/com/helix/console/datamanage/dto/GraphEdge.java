package com.helix.console.datamanage.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GraphEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    private String source;

    private String target;

    /** FIELD_KNOWLEDGE / KNOWLEDGE_NODE / NODE_VERSION */
    private String kind;
}
