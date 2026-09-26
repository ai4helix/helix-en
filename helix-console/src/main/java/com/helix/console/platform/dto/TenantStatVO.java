package com.helix.console.platform.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class TenantStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long organId;

    private String name;

    private String code;

    private Long userCount;

    private Long roleCount;

    private Long decisionTotal;

    private Long decisionToday;

    private Long passCount;

    private Long rejectCount;

    private Long manualCount;

    private Double passRate;

    private Date lastDecisionTime;

    private Long batchCount;

    private Long batchRows;

    private Long batchRowsOk;
}
