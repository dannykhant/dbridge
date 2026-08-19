package dbridge.analysis.jdbc.trans.fold;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.node.ConstTableNode;
import dbridge.analysis.jdbc.expr.node.CountStarNode;
import dbridge.analysis.jdbc.expr.node.ZeroNode;
import dbridge.analysis.jdbc.trans.*;
public class RuleT4 extends Rule {
    public RuleT4() { super(new InputTree(OpType.Fold, 0, new InputTree(OpType.FuncExpr, 1, new InputTree(OpType.One, 2)), new InputTree(OpType.Zero, 3), new InputTree(OpType.Any, 4)), new OutputTree(OpType.Project, new OutputTree(OpType.CartesianProd, new OutputTree(node -> new ConstTableNode())), new OutputTree(OpType.Gt, new OutputTree(OpType.Project, new OutputTree(4), new OutputTree(node -> new CountStarNode())), new OutputTree(node -> new ZeroNode())))); }
}
