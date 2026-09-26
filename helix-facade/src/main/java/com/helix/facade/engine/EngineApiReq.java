package com.helix.facade.engine;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Decision engine invocation request (contract aligned with helix-engine's POST /engineApi/decision).
 */
@Data
public class EngineApiReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Engine code (required) */
    @NotBlank(message = "Engine code must not be blank")
    private String code;

    /** Application ID */
    private String pid;

    /** User ID */
    private String uid;

    /** Merchant number (reserved) */
    private String mchNo;

    /** Request timestamp (reserved) */
    private String ts;

    /** Random nonce (reserved) */
    private String nonce;

    /** Signature (reserved) */
    private String sign;

    /** Business input params: key is the English name of the field */
    private Map<String, Object> data = new HashMap<>();

    /** Whether to force a specific version (for gray release or replay) */
    private Integer versionId;
}
