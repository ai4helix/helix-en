package com.helix.console.system.security;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String account;

    private String nickName;

    private Long organId;

    private String organName;

    private Integer userType;

    private List<String> roleCodes = new ArrayList<>();

    private List<String> permissions = new ArrayList<>();

    public boolean isSuperAdmin() {
        return organId != null && organId == 1L;
    }

    public boolean isAdminUser() {
        return userType != null && userType == 1;
    }

    public boolean hasRole(String roleCode) {
        return roleCodes != null && roleCodes.contains(roleCode);
    }
}
