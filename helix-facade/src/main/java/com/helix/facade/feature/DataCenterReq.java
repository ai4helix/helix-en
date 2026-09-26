package com.helix.facade.feature;

import com.helix.facade.engine.HelixOrder;
import com.helix.facade.engine.HelixUser;
import lombok.Data;

import java.io.Serializable;

/**
 * Feature data request (contract aligned with helix-feature's POST /dataCenter/feature).
 */
@Data
public class DataCenterReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private HelixUser helixUser;

    private HelixOrder helixOrder;

    private String nonce;

    private String ruleType;

    /** Comma-separated engine feature names */
    private String fields;

    private String sign;
}
