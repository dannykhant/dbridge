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
    Any, Bottom, UnAlg
}
