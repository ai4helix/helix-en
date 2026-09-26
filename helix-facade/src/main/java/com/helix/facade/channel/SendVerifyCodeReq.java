package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** SendVerifyCodeReq */
@Data
public class SendVerifyCodeReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String idNo;
    private String mobileNo;
    private String bankCardNo;
}
