package com.helix.console.knowledge.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule save request.
 *
 * <p>Design decision: the backend generates the {@code t_rule.content} expression from
 * {@code conditions} automatically; the frontend no longer passes the expression directly. This keeps
 * "structured conditions" and "the executable expression" always consistent, avoiding the inconsistency
 * problem of the legacy version where two paths (t_rule_field and content) coexisted.</p>
 */
@Data
public class RuleSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Carried on update */
    private Integer id;

    @NotBlank(message = "Rule name must not be empty")
    private String name;

    private String code;

    private String description;

    /** Priority; lower executes first */
    private Integer priority;

    /** Id of the attached catalog node */
    private Integer parentId;

    /** 1 organization level, 2 engine level */
    private Integer type;

    private Integer engineId;

    private Integer organId;

    /** 0 hard reject, 1 score adjust */
    private Integer ruleType;

    /** 2 reject, 3 manual review, 4 simplify, 5 pass */
    private Integer ruleAudit;

    /** Score of a score-adjust rule; may be negative */
    private Integer score;

    /** Global negation: 0 no, 1 yes */
    private Integer isNon;

    /** Condition list; the order is the evaluation order */
    private List<RuleConditionDTO> conditions = new ArrayList<>();
}
