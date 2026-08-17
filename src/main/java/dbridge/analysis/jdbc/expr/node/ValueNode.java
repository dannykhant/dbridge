package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/**
 * A literal constant value appearing in an expression.
 */
public class ValueNode extends Node {

    private final soot.Value value;

    public ValueNode(soot.Value value) {
        super(OpType.Value);
        this.value = value;
    }

    public soot.Value getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value == null ? "null" : value.toString();
    }
}
