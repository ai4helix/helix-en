package com.helix.engine.rule.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FlatConditions {

    public static class Flat {
        public String field;
        public String operator;
        public String value;
        public String logical;
    }

    public static class LeafDetail {
        public String field;
        public String operator;
        public String value;
        public Object actual;
        public boolean hit;
        public boolean unknownOperator;
    }

    public static class Result {
        public boolean matched;
        public boolean empty;
        public boolean inverted;
        public List<LeafDetail> leaves = new ArrayList<LeafDetail>();
    }

    private FlatConditions() {
    }

    public static Result eval(List<Flat> conds, Integer isNon, Map<String, Object> variables) {
        Result out = new Result();
        if (conds == null || conds.isEmpty()) {
            out.empty = true;
            return out;
        }
        out.inverted = isNon != null && isNon == 1;

        List<LeafDetail> details = new ArrayList<LeafDetail>(conds.size());
        List<Boolean> hits = new ArrayList<Boolean>(conds.size());
        for (Flat c : conds) {
            LeafDetail d = new LeafDetail();
            d.field = c.field;
            d.operator = c.operator;
            d.value = c.value;
            d.actual = c.field == null ? null : variables.get(c.field);

            Operator op = Operator.parse(c.operator);
            if (op == null) {
                d.unknownOperator = true;
                op = Operator.EQ;
            }
            List<String> values = new ArrayList<String>(1);
            values.add(c.value);
            Condition.Leaf leaf = new Condition.Leaf(c.field, op, values);
            d.hit = ConditionEvaluator.match(leaf, variables);

            details.add(d);
            hits.add(d.hit);
        }
        out.leaves = details;

        boolean anyGroupHit = false;
        int groupStart = 0;
        for (int i = 0; i < conds.size(); i++) {
            boolean isLast = i == conds.size() - 1;
            boolean boundary = isLast || isOr(conds.get(i).logical);
            if (!boundary) {
                continue;
            }
            boolean groupHit = true;
            for (int j = groupStart; j <= i; j++) {
                if (!hits.get(j)) {
                    groupHit = false;
                    break;
                }
            }
            if (groupHit) {
                anyGroupHit = true;
                break;
            }
            groupStart = i + 1;
        }
        out.matched = anyGroupHit;
        if (out.inverted) {
            out.matched = !out.matched;
        }
        return out;
    }

    private static boolean isOr(String logical) {
        return logical != null && ("||".equals(logical.trim()) || "or".equalsIgnoreCase(logical.trim()));
    }
}
