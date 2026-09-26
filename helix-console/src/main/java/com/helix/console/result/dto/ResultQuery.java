package com.helix.console.result.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ResultQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private String engineCode;

    private String result;

    private String uuid;

    private String pid;

    private String batchNo;

    private String startTime;

    private String endTime;
}
