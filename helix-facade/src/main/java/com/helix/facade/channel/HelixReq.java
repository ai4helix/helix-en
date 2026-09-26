package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** HelixReq */
@Data
public class HelixReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reqId;
    private String prodCd;
    private String qryRsn;
    private String certNo;
    private String certType;
    private String certName;
    private String mobile;
}
