package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** LocationInfoRsp */
@Data
public class LocationInfoRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String province;
    private String provinceCode;
    private String city;
    private String cityCode;
    private String region;
    private String regionCode;
    private String error;
}
