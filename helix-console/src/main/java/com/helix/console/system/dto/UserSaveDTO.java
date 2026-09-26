package com.helix.console.system.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * User save request.
 */
@Data
public class UserSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long organId;

    /** User type: 1 platform admin user, 2 SaaS organization user; defaults by organization */
    private Integer userType;

    private String employeeId;

    @NotBlank(message = "Account must not be blank")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{3,15}$",
            message = "Account must start with a letter, be 4-16 characters, and contain only letters, digits and underscores")
    private String account;

    @NotBlank(message = "Name must not be blank")
    private String nickName;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "Invalid phone number format")
    private String cellphone;

    private String qq;

    /** Role id list */
    private List<Long> roleIds = new ArrayList<>();

    /** Initial password on create; defaults to the default password when blank */
    private String password;
}
