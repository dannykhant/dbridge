package dbridge.analysis.jdbc.trans.fold;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleT2 extends Rule {
    public RuleT2() { super(new InputTree(OpType.Fold, 0, new InputTree(OpType.FuncExpr, 1, new InputTree(OpType.Ternary, 2, new InputTree(OpType.Any, 3), new InputTree(OpType.Any, 4), new InputTree(OpType.PlaceholderVar, 5))), new InputTree(OpType.Any, 6), new InputTree(OpType.Any, 7)), new OutputTree(OpType.Fold, new OutputTree(OpType.FuncExpr, new OutputTree(4)), new OutputTree(6), new OutputTree(OpType.Select, new OutputTree(7), new OutputTree(3)))); }
}
