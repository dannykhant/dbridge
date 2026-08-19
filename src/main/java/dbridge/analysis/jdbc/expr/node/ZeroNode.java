package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

public class ZeroNode extends Node implements SQLTranslatable {
    public ZeroNode() { super(OpType.Zero); }
    @Override public String toSQLQuery() throws QueryTranslationException { return "0"; }
}
