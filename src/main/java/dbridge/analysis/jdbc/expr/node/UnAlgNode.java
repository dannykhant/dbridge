package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/**
 * Marker node for a variable whose value cannot be expressed algebraically
 * (i.e., the transformation preconditions are not satisfied).
 */
public class UnAlgNode extends Node {

    private static final UnAlgNode INSTANCE = new UnAlgNode();

    private UnAlgNode() {
        super(OpType.UnAlg);
    }

    public static UnAlgNode v() {
        return INSTANCE;
    }

    public static boolean isUnAlgNode(Node node) {
        return node instanceof UnAlgNode;
    }
}
