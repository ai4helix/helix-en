package com.helix.facade.channel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;


/** PayPlanQueryRsp */
@Data

public class PayPlanQueryRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String brcShName0;
    private String fileFlag;
    private String fileName;
    private String tranName;
    private String brcLvl0;
    private String brcType0;
}
