package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** HelixValidThreeRsp */
@Data
public class HelixValidThreeRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String flag;
    private String pvceValid;
    private String pvceCarrier;
}
