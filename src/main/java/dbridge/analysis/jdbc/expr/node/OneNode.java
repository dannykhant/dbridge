package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

public class OneNode extends Node implements SQLTranslatable {
    public OneNode() { super(OpType.One); }
    @Override public String toSQLQuery() throws QueryTranslationException { return "1"; }
}
