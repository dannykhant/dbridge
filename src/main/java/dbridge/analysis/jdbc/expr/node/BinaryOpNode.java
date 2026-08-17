package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/**
 * Generic node for binary operators (arithmetic, comparison, boolean).
 */
public class BinaryOpNode extends Node {

    public BinaryOpNode(OpType opType, Node left, Node right) {
        super(opType, left, right);
    }

    public Node getLeft() {
        return getChild(0);
    }

    public Node getRight() {
        return getChild(1);
    }
}
