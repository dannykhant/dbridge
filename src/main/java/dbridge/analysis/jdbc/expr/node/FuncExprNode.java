package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.visitor.NodeVisitor;

/** Parameterized function body used as the first child of a FoldNode. */
public final class FuncExprNode extends Node {
    public FuncExprNode(Node body, VarNode accumulator) {
        super(OpType.FuncExpr, parameterize(body, accumulator));
    }

    public FuncExprNode(Node body) {
        super(OpType.FuncExpr, body);
    }

    public Node getBody() {
        return getChild(0);
    }

    private static Node parameterize(Node body, VarNode accumulator) {
        if (body == null || accumulator == null) {
            return body;
        }
        final PlaceholderVarNode placeholder = new PlaceholderVarNode();
        return body.accept(new NodeVisitor() {
            @Override
            public Node visit(Node node) {
                return node.equals(accumulator) ? placeholder : node;
            }
        });
    }
}
