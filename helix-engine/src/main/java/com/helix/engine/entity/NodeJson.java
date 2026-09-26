package com.helix.engine.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code node_json} structure of a policy rule node.
 *
 * <p>Written by the helix-rules configuration side; this service reads it to decide which rules to execute
 * and how to aggregate results.</p>
 *
 * <pre>
 * {
 *   "selectedRule":  [ {code, id, name, priority, parentId} ],   // all rules selected by the node
 *   "deny_rules":    { "isSerial": 0, "rules": [...] },          // reject-type rules
 *   "addOrSubRules": { "threshold": 1.0, "rules": [...] }        // add/subtract-type rules
 * }
 * </pre>
 */
@Data
// selectedRule stores JSON references like {"$ref":"$.deny_rules.rules[0]"},
// field names are not fixed, so unknown properties must be ignored, otherwise the entire deserialization
// fails and deny_rules cannot be parsed either.
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeJson implements Serializable {

    private static final long serialVersionUID = 1L;

    /** All rules selected by the node. In real data this is an array of JSON references, with id always null */
    private List<RuleRef> selectedRule = new ArrayList<>();

    /**
     * Reject-type rule configuration.
     * The JSON key is {@code deny_rules} (underscore), requiring explicit mapping,
     * otherwise it deserializes to null and the node is judged as "no rules configured".
     */
    @JsonProperty("deny_rules")
    @JsonAlias("denyRules")
    private DenyRules denyRules;

    /** Add/subtract-type rule configuration. The JSON key is {@code addOrSubRules} */
    @JsonProperty("addOrSubRules")
    @JsonAlias("add_or_sub_rules")
    private AddOrSubRules addOrSubRules;

    /**
     * Rule reference.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String code;
        private Integer id;
        private String name;
        private Integer priority;
        private Integer parentId;
    }

    /**
     * Deny rule configuration.
     */
    @Data
    public static class DenyRules implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * Whether serial: 0 parallel (any hit rejects), 1 serial (must accumulate to the threshold)
         */
        private Integer isSerial;
        private List<RuleRef> rules = new ArrayList<>();
    }

    /**
     * Add/subtract score rule configuration.
     */
    @Data
    public static class AddOrSubRules implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Threshold: takes effect only when the accumulated score reaches this value */
        private Double threshold;
        private List<RuleRef> rules = new ArrayList<>();
    }

    /**
     * Collect all rule ids involved in the node (deduplicated).
     *
     * <p>Note: in real data {@code selectedRule} stores JSON references
     * (of the form {@code {"$ref":"$.deny\\_rules.rules[0]"}}) rather than complete rule objects,
     * and their {@code id} is always null. Therefore the truly reliable rule sources are
     * {@code deny_rules.rules} and {@code addOrSubRules.rules};
     * {@code selectedRule} serves only as a fallback (when it does contain complete objects).</p>
     */
    public List<Integer> allRuleIds() {
        java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
        // Primary source: classification lists
        if (denyRules != null && denyRules.getRules() != null) {
            denyRules.getRules().stream().map(RuleRef::getId).filter(java.util.Objects::nonNull).forEach(ids::add);
        }
        if (addOrSubRules != null && addOrSubRules.getRules() != null) {
            addOrSubRules.getRules().stream().map(RuleRef::getId).filter(java.util.Objects::nonNull).forEach(ids::add);
        }
        // Fallback: include selectedRule too when it holds complete objects
        if (selectedRule != null) {
            selectedRule.stream().map(RuleRef::getId).filter(java.util.Objects::nonNull).forEach(ids::add);
        }
        return new ArrayList<>(ids);
    }
}
