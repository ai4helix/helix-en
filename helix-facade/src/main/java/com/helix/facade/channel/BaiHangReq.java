package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** BaiHangReq */
@Data
public class BaiHangReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String prodCd;
    private String name;
    private String certType;
    private String certNo;
    private String mobile;
    private String encryptType;
    private String applyDate;
}
