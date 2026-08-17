package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/**
 * A placeholder for a query parameter (the {@code ?} in a PreparedStatement).
 * The optional child is the value that the parameter is bound to.
 */
public class ParamNode extends Node {

    public ParamNode() {
        super(OpType.Param);
    }

    public ParamNode(Node boundValue) {
        super(OpType.Param, boundValue);
    }
}
