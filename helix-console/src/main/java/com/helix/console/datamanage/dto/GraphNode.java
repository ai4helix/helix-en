package com.helix.console.datamanage.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GraphNode implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    /** VERSION / NODE / KNOWLEDGE / FIELD */
    private String type;

    private String label;

    private String subType;

    private Long refId;

    private String refType;
}
