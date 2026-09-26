package com.helix.facade.channel;

import lombok.Data;

import java.util.List;

@Data
public class OcrBankCardRsp {
    private String cardNo;
    private String bankInfo;
    private String validDate;
    private String cardType;
    private String cardName;
    private String borderCutImage;
    private String cardNoImage;
    private List<String> warningCode;
    private String qualityValue;
    private String requestId;
}