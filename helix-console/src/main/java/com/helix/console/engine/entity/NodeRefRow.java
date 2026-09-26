package com.helix.console.engine.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class NodeRefRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer nodeId;
    private String nodeCode;
    private String nodeName;

    private Integer versionId;
    private Integer version;
    private Integer subVersion;

    private Integer engineId;
    private String engineCode;
    private String engineName;

    public String versionLabel() {
        return version + "." + subVersion;
    }
}
