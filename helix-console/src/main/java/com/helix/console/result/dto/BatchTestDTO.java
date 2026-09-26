package com.helix.console.result.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Batch test input. Submits a batch of samples as a whole, executes one by one
 * and summarizes statistics.
 */
@Data
public class BatchTestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Engine code must not be blank")
    private String engineCode;

    /** Batch remark */
    private String remark;

    /** Target version (current effective version when omitted); for "try-compute/replay by decision flow version" */
    private Integer versionId;

    /** Whether to return node-level execution traces (recommended true for single try-compute; keep false for large batches to avoid oversized responses) */
    private Boolean withTrace = Boolean.FALSE;

    /** Sample list; each item is one decision input */
    private List<Map<String, Object>> samples = new ArrayList<>();
}
