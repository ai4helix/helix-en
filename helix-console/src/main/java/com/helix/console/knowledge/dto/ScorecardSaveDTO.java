package com.helix.console.knowledge.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scorecard save request.
 */
@Data
public class ScorecardSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    @NotBlank(message = "Scorecard name must not be empty")
    private String name;

    private String code;

    private String description;

    private String version;

    /** Id of the attached catalog node */
    private Integer parentId;

    private Integer type;

    private Integer engineId;

    private Integer organId;

    /** Dimension list; the backend serializes it and writes it into t_scorecard.score */
    private List<ScorecardDimensionDTO> dimensions = new ArrayList<>();

    /** Mapping from score to PD (probability of default), keyed by score lower bound */
    private Map<String, String> pd;

    /** Mapping from score to Odds */
    private Map<String, String> odds;
}
