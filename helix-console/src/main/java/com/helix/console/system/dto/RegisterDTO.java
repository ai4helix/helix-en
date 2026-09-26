package com.helix.console.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * Tenant phone registration request.
 *
 * <p>A successful registration provisions the tenant: creates the organization +
 * the "tenant administrator" role binding + the tenant administrator user
 * (account = phone number, user_type=2); login afterwards uses phone + password.</p>
 */
@Data
public class RegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Phone number must not be blank")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "Invalid phone number format")
    private String phone;

    @NotBlank(message = "Captcha must not be blank")
    @Pattern(regexp = "^\\d{6}$", message = "Captcha must be 6 digits")
    private String code;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, max = 32, message = "Password must be 8-32 characters")
    private String password;

    @NotBlank(message = "Organization name must not be blank")
    @Size(max = 64, message = "Organization name is too long")
    private String orgName;

    /** Account nickname; defaults to "organization name + Administrator" */
    @Size(max = 64, message = "Nickname is too long")
    private String nickName;
}
