package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** VerifyCodeReq */
@Data
public class VerifyCodeReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String flowId;
    private String authCode;
}
