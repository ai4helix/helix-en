package com.helix.console.engine.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class GraphVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer versionId;

    private Double zoom;

    private List<CellVO> cells = new ArrayList<>();

    @Data
    public static class CellVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String id;

        private String shape;

        private Position position;

        private Size size;

        private Terminal source;

        private Terminal target;

        private NodeData data;

        private String label;

        private Integer kind;
    }

    @Data
    public static class Position implements Serializable {
        private static final long serialVersionUID = 1L;
        private Double x;
        private Double y;
    }

    @Data
    public static class Size implements Serializable {
        private static final long serialVersionUID = 1L;
        private Double width;
        private Double height;
    }

    @Data
    public static class Terminal implements Serializable {
        private static final long serialVersionUID = 1L;
        private String cell;
        private String port;
    }

    @Data
    public static class NodeData implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer nodeId;

        private String nodeName;

        private String nodeCode;

        private Integer nodeType;

        private Integer nodeOrder;

        private Object nodeJson;

        private String nodeScript;

        private List<KnowledgeRef> knowledge = new ArrayList<>();

        private List<String> nextNodes = new ArrayList<>();

        private List<Integer> innerListDbs = new ArrayList<>();

        private List<Integer> outerListDbs = new ArrayList<>();
    }

    @Data
    public static class KnowledgeRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer knowledgeId;
        private Integer knowledgeType;
        private String name;
        private String code;
    }
}
