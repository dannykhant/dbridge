package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

/**
 * A placeholder for a query parameter (the {@code ?} in a PreparedStatement).
 * The optional child is the value that the parameter is bound to.
 */
public class ParamNode extends Node implements SQLTranslatable {
    private final int index;

    public ParamNode() {
        this(-1);
    }

    public ParamNode(int index) {
        super(OpType.Param);
        this.index = index;
    }

    public ParamNode(Node boundValue) {
        super(OpType.Param, boundValue);
        this.index = -1;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String toSQLQuery() throws QueryTranslationException {
        return "?";
    }
}
