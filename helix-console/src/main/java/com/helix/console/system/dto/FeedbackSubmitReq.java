package com.helix.console.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FeedbackSubmitReq {

    private Integer category;

    private String content;

    private List<String> images = new ArrayList<>();
}
