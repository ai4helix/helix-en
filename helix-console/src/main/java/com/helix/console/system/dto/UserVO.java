package com.helix.console.system.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long organId;

    private Integer userType;

    private String organName;

    private String employeeId;

    private String account;

    private String nickName;

    private String email;

    private String cellphone;

    private String qq;

    private Integer status;

    private LocalDateTime latestTime;

    private String latestIp;

    private LocalDateTime birth;

    private String author;

    private List<Long> roleIds = new ArrayList<>();

    private List<String> roleNames = new ArrayList<>();
}
