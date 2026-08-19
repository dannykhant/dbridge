package dbridge.analysis.jdbc.trans.simplify;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleS1A extends Rule {
    public RuleS1A() { super(new InputTree(OpType.Eq, 0, new InputTree(OpType.Eq, 1), new InputTree(OpType.Zero, 2)), new OutputTree(1)); }
}
