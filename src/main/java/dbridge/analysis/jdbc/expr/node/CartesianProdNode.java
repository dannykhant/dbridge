package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

/** A comma-separated SQL relation list. */
public class CartesianProdNode extends Node implements SQLTranslatable {
    public CartesianProdNode(Node... relations) {
        super(OpType.CartesianProd, relations);
    }

    @Override
    public String toSQLQuery() throws QueryTranslationException {
        StringBuilder sql = new StringBuilder("from ");
        for (int i = 0; i < getNumChildren(); i++) {
            Node child = getChild(i);
            ClassRefNode table = child instanceof ClassRefNode
                    ? (ClassRefNode) child
                    : child instanceof FieldRefNode ? ((FieldRefNode) child).getTypeClassRef() : null;
            if (table == null) {
                throw new QueryTranslationException(child + " is not a ClassRef or FieldRef");
            }
            if (i > 0) sql.append(", ");
            sql.append(table.toSQLQuery()).append(" as ").append(table.getAlias());
        }
        return sql.toString();
    }
}
