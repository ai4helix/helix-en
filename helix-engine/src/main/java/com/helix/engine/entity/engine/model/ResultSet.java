package com.helix.engine.entity.engine.model;

import lombok.Data;

import java.util.Date;

@Data
public class ResultSet {
    private Integer id;

    private String uid;

    private String pid;

    private Date createDatetime;

    private String result;

    private Integer engineId;

    private Integer engineVersion;

    private String uuid;

    private String engineName;

    private String engineCode;

    private Integer type;

    private Integer subVersion;

    private String scoreCardScore;

    private String batchNo;

    private String detailResult;

    private String input;

    private Date startDate;

    private Date endDate;

    private Date startTime;

    private String costTime;
}
