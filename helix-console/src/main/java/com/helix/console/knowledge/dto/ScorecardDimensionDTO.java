package com.helix.console.knowledge.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Scorecard dimension. Corresponds to one item of the {@code t_scorecard.score} JSON array.
 *
 * <p>Structure example:
 * <pre>
 * { "field": "helix_score", "weight": 30, "type": "inverse",
 *   "bins": [ {"min": 750, "max": 900, "score": 30} ] }
 * </pre>
 * </p>
 */
@Data
public class ScorecardDimensionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Field English name; the value is read by this name when scoring */
    @NotBlank(message = "Dimension field must not be empty")
    private String field;

    /** Field Chinese name, for frontend display */
    private String fieldCn;

    /** Weight, display only; the actual score is determined by bins */
    private Integer weight;

    /** score = forward scoring / inverse = reverse scoring, frontend hint only */
    private String type;

    /** Bin configuration */
    private List<ScorecardBinDTO> bins = new ArrayList<>();

    @Data
    public static class ScorecardBinDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Bin lower bound (inclusive) */
        private Double min;

        /** Bin upper bound (exclusive) */
        private Double max;

        /** Score of this bin */
        private Integer score;
    }
}
