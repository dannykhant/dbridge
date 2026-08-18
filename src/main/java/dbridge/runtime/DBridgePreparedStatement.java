package dbridge.runtime;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DBridgePreparedStatement {

    private static final String TEMP_TABLE = "pb";

    private final PreparedStatement pstmt;
    private final String sql;
    private Object[] currentBindings = new Object[0];
    private final List<Object[]> batches = new ArrayList<>();
    private final List<ResultSet> results = new ArrayList<>();
    private final List<Statement> heldStatements = new ArrayList<>();
    private int resultIndex = -1;
    private ScalarAggregateInfo scalarInfo;
    private boolean scalarInfoComputed;

    public DBridgePreparedStatement(PreparedStatement pstmt, String sql) {
        this.pstmt = pstmt;
        this.sql = sql;
    }

    public void setInt(int parameterIndex, int x) throws SQLException {
        setBinding(parameterIndex, x);
        pstmt.setInt(parameterIndex, x);
    }

    public void setString(int parameterIndex, String x) throws SQLException {
        setBinding(parameterIndex, x);
        pstmt.setString(parameterIndex, x);
    }

    public void setLong(int parameterIndex, long x) throws SQLException {
        setBinding(parameterIndex, x);
        pstmt.setLong(parameterIndex, x);
    }

    public void setObject(int parameterIndex, Object x) throws SQLException {
        setBinding(parameterIndex, x);
        pstmt.setObject(parameterIndex, x);
    }

    public void bind(int parameterIndex, Object x) {
        setBinding(parameterIndex, x);
    }

    private void setBinding(int parameterIndex, Object x) {
        if (parameterIndex >= currentBindings.length) {
            currentBindings = Arrays.copyOf(currentBindings, parameterIndex + 1);
        }
        currentBindings[parameterIndex] = x;
    }

    public void addBatch() throws SQLException {
        batches.add(Arrays.copyOf(currentBindings, currentBindings.length));
        currentBindings = new Object[0];
        if (!isScalarAggregate()) {
            pstmt.addBatch();
        }
    }

    public void executeBatch() throws SQLException {
        if (isScalarAggregate()) {
            executeScalarAggregate(scalarInfo);
        } else {
            pstmt.executeBatch();
            try {
                ResultSet keys = pstmt.getGeneratedKeys();
                if (keys != null) {
                    results.add(keys);
                }
            } catch (SQLException ignored) {
                // Not all statements return generated keys.
            }
        }
    }

    private boolean isScalarAggregate() {
        if (!scalarInfoComputed) {
            scalarInfo = parseScalarAggregate();
            scalarInfoComputed = true;
        }
        return scalarInfo != null;
    }

    public ResultSet executeQuery() throws SQLException {
        return pstmt.executeQuery();
    }

    public boolean getMoreResults() throws SQLException {
        if (resultIndex + 1 < results.size()) {
            resultIndex++;
            return true;
        }
        return false;
    }

    public ResultSet getResultSet() throws SQLException {
        int i = resultIndex >= 0 ? resultIndex : 0;
        return (i < results.size()) ? results.get(i) : null;
    }

    public void close() throws SQLException {
        SQLException first = null;
        for (ResultSet rs : results) {
            try {
                rs.close();
            } catch (SQLException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        for (Statement st : heldStatements) {
            try {
                st.close();
            } catch (SQLException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        try {
            pstmt.close();
        } catch (SQLException e) {
            if (first == null) {
                first = e;
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private ScalarAggregateInfo parseScalarAggregate() {
        try {
            net.sf.jsqlparser.statement.Statement stmt = CCJSqlParserUtil.parse(sql);
            if (!(stmt instanceof Select)) {
                return null;
            }
            PlainSelect ps = ((Select) stmt).getPlainSelect();
            if (ps == null) {
                return null;
            }
            List<SelectItem<?>> items = ps.getSelectItems();
            if (items == null || items.size() != 1) {
                return null;
            }
            SelectItem<?> item = items.get(0);
            Expression agg = item.getExpression();
            FromItem fromItem = ps.getFromItem();
            if (!(fromItem instanceof Table)) {
                return null;
            }
            Table table = (Table) fromItem;
            WhereInfo wi = analyzeWhere(ps.getWhere());
            if (wi == null) {
                return null;
            }
            ScalarAggregateInfo info = new ScalarAggregateInfo();
            info.aggExpr = agg.toString();
            Alias aggAlias = item.getAlias();
            info.aggAlias = aggAlias != null ? aggAlias.getName() : null;
            info.tableName = table.getName();
            Alias tableAlias = table.getAlias();
            info.tableAlias = tableAlias != null ? tableAlias.getName() : null;
            info.bindColumn = wi.bindColumn;
            info.extraConditions = wi.extraConditions;
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private void executeScalarAggregate(ScalarAggregateInfo info) throws SQLException {
        if (batches.isEmpty()) {
            return;
        }
        Connection conn = pstmt.getConnection();
        String col = info.bindColumn;
        String type = sqlTypeFor(firstBindingValue());
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + TEMP_TABLE);
            st.execute("CREATE TABLE " + TEMP_TABLE + " (" + col + " " + type + ")");
            st.execute("CREATE INDEX " + TEMP_TABLE + "_idx ON " + TEMP_TABLE + "(" + col + ")");
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO " + TEMP_TABLE + " (" + col + ") VALUES (?)")) {
            for (Object[] b : batches) {
                Object val = b.length > 1 ? b[1] : null;
                ins.setObject(1, val);
                ins.addBatch();
            }
            ins.executeBatch();
        }
        String rewritten = buildRewrittenQuery(info, TEMP_TABLE);
        Statement queryStmt = conn.createStatement();
        ResultSet rs = queryStmt.executeQuery(rewritten);
        heldStatements.add(queryStmt);
        results.add(rs);
        // pb is left in place (dropped at the start of the next executeBatch)
        // because the returned ResultSet may be read lazily by the caller.
    }

    private Object firstBindingValue() {
        if (batches.isEmpty()) {
            return null;
        }
        Object[] b = batches.get(0);
        return b.length > 1 ? b[1] : null;
    }

    private String sqlTypeFor(Object val) {
        if (val == null) {
            return "VARCHAR";
        }
        if (val instanceof Integer) {
            return "INTEGER";
        }
        if (val instanceof Long) {
            return "BIGINT";
        }
        if (val instanceof Short) {
            return "SMALLINT";
        }
        if (val instanceof Byte) {
            return "TINYINT";
        }
        if (val instanceof Double) {
            return "DOUBLE";
        }
        if (val instanceof Float) {
            return "REAL";
        }
        if (val instanceof Boolean) {
            return "BOOLEAN";
        }
        if (val instanceof java.math.BigDecimal) {
            return "DECIMAL";
        }
        if (val instanceof java.sql.Date) {
            return "DATE";
        }
        if (val instanceof java.sql.Timestamp) {
            return "TIMESTAMP";
        }
        return "VARCHAR";
    }

    private String buildRewrittenQuery(ScalarAggregateInfo info, String tempTable) {
        String col = info.bindColumn;
        String joinTable = info.tableAlias != null
                ? info.tableName + " " + info.tableAlias
                : info.tableName;
        String qualifier = info.tableAlias != null ? info.tableAlias : info.tableName;
        String aggSelect = info.aggExpr;
        if (info.aggAlias != null) {
            aggSelect += " AS " + info.aggAlias;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(aggSelect)
                .append(", ").append(tempTable).append('.').append(col)
                .append(" AS ").append(col)
                .append(" FROM ").append(tempTable)
                .append(" LEFT JOIN ").append(joinTable)
                .append(" ON ").append(tempTable).append('.').append(col)
                .append(" = ").append(qualifier).append('.').append(col);
        if (info.extraConditions != null) {
            sb.append(" AND ").append(info.extraConditions);
        }
        sb.append(" GROUP BY ").append(tempTable).append('.').append(col);
        return sb.toString();
    }

    private WhereInfo analyzeWhere(Expression where) {
        if (where == null) {
            return null;
        }
        if (where instanceof EqualsTo) {
            String col = columnIfParam((EqualsTo) where);
            if (col == null) {
                return null;
            }
            WhereInfo wi = new WhereInfo();
            wi.bindColumn = col;
            return wi;
        }
        if (where instanceof AndExpression) {
            List<Expression> conjuncts = flattenAnd(where);
            String col = null;
            List<String> extras = new ArrayList<>();
            for (Expression c : conjuncts) {
                if (col == null && c instanceof EqualsTo) {
                    String cc = columnIfParam((EqualsTo) c);
                    if (cc != null) {
                        col = cc;
                        continue;
                    }
                }
                extras.add(c.toString());
            }
            if (col == null) {
                return null;
            }
            WhereInfo wi = new WhereInfo();
            wi.bindColumn = col;
            wi.extraConditions = extras.isEmpty() ? null : String.join(" AND ", extras);
            return wi;
        }
        return null;
    }

    private String columnIfParam(EqualsTo eq) {
        Expression l = eq.getLeftExpression();
        Expression r = eq.getRightExpression();
        if (l instanceof Column && r instanceof JdbcParameter) {
            return ((Column) l).getColumnName();
        }
        if (r instanceof Column && l instanceof JdbcParameter) {
            return ((Column) r).getColumnName();
        }
        return null;
    }

    private List<Expression> flattenAnd(Expression e) {
        List<Expression> out = new ArrayList<>();
        if (e instanceof AndExpression) {
            AndExpression and = (AndExpression) e;
            out.addAll(flattenAnd(and.getLeftExpression()));
            out.addAll(flattenAnd(and.getRightExpression()));
        } else {
            out.add(e);
        }
        return out;
    }

    private static class ScalarAggregateInfo {
        String aggExpr;
        String aggAlias;
        String tableName;
        String tableAlias;
        String bindColumn;
        String extraConditions;
    }

    private static class WhereInfo {
        String bindColumn;
        String extraConditions;
    }
}
