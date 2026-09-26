package com.helix.console.knowledge.support;

import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.knowledge.dto.RuleConditionDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule condition -> AST tree converter (configuration side).
 *
 * <p>Converts the editor's flat condition list into a tree structure for
 * writing to {@code t_rule_condition}. <b>Grouping rules must exactly match
 * {@link RuleExpressionBuilder}</b> -- the two produce the "AST tree" and the
 * "readable expression" respectively; if their grouping conventions diverge,
 * an expression can look correct while the engine executes a different AST.</p>
 *
 * <h3>Unified grouping convention</h3>
 * <pre>
 *   Condition list: [A] && [B] || [C] && [D]
 *   -> split by || into "AND groups": (A && B) || (C && D)
 *   -> AST: Group(OR, [ Group(AND, [A,B]), Group(AND, [C,D]) ])
 * </pre>
 * With a single connector kind, flatten directly without extra grouping.
 */
@Component
public class RuleAstBuilder {

    /** Node types, matching {@code t_rule_condition.node_type} */
    public static final int NODE_LEAF = 1;
    public static final int NODE_AND = 2;
    public static final int NODE_OR = 3;
    public static final int NODE_NOT = 4;

    /**
     * AST node (in-memory representation before persisting).
     *
     * <p>The root node is not persisted; it only carries children. Persistence
     * starts from the child nodes.</p>
     */
    public static class AstNode {

        public int nodeType;
        public String fieldCode;
        public String operator;
        public String value;
        public int sortNo;
        public int depth;
        public List<AstNode> children = new ArrayList<AstNode>();

        public AstNode(int nodeType) {
            this.nodeType = nodeType;
            this.depth = 1;
        }

        public static AstNode leaf(String field, String operator, String value) {
            AstNode n = new AstNode(NODE_LEAF);
            n.fieldCode = field;
            n.operator = operator;
            n.value = value;
            return n;
        }

        public static AstNode group(int nodeType, List<AstNode> children) {
            AstNode n = new AstNode(nodeType);
            n.children = children;
            return n;
        }

        public boolean isLeaf() {
            return nodeType == NODE_LEAF;
        }

        /** Recursively count nodes (including self) */
        public int count() {
            int n = 1;
            for (AstNode c : children) {
                n += c.count();
            }
            return n;
        }

        /** Recursively compute depth and backfill */
        public int calcDepth() {
            if (children.isEmpty()) {
                this.depth = 1;
                return 1;
            }
            int max = 0;
            for (AstNode c : children) {
                max = Math.max(max, c.calcDepth());
            }
            this.depth = max + 1;
            return this.depth;
        }
    }

