package com.helix.facade.engine;

import lombok.Data;

import java.io.Serializable;

/**
 * Credit order elements (shared across services).
 */
@Data
public class HelixOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private String orderType;
    private String orderInfo;
}
