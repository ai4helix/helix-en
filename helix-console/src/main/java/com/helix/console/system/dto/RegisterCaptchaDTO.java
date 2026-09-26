package com.helix.console.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * Tenant registration captcha send request.
 */
@Data
public class RegisterCaptchaDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Phone number must not be blank")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "Invalid phone number format")
    private String phone;
}
