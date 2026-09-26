package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** BaseRsp */
@Data
public class BaseRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String rspCode = "0";
    private String rspInfo = "Success";
}
