package com.helix.console.result.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class BatchTestResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String batchNo;

    private String engineCode;

    private int total;

    private int passCount;

    private int rejectCount;

    private int manualCount;

    private int errorCount;

    private double passRate;

    private long costMs;

    private List<ResultSetVO> details = new ArrayList<>();
}
