package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS6 extends Rule {
    public RuleS6() { super(new InputTree(OpType.InvokeMethod, 0, new InputTree(OpType.Any, 1), new InputTree(OpType.MethodBooleanValue, 2), new InputTree(OpType.Any, 3)), new OutputTree(1)); }
}
