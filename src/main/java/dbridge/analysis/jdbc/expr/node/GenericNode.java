package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/** Structural node for operators not yet produced by the JDBC DIR builder. */
public final class GenericNode extends Node {
    public GenericNode(OpType opType, Node... children) {
        super(opType, children);
    }
}
