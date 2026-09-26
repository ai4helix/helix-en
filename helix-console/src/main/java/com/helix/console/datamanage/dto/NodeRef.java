package com.helix.console.datamanage.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class NodeRef implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer engineId;
    private String engineCode;
    private String engineName;

    private Integer versionId;
    private String versionLabel;

    private Integer nodeId;
    private String nodeCode;
    private String nodeName;
}
