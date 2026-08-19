package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS7B extends Rule {
    public RuleS7B() { super(new InputTree(OpType.CartesianProd, 0, new InputTree(OpType.Any, 1), new InputTree(OpType.CartesianProd, 2, new InputTree(OpType.Any, 3))), new OutputTree(OpType.CartesianProd, new OutputTree(1), new OutputTree(3))); }
}
