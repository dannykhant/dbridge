package dbridge.analysis.jdbc.trans.fold;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.node.CountStarNode;
import dbridge.analysis.jdbc.trans.*;
public class RuleT5 extends Rule {
    public RuleT5() { super(new InputTree(OpType.Fold, 0, new InputTree(OpType.FuncExpr, 1, new InputTree(OpType.ArithAdd, 2, new InputTree(OpType.Any, 3), new InputTree(OpType.One, 4))), new InputTree(OpType.Zero, 5), new InputTree(OpType.Any, 6)), new OutputTree(OpType.Project, new OutputTree(6), new OutputTree(node -> new CountStarNode()))); }
}
