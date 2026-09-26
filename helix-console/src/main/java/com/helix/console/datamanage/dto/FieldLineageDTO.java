package com.helix.console.datamanage.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class FieldLineageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer fieldId;
    private String fieldEn;
    private String fieldCn;

    private List<LineageUsageItem> rules = new ArrayList<>();
    private List<LineageUsageItem> scorecards = new ArrayList<>();
    private List<LineageUsageItem> decisionTables = new ArrayList<>();
    private List<LineageUsageItem> listDbs = new ArrayList<>();

    public int getTotal() {
        return rules.size() + scorecards.size() + decisionTables.size() + listDbs.size();
    }
}
