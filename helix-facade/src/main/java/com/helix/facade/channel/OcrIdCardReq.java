package com.helix.facade.channel;

import lombok.Data;

@Data
public class OcrIdCardReq {
    private String imageBase64;
    private String cardSide;
    private String sign;
}