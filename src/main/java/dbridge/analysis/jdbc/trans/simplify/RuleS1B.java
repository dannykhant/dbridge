package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS1B extends Rule {
    public RuleS1B() { super(new InputTree(OpType.NotEq, 0, new InputTree(OpType.Eq, 1, new InputTree(OpType.Any, 2), new InputTree(OpType.Any, 3)), new InputTree(OpType.Zero, 4)), new OutputTree(OpType.NotEq, new OutputTree(2), new OutputTree(3))); }
}
