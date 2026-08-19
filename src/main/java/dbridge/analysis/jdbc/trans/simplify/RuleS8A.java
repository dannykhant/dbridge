package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS8A extends Rule {
    public RuleS8A() { super(new InputTree(OpType.InvokeMethod, 0, new InputTree(OpType.CartesianProd, 1), new InputTree(OpType.FieldRef, 2), new InputTree(OpType.Any, 3)), new OutputTree(2)); }
}
