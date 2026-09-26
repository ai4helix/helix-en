package com.helix.engine.rule.ast;

import java.util.List;

public final class ConditionExpressions {

    private ConditionExpressions() {
    }

    public static String render(Condition c) {
        if (c == null) {
            return null;
        }
        String s = renderNode(c);
        return s == null || s.isEmpty() ? null : s;
    }

    private static String renderNode(Condition c) {
        if (c instanceof Condition.Leaf) {
            return renderLeaf((Condition.Leaf) c);
        }
        if (c instanceof Condition.Not) {
            String inner = renderNode(((Condition.Not) c).getChild());
            return inner == null ? null : "!(" + inner + ")";
        }
        Condition.Group g = (Condition.Group) c;
        List<Condition> children = g.getChildren();
        if (children == null || children.isEmpty()) {
            return null;
        }
        String joiner = g.getLogic() == Logic.OR ? " || " : " && ";
        StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (Condition child : children) {
            String s = renderNode(child);
            if (s == null) {
                continue;
            }
            if (!first) {
                sb.append(joiner);
            }
            sb.append(s);
            first = false;
        }
        if (first) {
            return null;
        }
        return sb.append(')').toString();
    }

    private static String renderLeaf(Condition.Leaf leaf) {
        String field = leaf.getField() == null ? "?" : leaf.getField();
        Operator op = leaf.getOperator() == null ? Operator.EQ : leaf.getOperator();
        switch (op) {
            case IS_NULL:
                return field + " == null";
            case NOT_NULL:
                return field + " != null";
            case IN:
            case NOT_IN: {
                StringBuilder items = new StringBuilder();
                for (String v : leaf.getValues()) {
                    if (v == null) {
                        continue;
                    }
                    for (String part : v.split(",")) {
                        String t = part.trim();
                        if (!t.isEmpty()) {
                            if (items.length() > 0) {
                                items.append(", ");
                            }
                            items.append(literal(t));
                        }
                    }
                }
                String call = "[" + items + "].contains(" + field + ")";
                return op == Operator.NOT_IN ? "!(" + call + ")" : call;
            }
            case BETWEEN: {
                List<String> vs = leaf.getValues();
                String min = vs != null && !vs.isEmpty() ? vs.get(0) : null;
                String max = vs != null && vs.size() > 1 ? vs.get(1) : null;
                return field + " >= " + literal(min) + " && " + field + " <= " + literal(max);
            }
            case CONTAINS:
                return field + " =~ '.*" + firstValue(leaf) + ".*'";
            case NOT_CONTAINS:
                return "!(" + field + " =~ '.*" + firstValue(leaf) + ".*')";
            case STARTS_WITH:
                return field + " =^ '" + firstValue(leaf) + "'";
            case ENDS_WITH:
                return field + " =$ '" + firstValue(leaf) + "'";
            default:
                return field + " " + symbol(op) + " " + literal(firstValue(leaf));
        }
    }

    private static String firstValue(Condition.Leaf leaf) {
        List<String> vs = leaf.getValues();
        return vs == null || vs.isEmpty() ? null : vs.get(0);
    }

    private static String symbol(Operator op) {
        switch (op) {
            case GT: return ">";
            case GE: return ">=";
            case LT: return "<";
            case LE: return "<=";
            case NE: return "!=";
            default: return "==";
        }
    }

    private static String literal(String v) {
        if (v == null) {
            return "null";
        }
        String t = v.trim();
        if (t.matches("-?\\d+(\\.\\d+)?")) {
            return t;
        }
        return "'" + t + "'";
    }
}
