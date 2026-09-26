package com.helix.console.engine.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DecisionTableSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    private String code;

    private String name;

    private String description;

    /** FIRST / ALL */
    private String hitPolicy;

    private Integer engineId;

    private Integer parentId;

    private Integer status;

    private String remark;

    private List<ColumnDTO> columns;

    private List<RowDTO> rows;

    @Data
    public static class ColumnDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer colType;
        private String fieldCode;
        private String operator;
        private String title;
        private Integer seq;
    }

    @Data
    public static class RowDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer rowNo;
        private String resultType;
        private String resultValue;
        private Integer scoreValue;
        private Integer enabled;
        private String remark;
        private List<CellDTO> cells;
    }

    @Data
    public static class CellDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer colIndex;
        private String value;
    }
}
