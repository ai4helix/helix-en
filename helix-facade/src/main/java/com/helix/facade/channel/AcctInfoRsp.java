package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** AcctInfoRsp */
@Data
public class AcctInfoRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String acctName;
    private String certType;
    private String certNo;
}
