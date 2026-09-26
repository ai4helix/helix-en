package com.helix.console.system.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class LoginResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private Long userId;

    private String account;

    private String nickName;

    private Long organId;

    private Integer userType;

    private String organName;

    private List<String> roles = new ArrayList<>();

    private List<String> permissions = new ArrayList<>();
}
