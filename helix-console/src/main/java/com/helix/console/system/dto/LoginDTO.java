package com.helix.console.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * Login request.
 */
@Data
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Account must not be blank")
    private String account;

    @NotBlank(message = "Password must not be blank")
    private String password;
}
