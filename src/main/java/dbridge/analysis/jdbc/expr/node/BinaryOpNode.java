package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

/**
 * Generic node for binary operators (arithmetic, comparison, boolean).
 */
public class BinaryOpNode extends Node implements SQLTranslatable {

    public BinaryOpNode(OpType opType, Node left, Node right) {
        super(opType, left, right);
    }

    public Node getLeft() {
        return getChild(0);
    }

    public Node getRight() {
        return getChild(1);
    }

    @Override
    public String toSQLQuery() throws QueryTranslationException {
        String operator;
        switch (getOpType()) {
            case ArithAdd: operator = "+"; break;
            case ArithSub: operator = "-"; break;
            case ArithMod: operator = "%"; break;
            case Eq: operator = "="; break;
            case NotEq: operator = "<>"; break;
            case Gt: operator = ">"; break;
            case Lt: operator = "<"; break;
            case And: operator = "AND"; break;
            case Or: operator = "OR"; break;
            default: throw new QueryTranslationException("Unsupported SQL binary operator: " + getOpType());
        }
        String expression = ProjectNode.sql(getLeft()) + " " + operator + " " + ProjectNode.sql(getRight());
        return getOpType() == OpType.ArithAdd || getOpType() == OpType.ArithSub
                || getOpType() == OpType.ArithMod
                ? "(" + expression + ")" : expression;
    }
}
