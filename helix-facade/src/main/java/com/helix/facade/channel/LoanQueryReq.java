package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** LoanQueryReq */
@Data
public class LoanQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String dueBillNo;
    private String termDate;
    private String termTime;
    private String termSeq;
    private String termId;
    private String brc;
    private String teller;
}
