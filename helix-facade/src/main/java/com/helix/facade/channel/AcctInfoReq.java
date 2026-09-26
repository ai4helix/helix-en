package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** AcctInfoReq */
@Data
public class AcctInfoReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String acctNo;
}
