package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** PayPlanQueryReq */
@Data
public class PayPlanQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fileFlag;
    private String fileName;
    private String oprType;
    private String dueBillNo;
    private String termDate;
    private String termTime;
    private String termSeq;
}
