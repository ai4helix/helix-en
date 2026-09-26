package com.helix.console.engine.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Save request for the whole decision flow.
 *
 * <p>Corresponds to the legacy JSP version's {@code /decision_flow/saveVersion}: submits the entire graph at once.</p>
 */
@Data
public class VersionSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "Version ID must not be empty")
    private Integer versionId;

    /** Layout mode */
    private Integer layout;

    /** The whole graph; the server diffs it and persists incrementally */
    private GraphVO graph;
}
