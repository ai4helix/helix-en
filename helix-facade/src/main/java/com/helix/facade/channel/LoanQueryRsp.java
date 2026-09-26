package com.helix.facade.channel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;


/** LoanQueryRsp */
@Data

public class LoanQueryRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String brcShName;
    private String fileFlag;
    private String tranName;
    private String brcLvl;
    private String brcType;
    private String brcAttr;
}
