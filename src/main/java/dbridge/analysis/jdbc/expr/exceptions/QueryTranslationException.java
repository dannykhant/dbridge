package dbridge.analysis.jdbc.expr.exceptions;

/** Raised when an expression cannot be rendered as SQL. */
public class QueryTranslationException extends Exception {
    public QueryTranslationException(String message) {
        super(message);
    }
}
