package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS2 extends Rule {
    public RuleS2() { super(new InputTree(OpType.InvokeMethod, 0, new InputTree(OpType.Any, 1), new InputTree(OpType.MethodIterator, 2), new InputTree(OpType.Any, 3)), new OutputTree(1)); }
}
