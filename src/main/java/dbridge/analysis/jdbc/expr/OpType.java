package dbridge.analysis.jdbc.expr;

/**
 * The set of expression DAG node types understood by DBridge.
 */
public enum OpType {
    Fold,
    InvokeMethod,
    ArithAdd, Eq, NotEq, Gt, Lt, And, Or,
    Var, RetVar, Param, Value, StringConst,
    If,
    Any, Bottom, UnAlg,

    // Operators used by the paper's transformation rules.  The JDBC DIR does
    // not currently construct these nodes, but retaining their labels lets the
    // rule engine represent and match the complete rule vocabulary.
    Zero, One, MethodIterator, MethodNext, SelfRef, FieldRef, Dao,
    MethodBooleanValue, CartesianProd, LazyFetch, PlaceholderVar,
    MethodInsert, FuncParams, FuncExpr, Ternary, Select, Seq, Project,
    ClassRef, ConstTable, CountStar
}
