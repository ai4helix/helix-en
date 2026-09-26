package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** SendMsgReq */
@Data
public class SendMsgReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String mobile;
    private String content;
    private String signName;
    private String templateCode;
    private String templateParam;
}
