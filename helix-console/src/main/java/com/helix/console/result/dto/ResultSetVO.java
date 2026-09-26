package com.helix.console.result.dto;

import com.helix.facade.engine.EngineApiRsp;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ResultSetVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    private String traceId;

    private String engineCode;

    private String engineName;

    private Integer engineVersion;

    private String result;

    private boolean pass;

    private boolean manualReview;

    private Integer score;

    private String pid;

    private String uid;

    private Integer type;

    private String batchNo;

    private LocalDateTime createdTime;

    private Map<String, Object> input;

    private List<EngineApiRsp.Trace> traces = new ArrayList<>();

    private List<String> hitDetails = new ArrayList<>();
}
