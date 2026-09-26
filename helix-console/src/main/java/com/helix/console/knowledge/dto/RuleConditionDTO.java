package com.helix.console.knowledge.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * Rule condition item. Corresponds to one row of {@code t_rule_field}.
 *
 * <p>{@code fieldId} is stored by the backend uniformly as {@code "{fieldId}|{fieldEn}"},
 * consistent with the variable name used by runtime expression evaluation.</p>
 */
@Data
public class RuleConditionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Carried when modifying an existing condition */
    private Integer id;

    /** Logical operator between this and the "next" condition: && / ||; -1 for the last one */
    private String logical;

    /** Comparison operator: == != > >= < <= in notIn contains etc. */
    @NotBlank(message = "Operator must not be empty")
    private String operator;

    /** Comparison value */
    private String fieldValue;

    /** Field identifier, format {id}|{fieldEn} */
    @NotBlank(message = "Field must not be empty")
    private String fieldId;

    /** Field English name, for frontend display; the backend builds the expression from it */
    private String fieldEn;
}
