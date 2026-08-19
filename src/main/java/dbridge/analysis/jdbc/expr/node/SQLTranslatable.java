package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

/** A node which can render itself as a JDBC SQL fragment. */
public interface SQLTranslatable {
    String toSQLQuery() throws QueryTranslationException;
}
