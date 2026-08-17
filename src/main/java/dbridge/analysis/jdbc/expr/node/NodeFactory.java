package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/**
 * Constructs expression DAG nodes from an {@link OpType} and child nodes.
 * Used by the transformation rule engine to instantiate output patterns.
 */
public final class NodeFactory {

    private NodeFactory() {
    }

    public static Node constructFromOpType(OpType opType, Node... children) {
        switch (opType) {
            case Fold:
                return new FoldNode(children.length > 0 ? children[0] : null, null, null);
            case InvokeMethod:
                return new InvokeMethodNode("", children);
            case ArithAdd:
            case Eq:
            case NotEq:
            case Gt:
            case Lt:
            case And:
            case Or:
                return new BinaryOpNode(opType, children.length > 0 ? children[0] : null,
                        children.length > 1 ? children[1] : null);
            case Var:
                return new VarNode((soot.Local) null);
            case RetVar:
                return RetVarNode.getARetVar();
            case Param:
                return new ParamNode();
            case Value:
                return new ValueNode(null);
            case StringConst:
                return new StringConstNode("");
            case If:
                return new IfNode(
                        children.length > 0 ? children[0] : null,
                        children.length > 1 ? children[1] : null,
                        children.length > 2 ? children[2] : null);
            case UnAlg:
                return UnAlgNode.v();
            case Bottom:
                return new BinaryOpNode(OpType.Bottom, null, null);
            default:
                throw new IllegalArgumentException("Unsupported op type: " + opType);
        }
    }
}
