package com.helix.console.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class FeedbackAdminVO {

    private Long id;

    private Integer organId;

    private String organName;

    private Long userId;

    private String username;

    private Integer category;

    private String content;

    private List<String> images = new ArrayList<>();

    private Integer status;

    private String reply;

    private String handledBy;

    private LocalDateTime handledTime;

    private LocalDateTime createdTime;
}
