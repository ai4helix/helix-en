package com.helix.engine.rule.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Condition {

    public enum Kind {
        LEAF, GROUP, NOT
    }

    public abstract Kind getKind();

    public static final class Leaf extends Condition {

        private final String field;
        private final Operator operator;
        private final List<String> values;

        public Leaf(String field, Operator operator, List<String> values) {
            this.field = field;
            this.operator = operator;
            this.values = values == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(values));
        }

        public String getField() {
            return field;
        }

        public Operator getOperator() {
            return operator;
        }

        public List<String> getValues() {
            return values;
        }

        public String firstValue() {
            return values.isEmpty() ? null : values.get(0);
        }

        @Override
        public Kind getKind() {
            return Kind.LEAF;
        }
    }

    public static final class Group extends Condition {

        private final Logic logic;
        private final List<Condition> children;

        public Group(Logic logic, List<Condition> children) {
            this.logic = logic == null ? Logic.AND : logic;
            this.children = children == null
                    ? Collections.<Condition>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(children));
        }

        public Logic getLogic() {
            return logic;
        }

        public List<Condition> getChildren() {
            return children;
        }

        @Override
        public Kind getKind() {
            return Kind.GROUP;
        }
    }

    public static final class Not extends Condition {

        private final Condition child;

        public Not(Condition child) {
            this.child = child;
        }

        public Condition getChild() {
            return child;
        }

        @Override
        public Kind getKind() {
            return Kind.NOT;
        }
    }

    public int depth() {
        if (this instanceof Leaf) {
            return 1;
        }
        if (this instanceof Not) {
            Not n = (Not) this;
            return n.getChild() == null ? 1 : 1 + n.getChild().depth();
        }
        Group g = (Group) this;
        int max = 0;
        for (Condition c : g.getChildren()) {
            if (c != null) {
                max = Math.max(max, c.depth());
            }
        }
        return 1 + max;
    }

    public int leafCount() {
        if (this instanceof Leaf) {
            return 1;
        }
        if (this instanceof Not) {
            Not n = (Not) this;
            return n.getChild() == null ? 0 : n.getChild().leafCount();
        }
        Group g = (Group) this;
        int sum = 0;
        for (Condition c : g.getChildren()) {
            if (c != null) {
                sum += c.leafCount();
            }
        }
        return sum;
    }
}
