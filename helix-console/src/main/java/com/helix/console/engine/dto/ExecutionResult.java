package com.helix.console.engine.dto;

import com.helix.facade.engine.EngineApiRsp;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExecutionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String traceId;

    private String engineCode;

    private String engineName;

    private Integer versionId;

    private String result;

    private boolean pass;

    private boolean manualReview;

    private int score;

    private long costMs;

    private List<EngineApiRsp.HitRule> hitRules = new ArrayList<>();

    private List<EngineApiRsp.Trace> traces = new ArrayList<>();
}
