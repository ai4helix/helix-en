package com.helix.console.platform.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RoleStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roleId;

    private String roleName;

    private String roleCode;

    private String roleDesc;

    private Long organId;

    private String organName;

    private Long userCount;

    private Integer status;
}
