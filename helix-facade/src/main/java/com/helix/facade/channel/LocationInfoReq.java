package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** LocationInfoReq */
@Data
public class LocationInfoReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String latitude;
    private String longitude;
}
