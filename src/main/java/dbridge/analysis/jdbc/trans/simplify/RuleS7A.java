package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS7A extends Rule {
    public RuleS7A() { super(new InputTree(OpType.CartesianProd, 0, new InputTree(OpType.CartesianProd, 1, new InputTree(OpType.Any, 2)), new InputTree(OpType.Any, 3)), new OutputTree(OpType.CartesianProd, new OutputTree(2), new OutputTree(3))); }
}
