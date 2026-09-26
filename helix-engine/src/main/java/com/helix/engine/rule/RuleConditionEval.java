package com.helix.engine.rule;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public final class RuleConditionEval {

    // ------------------------------------------------------------------
    //
    //
    //
    // ------------------------------------------------------------------

    private RuleConditionEval() {
    }

    static boolean eval(List<Cond> conditions, Map<String, Object> variables) {
        List<List<Cond>> orGroups = new ArrayList<>();
        List<Cond> current = new ArrayList<>();
        for (Cond c : conditions) {
            if ("or".equalsIgnoreCase(c.logical) && !current.isEmpty()) {
                orGroups.add(current);
                current = new ArrayList<>();
            }
            current.add(c);
        }
        orGroups.add(current);

        for (List<Cond> group : orGroups) {
            boolean all = true;
            for (Cond c : group) {
                if (!matchOne(c, variables)) {
                    all = false;
                    break;
                }
            }
            if (all && !group.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean matchOne(Cond c, Map<String, Object> variables) {
        Object raw = c.field == null ? null : variables.get(c.field);
        String op = c.operator == null ? "==" : c.operator;

        if ("isNull".equals(op)) {
            return raw == null || String.valueOf(raw).isEmpty();
        }
        if ("notNull".equals(op)) {
            return raw != null && !String.valueOf(raw).isEmpty();
        }
        if (raw == null) {
            switch (op) {
                case "!=":
                case "notIn":
                case "not_in":
                    return true;
                default:
                    return false;
            }
        }

        String expect = c.value == null ? "" : String.valueOf(c.value).trim();

        switch (op) {
            case "in":
            case "not_in":
            case "notIn": {
                boolean contains = false;
                for (String v : expect.split(",")) {
                    String t = v.trim();
                    if (!t.isEmpty() && looseEquals(raw, t)) {
                        contains = true;
                        break;
                    }
                }
                return op.equals("in") == contains;
            }
            case "contains":
                return String.valueOf(raw).contains(expect);
            case "notContains":
                return !String.valueOf(raw).contains(expect);
            case "startsWith":
                return String.valueOf(raw).startsWith(expect);
            case "endsWith":
                return String.valueOf(raw).endsWith(expect);
            default:
                break;
        }

        Double left = toDouble(raw);
        Double right = toDouble(expect);
        if (left != null && right != null) {
            switch (op) {
                case ">":  return left > right;
                case ">=": return left >= right;
                case "<":  return left < right;
                case "<=": return left <= right;
                case "!=": return !left.equals(right);
                default:   return left.equals(right);
            }
        }
        String l = String.valueOf(raw);
        switch (op) {
            case "!=": return !l.equals(expect);
            case ">":  return l.compareTo(expect) > 0;
            case ">=": return l.compareTo(expect) >= 0;
            case "<":  return l.compareTo(expect) < 0;
            case "<=": return l.compareTo(expect) <= 0;
            default:   return l.equals(expect);
        }
    }

    private static boolean looseEquals(Object raw, String expect) {
        Double l = toDouble(raw);
        Double r = toDouble(expect);
        if (l != null && r != null) {
            return l.equals(r);
        }
        return String.valueOf(raw).equals(expect);
    }

    private static Double toDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static class Cond {
        private String field;
        private String operator;
        private String value;
        private String logical;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getLogical() {
            return logical;
        }

        public void setLogical(String logical) {
            this.logical = logical;
        }
    }
}
