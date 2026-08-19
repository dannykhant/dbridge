package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

public class CountStarNode extends Node implements SQLTranslatable {
    public CountStarNode() { super(OpType.CountStar); }

    @Override
    public String toSQLQuery() throws QueryTranslationException { return "count(*)"; }
}