    /**
     * Build the AST root node from a condition list.
     *
     * @param conditions editor condition list
     * @param isNon      whether to negate the whole tree (1 = negate)
     * @return root node; null when there are no conditions
     */
    public AstNode build(List<RuleConditionDTO> conditions, Integer isNon) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }

        // 1. Convert each condition to a leaf node
        List<AstNode> leaves = new ArrayList<AstNode>(conditions.size());
        for (RuleConditionDTO c : conditions) {
            leaves.add(toLeaf(c));
        }

        // 2. Determine connector composition (the last condition's logical is always -1 and does not join)
        boolean hasOr = false;
        boolean hasAnd = false;
        for (int i = 0; i < conditions.size() - 1; i++) {
            if ("||".equals(normalizeLogical(conditions.get(i).getLogical()))) {
                hasOr = true;
            } else {
                hasAnd = true;
            }
        }

        // 3. Assemble
        AstNode root;
        if (hasOr && hasAnd) {
            root = buildOrOfAndGroups(conditions, leaves);
        } else if (hasOr) {
            root = leaves.size() == 1 ? leaves.get(0) : AstNode.group(NODE_OR, leaves);
        } else {
            root = leaves.size() == 1 ? leaves.get(0) : AstNode.group(NODE_AND, leaves);
        }

        // 4. Overall negation
        if (isNon != null && isNon == 1) {
            AstNode not = new AstNode(NODE_NOT);
            not.children.add(root);
            root = not;
        }
        root.calcDepth();
        return root;
    }

    /** Split by || into "AND groups"; && inside a group */
    private AstNode buildOrOfAndGroups(List<RuleConditionDTO> conditions, List<AstNode> leaves) {
        List<AstNode> orGroups = new ArrayList<AstNode>();
        List<AstNode> currentAnd = new ArrayList<AstNode>();
        currentAnd.add(leaves.get(0));

        for (int i = 0; i < conditions.size() - 1; i++) {
            String lg = normalizeLogical(conditions.get(i).getLogical());
            if ("||".equals(lg)) {
                orGroups.add(currentAnd.size() == 1 ? currentAnd.get(0)
                        : AstNode.group(NODE_AND, currentAnd));
                currentAnd = new ArrayList<AstNode>();
            }
            currentAnd.add(leaves.get(i + 1));
        }
        orGroups.add(currentAnd.size() == 1 ? currentAnd.get(0)
                : AstNode.group(NODE_AND, currentAnd));
        return AstNode.group(NODE_OR, orGroups);
    }

    /** Single condition -> leaf node */
    private AstNode toLeaf(RuleConditionDTO c) {
        String var = resolveVariable(c);
        String op = StringUtils.trimToEmpty(c.getOperator());
        if (op.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Condition operator must not be blank");
        }
        OperatorCode code = OperatorCode.of(op);
        if (code == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Unsupported operator: " + op);
        }
        String raw = StringUtils.trimToEmpty(c.getFieldValue());

        // Multi-value operators: store uniformly comma-separated (tokenized by comma at execution time)
        if (code.multiValue) {
            List<String> items = new ArrayList<String>();
            for (String v : raw.split(",")) {
                String t = v.trim();
                if (!t.isEmpty()) {
                    items.add(t);
                }
            }
            if (items.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Value list of field " + var + " is empty");
            }
            return AstNode.leaf(var, code.name(), String.join(",", items));
        }
        return AstNode.leaf(var, code.name(), raw);
    }

    /** Resolve the variable name with the same convention as RuleExpressionBuilder.resolveVariable */
    private String resolveVariable(RuleConditionDTO c) {
        if (StringUtils.isNotBlank(c.getFieldEn())) {
            return c.getFieldEn().trim();
        }
        String fieldId = StringUtils.trimToEmpty(c.getFieldId());
        if (fieldId.contains("|")) {
            String[] parts = fieldId.split("\\|", 2);
            if (StringUtils.isNotBlank(parts[1])) {
                return parts[1].trim();
            }
        }
        if (StringUtils.isBlank(fieldId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Condition is missing a field identifier");
        }
        return fieldId;
    }

    /**
     * Convert to the flat condition format (field / operator / value / logical)
     * of the engine dry-run API.
     *
     * <p>Dry run delegates evaluation to helix-engine (sharing the production
     * ConditionEvaluator); this method only maps formats; field resolution
     * follows the same convention as {@link #build}.</p>
     */
    public List<Map<String, Object>> toFlatConditions(List<RuleConditionDTO> conditions) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (conditions == null) {
            return out;
        }
        for (RuleConditionDTO c : conditions) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("field", resolveVariable(c));
            m.put("operator", StringUtils.trimToEmpty(c.getOperator()));
            m.put("value", StringUtils.trimToEmpty(c.getFieldValue()));
            m.put("logical", StringUtils.trimToEmpty(c.getLogical()));
            out.add(m);
        }
        return out;
    }

    private String normalizeLogical(String logical) {
        if (StringUtils.isBlank(logical)) {
            return "&&";
        }
        String l = logical.trim();
        if ("or".equalsIgnoreCase(l) || "||".equals(l)) {
            return "||";
        }
        return "&&";
    }

    /**
     * Operator codes: normalize editor symbols/aliases to engine-side enum names.
     *
     * <p>Must match the enum names of the engine's
     * {@code com.helix.engine.rule.ast.Operator}, otherwise {@code Operator.parse}
     * fails and the rule never hits.</p>
     */
    public enum OperatorCode {
        EQ(false), NE(false), GT(false), GE(false), LT(false), LE(false),
        IN(true), NOT_IN(true), CONTAINS(false), NOT_CONTAINS(false),
        STARTS_WITH(false), ENDS_WITH(false), BETWEEN(true), IS_NULL(false), NOT_NULL(false);

        public final boolean multiValue;

        OperatorCode(boolean multiValue) {
            this.multiValue = multiValue;
        }

        private static final List<String> SYMBOLS = Arrays.asList("==", "!=", ">", ">=", "<", "<=");

        /**
         * Parse an operator: supports symbols, camelCase and underscore forms.
         *
         * @return null when unrecognized
         */
        public static OperatorCode of(String raw) {
            if (raw == null) {
                return null;
            }
            String s = raw.trim();
            if ("=".equals(s)) {
                return EQ;
            }
            if ("<>".equals(s)) {
                return NE;
            }
            if (SYMBOLS.contains(s)) {
                switch (s) {
                    case "==": return EQ;
                    case "!=": return NE;
                    case ">": return GT;
                    case ">=": return GE;
                    case "<": return LT;
                    default: return LE;
                }
            }
            // Normalize: strip separators, uppercase, then compare with enum names
            String key = normalizeName(s);
            for (OperatorCode c : values()) {
                if (normalizeName(c.name()).equals(key)) {
                    return c;
                }
            }
            return null;
        }

        private static String normalizeName(String s) {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch == '_' || ch == '-' || ch == ' ') {
                    continue;
                }
                sb.append(Character.toUpperCase(ch));
            }
            return sb.toString();
        }
    }
}
