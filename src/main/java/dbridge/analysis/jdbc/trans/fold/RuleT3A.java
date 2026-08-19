package dbridge.analysis.jdbc.trans.fold;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleT3A extends Rule {
    public RuleT3A() { super(pattern(), new OutputTree(OpType.Select, new OutputTree(OpType.CartesianProd, new OutputTree(10), new OutputTree(13)), new OutputTree(11))); }
    private static InputTree pattern() { return new InputTree(OpType.Fold, 0, new InputTree(OpType.FuncExpr, 1, new InputTree(OpType.Fold, 2, new InputTree(OpType.FuncExpr, 3, new InputTree(OpType.InvokeMethod, 4, new InputTree(OpType.PlaceholderVar, 5), new InputTree(OpType.MethodInsert, 6), new InputTree(OpType.Any, 7))), new InputTree(OpType.Any, 8), new InputTree(OpType.Select, 9, new InputTree(OpType.Any, 10), new InputTree(OpType.Any, 11)))), new InputTree(OpType.Bottom, 12), new InputTree(OpType.Any, 13)); }
}
