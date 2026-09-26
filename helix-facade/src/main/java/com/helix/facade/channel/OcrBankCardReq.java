package com.helix.facade.channel;

import lombok.Data;

@Data
public class OcrBankCardReq {
    private String imageBase64;
    private String sign;
}