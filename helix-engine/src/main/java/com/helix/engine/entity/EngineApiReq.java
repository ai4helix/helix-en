package com.helix.engine.entity;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Decision request.
 *
 * <p>The request body keeps the same field structure as the original service, so callers need no changes.
 * Signature fields (sign/mchNo/ts/nonce) are retained but currently not verified;
 * signature checking can be implemented at the filter layer if needed.</p>
 */
@Data
public class EngineApiReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Engine code, required */
    @NotBlank(message = "Engine code must not be blank")
    private String code;

    /** Application identifier */
    private String pid;

    /** User identifier */
    private String uid;

    /** Merchant number (reserved) */
    private String mchNo;

    /** Request timestamp (reserved) */
    private String ts;

    /** Nonce string (reserved) */
    private String nonce;

    /** Signature (reserved) */
    private String sign;

    /** Business inputs: key is the field's English name */
    private Map<String, Object> data = new HashMap<>();

    /** Whether to force a specific version (for gray release or replay) */
    private Integer versionId;
}
