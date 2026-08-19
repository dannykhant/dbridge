package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

/** Adds a SQL where predicate to a relation. */
public class SelectNode extends Node implements SQLTranslatable {
    public SelectNode(Node relation, Node condition) {
        super(OpType.Select, relation, condition);
    }

    @Override
    public String toSQLQuery() throws QueryTranslationException {
        return ProjectNode.sql(getChild(0)) + " where " + ProjectNode.sql(getChild(1));
    }
}
