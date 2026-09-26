package com.helix.console.datamanage.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class FieldDetailVO extends FieldVO {

    private static final long serialVersionUID = 1L;

    private List<FieldBrief> usedFields = new ArrayList<>();

    private List<FieldBrief> origFields = new ArrayList<>();

    private Integer referencedBy = 0;

    @Data
    public static class FieldBrief implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer id;
        private String fieldEn;
        private String fieldCn;
    }
}
