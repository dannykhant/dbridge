package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.ArrayList;
import java.util.List;

/** A scalar JDBC query rewritten once for batched bindings. */
public final class JdbcSetQueryNode extends Node implements SQLTranslatable {
    private static final String MARKER = "/* dbridge-prebuilt */ ";
    private final String query;

    public JdbcSetQueryNode(String query) {
        super(OpType.Select);
        this.query = query;
    }

    public static JdbcSetQueryNode from(Node node) {
        String query = findQuery(node);
        return query == null ? null : new JdbcSetQueryNode(query);
    }

    public static boolean isPrebuilt(String query) {
        return query != null && query.startsWith(MARKER);
    }

    @Override
    public String toSQLQuery() throws QueryTranslationException {
        try {
            return MARKER + rewrite(query);
        } catch (RuntimeException e) {
            throw new QueryTranslationException("Cannot rewrite JDBC query: " + query + ": " + e.getMessage());
        }
    }

    private static String findQuery(Node node) {
        if (node == null) {
            return null;
        }
        if (node instanceof StringConstNode) {
            String value = ((StringConstNode) node).getValue();
            if (value != null && value.trim().toLowerCase().startsWith("select ")) {
                return value;
            }
        }
        for (Node child : node.getChildren()) {
            String query = findQuery(child);
            if (query != null) {
                return query;
            }
        }
        return null;
    }

    private static String rewrite(String sql) {
        try {
            net.sf.jsqlparser.statement.Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select)) {
                throw new IllegalArgumentException("not a SELECT");
            }
            PlainSelect select = ((Select) statement).getPlainSelect();
            if (select == null || (select.getJoins() != null && !select.getJoins().isEmpty())
                    || select.getSelectItems() == null || select.getSelectItems().size() != 1
                    || countParameters(select.getWhere()) != 1
                    || !(select.getFromItem() instanceof Table)) {
                throw new IllegalArgumentException("unsupported scalar aggregate");
            }

            SelectItem<?> item = select.getSelectItems().get(0);
            Table table = (Table) select.getFromItem();
            WhereInfo where = analyzeWhere(select.getWhere());
            if (where == null) {
                throw new IllegalArgumentException("missing parameter predicate");
            }
            String tableName = table.getName();
            String tableAlias = table.getAlias() == null ? null : table.getAlias().getName();
            String qualifier = tableAlias == null ? tableName : tableAlias;
            String aggregate = item.getExpression().toString();
            Alias aggregateAlias = item.getAlias();
            if (aggregateAlias != null) {
                aggregate += " AS " + aggregateAlias.getName();
            }
            String joinTable = tableAlias == null ? tableName : tableName + " " + tableAlias;
            StringBuilder result = new StringBuilder("SELECT ")
                    .append(aggregate)
                    .append(", pb.").append(where.bindColumn).append(" AS ").append(where.bindColumn)
                    .append(", pb.batch_ordinal FROM pb LEFT JOIN ").append(joinTable)
                    .append(" ON pb.").append(where.bindColumn).append(" = ")
                    .append(qualifier).append('.').append(where.bindColumn);
            if (where.extraConditions != null) {
                result.append(" AND ").append(where.extraConditions);
            }
            return result.append(" GROUP BY pb.batch_ordinal, pb.")
                    .append(where.bindColumn).toString();
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static int countParameters(Expression expression) {
        if (expression == null) {
            return 0;
        }
        final int[] count = {0};
        expression.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(JdbcParameter parameter) {
                count[0]++;
            }
        });
        return count[0];
    }

    private static WhereInfo analyzeWhere(Expression expression) {
        List<Expression> conjuncts = new ArrayList<>();
        flattenAnd(expression, conjuncts);
        String bindColumn = null;
        List<String> extras = new ArrayList<>();
        for (Expression conjunct : conjuncts) {
            String column = columnIfParameter(conjunct);
            if (bindColumn == null && column != null) {
                bindColumn = column;
            } else {
                extras.add(conjunct.toString());
            }
        }
        if (bindColumn == null) {
            return null;
        }
        WhereInfo result = new WhereInfo();
        result.bindColumn = bindColumn;
        result.extraConditions = extras.isEmpty() ? null : String.join(" AND ", extras);
        return result;
    }

    private static void flattenAnd(Expression expression, List<Expression> result) {
        if (expression instanceof AndExpression) {
            AndExpression and = (AndExpression) expression;
            flattenAnd(and.getLeftExpression(), result);
            flattenAnd(and.getRightExpression(), result);
        } else if (expression != null) {
            result.add(expression);
        }
    }

    private static String columnIfParameter(Expression expression) {
        if (!(expression instanceof EqualsTo)) {
            return null;
        }
        EqualsTo equals = (EqualsTo) expression;
        if (equals.getLeftExpression() instanceof Column
                && equals.getRightExpression() instanceof JdbcParameter) {
            return ((Column) equals.getLeftExpression()).getColumnName();
        }
        if (equals.getRightExpression() instanceof Column
                && equals.getLeftExpression() instanceof JdbcParameter) {
            return ((Column) equals.getRightExpression()).getColumnName();
        }
        return null;
    }

    private static final class WhereInfo {
        private String bindColumn;
        private String extraConditions;
    }
}
