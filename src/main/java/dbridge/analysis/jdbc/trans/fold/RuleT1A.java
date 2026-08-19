package dbridge.analysis.jdbc.trans.fold;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleT1A extends Rule {
    public RuleT1A() { super(pattern(new InputTree(OpType.CartesianProd, 6), new InputTree(OpType.Any, 8)), new OutputTree(8)); }
    private static InputTree pattern(InputTree cart, InputTree result) { return new InputTree(OpType.Fold, 0, new InputTree(OpType.FuncExpr, 1, new InputTree(OpType.InvokeMethod, 2, new InputTree(OpType.PlaceholderVar, 3), new InputTree(OpType.MethodInsert, 4), new InputTree(OpType.FuncParams, 5, cart))), new InputTree(OpType.Bottom, 7), result); }
}
