package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS7C extends Rule {
    public RuleS7C() { super(new InputTree(OpType.CartesianProd, 0, new InputTree(OpType.CartesianProd, 1, new InputTree(OpType.Any, 2))), new OutputTree(OpType.CartesianProd, new OutputTree(2))); }
}
