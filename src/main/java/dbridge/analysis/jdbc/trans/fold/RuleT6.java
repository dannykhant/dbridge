package dbridge.analysis.jdbc.trans.fold;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.node.JdbcSetQueryNode;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.trans.InputTree;
import dbridge.analysis.jdbc.trans.LeafConstructor;
import dbridge.analysis.jdbc.trans.OutputTree;
import dbridge.analysis.jdbc.trans.Rule;

/** Converts a fold containing a scalar JDBC query into a set-oriented query. */
public final class RuleT6 extends Rule {
    public RuleT6() {
        super(new InputTree(OpType.Fold, 0,
                        new InputTree(OpType.FuncExpr, 1, new InputTree(OpType.Any, 2)),
                        new InputTree(OpType.Any, 3), new InputTree(OpType.Any, 4)),
                new OutputTree(1, new LeafConstructor() {
                    @Override
                    public Node construct(Node source) {
                        return JdbcSetQueryNode.from(source);
                    }
                 }));
    }

    @Override
    public boolean checkPreconds(Node root) {
        return JdbcSetQueryNode.from(binding.get(1)) != null;
    }
}
