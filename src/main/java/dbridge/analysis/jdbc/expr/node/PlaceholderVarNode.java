package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/** Placeholder for the accumulator inside a fold function. */
public final class PlaceholderVarNode extends Node {
    public PlaceholderVarNode() {
        super(OpType.PlaceholderVar);
    }

    @Override
    public String toString() {
        return "<placeholder>";
    }
}
