package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS4 extends Rule {
    public RuleS4() { super(new InputTree(OpType.InvokeMethod, 0, new InputTree(OpType.Any, 1), new InputTree(OpType.FieldRef, 2, new InputTree(OpType.SelfRef, 3), new InputTree(OpType.Any, 4), new InputTree(OpType.Any, 5)), new InputTree(OpType.Any, 6)), new OutputTree(OpType.FieldRef, new OutputTree(1), new OutputTree(4), new OutputTree(5))); }
}
