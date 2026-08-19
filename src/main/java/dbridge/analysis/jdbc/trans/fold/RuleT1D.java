package dbridge.analysis.jdbc.trans.fold;
import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.trans.*;
public class RuleT1D extends Rule {
    public RuleT1D() { super(pattern(), new OutputTree(OpType.Project, new OutputTree(8), new OutputTree(6))); }
    private static InputTree pattern() { return new InputTree(OpType.Fold, 0, new InputTree(OpType.FuncExpr, 1, new InputTree(OpType.InvokeMethod, 2, new InputTree(OpType.PlaceholderVar, 3), new InputTree(OpType.MethodInsert, 4), new InputTree(OpType.FuncParams, 5, new InputTree(OpType.FieldRef, 6)))), new InputTree(OpType.Bottom, 7), new InputTree(OpType.Any, 8)); }
}
