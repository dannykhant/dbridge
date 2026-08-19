package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS5 extends Rule {
    public RuleS5() { super(new InputTree(OpType.InvokeMethod, 0, new InputTree(OpType.Dao, 1), new InputTree(OpType.Any, 2), new InputTree(OpType.Any, 3)), new OutputTree(2)); }
}
