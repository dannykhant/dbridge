package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/**
 * A conditional (if-then-else) expression. child[0] is the condition,
 * child[1] the true branch and child[2] the false branch.
 */
public class IfNode extends Node {

    public IfNode(Node condition, Node thenExpr, Node elseExpr) {
        super(OpType.If, condition, thenExpr, elseExpr);
    }

    public Node getCondition() {
        return getChild(0);
    }

    public Node getThenExpr() {
        return getChild(1);
    }

    public Node getElseExpr() {
        return getChild(2);
    }
}
