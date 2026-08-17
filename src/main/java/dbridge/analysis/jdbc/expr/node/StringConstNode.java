package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/**
 * A string literal constant.
 */
public class StringConstNode extends Node {

    private final String value;

    public StringConstNode(String value) {
        super(OpType.StringConst);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }
}
