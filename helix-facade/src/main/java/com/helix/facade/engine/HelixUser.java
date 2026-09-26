package com.helix.facade.engine;

import lombok.Data;

import java.io.Serializable;

/**
 * Credit applicant elements (shared across services).
 */
@Data
public class HelixUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private String userName;
    private String education;
    private String phone;
    private String companyPhone;
    private String cardNo;
    private String certificateType;
    private String certificateNo;
    private String marital;
}
