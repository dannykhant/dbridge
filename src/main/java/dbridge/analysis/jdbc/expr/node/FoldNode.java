package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;
import dbridge.analysis.region.regions.LoopRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the aggregation (fold) performed over the iterations of a loop.
 * Created by the loop region analyzer when the loop-splitting precondition is
 * satisfied, and remembers the loop(s) it has "swallowed".
 */
public class FoldNode extends Node implements SQLTranslatable {

    private final VarNode aggVar;
    private final VarNode loopVar;
    public FoldNode(Node bodyExpr, VarNode aggVar, VarNode loopVar) {
        super(OpType.Fold, bodyExpr);
        this.aggVar = aggVar;
        this.loopVar = loopVar;
    }

    public Node getBodyExpr() {
        return getChild(0);
    }

    public VarNode getAggVar() {
        return aggVar;
    }

    public VarNode getLoopVar() {
        return loopVar;
    }

    public void addLoopSwallowed(LoopRegion region) {
        super.addLoopSwallowed(region);
    }

    @Override
    public String toSQLQuery() throws QueryTranslationException {
        String query = translate(getBodyExpr(), this);
        if (query == null) {
            throw new QueryTranslationException(this + " has no JDBC query source");
        }
        return query;
    }

    private static String translate(Node node, Node fold) throws QueryTranslationException {
        if (node == null) {
            return null;
        }
        if (node != fold && node instanceof SQLTranslatable) {
            try {
                return ((SQLTranslatable) node).toSQLQuery();
            } catch (QueryTranslationException ignored) {
                // A procedural wrapper may contain a translatable relational branch.
            }
        }
        if (node instanceof StringConstNode) {
            String value = ((StringConstNode) node).getValue();
            String normalized = value == null ? "" : value.trim().toLowerCase();
            return normalized.startsWith("select ") ? value : null;
        }
        if (node instanceof IfNode) {
            String query = translate(((IfNode) node).getThenExpr(), fold);
            if (query == null) query = translate(((IfNode) node).getElseExpr(), fold);
            if (query == null) query = translate(((IfNode) node).getCondition(), fold);
            return query;
        }
        for (Node child : node.getChildren()) {
            String query = translate(child, fold);
            if (query != null) {
                return query;
            }
        }
        return null;
    }
}
