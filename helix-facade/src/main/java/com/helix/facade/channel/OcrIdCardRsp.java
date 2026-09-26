package com.helix.facade.channel;

import lombok.Data;

@Data
public class OcrIdCardRsp {
    private String name;
    private String sex;
    private String nation;
    private String birth;
    private String address;
    private String idNum;
    private String authority;
    private String validDate;
    private String advancedInfo;
    private String requestId;
}