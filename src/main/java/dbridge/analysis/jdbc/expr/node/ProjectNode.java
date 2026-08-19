package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

/** Projects an expression from a relation. */
public class ProjectNode extends Node implements SQLTranslatable {
    public ProjectNode(Node relation, Node projection) {
        super(OpType.Project, relation, projection);
    }

    @Override
    public String toSQLQuery() throws QueryTranslationException {
        return "(select " + sql(getChild(1)) + " " + sql(getChild(0)) + ")";
    }

    static String sql(Node node) throws QueryTranslationException {
        if (!(node instanceof SQLTranslatable)) {
            throw new QueryTranslationException(node + " is not SQL translatable");
        }
        return ((SQLTranslatable) node).toSQLQuery();
    }
}
