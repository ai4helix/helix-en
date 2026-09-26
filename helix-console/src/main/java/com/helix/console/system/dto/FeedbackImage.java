package com.helix.console.system.dto;

import lombok.Data;

@Data
public class FeedbackImage {

    private byte[] content;

    private String contentType;

    public FeedbackImage(byte[] content, String contentType) {
        this.content = content;
        this.contentType = contentType;
    }
}
