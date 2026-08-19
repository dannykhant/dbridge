package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS9 extends Rule {
    public RuleS9() { super(new InputTree(OpType.InvokeMethod, 0, new InputTree(OpType.FieldRef, 1), new InputTree(OpType.Dao, 2), new InputTree(OpType.Any, 3)), new OutputTree(2)); }
}
