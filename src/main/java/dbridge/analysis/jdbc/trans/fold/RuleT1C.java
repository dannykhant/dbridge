package dbridge.analysis.jdbc.trans.fold;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleT1C extends Rule {
    public RuleT1C() { super(pattern(), new OutputTree(OpType.Seq, new OutputTree(OpType.CartesianProd, new OutputTree(OpType.ClassRef)), new OutputTree(9))); }
    private static InputTree pattern() { return new InputTree(OpType.Fold, 0, new InputTree(OpType.FuncExpr, 1, new InputTree(OpType.InvokeMethod, 2, new InputTree(OpType.PlaceholderVar, 3), new InputTree(OpType.MethodInsert, 4), new InputTree(OpType.FuncParams, 5, new InputTree(OpType.LazyFetch, 6, new InputTree(OpType.FieldRef, 7))))), new InputTree(OpType.Bottom, 8), new InputTree(OpType.Any, 9)); }
}
