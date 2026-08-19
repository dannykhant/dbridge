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
                if (children.length != 3 || !(children[0] instanceof FuncExprNode)) {
                    throw new IllegalArgumentException("Fold requires FuncExpr, init value, and loop collection");
                }
                return new FoldNode((FuncExprNode) children[0], children[1], children[2]);
            case InvokeMethod:
                return new InvokeMethodNode("", children);
            case ArithAdd:
            case ArithSub:
            case ArithMod:
            case Eq:
            case NotEq:
            case Gt:
            case Lt:
            case And:
            case Or:
                return new BinaryOpNode(opType, children.length > 0 ? children[0] : null,
                        children.length > 1 ? children[1] : null);
            case Var:
                throw new IllegalArgumentException("Var nodes require a Local");
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
            case Zero:
                return new ZeroNode();
            case One:
                return new OneNode();
            case MethodIterator:
            case MethodNext:
            case SelfRef:
            case Dao:
            case MethodBooleanValue:
            case LazyFetch:
            case MethodInsert:
                return new GenericNode(opType, children);
            case FuncParams:
                return new GenericNode(opType, children);
            case FuncExpr:
                return new FuncExprNode(children.length == 0 ? null : children[0]);
            case PlaceholderVar:
                return new PlaceholderVarNode();
            case Ternary:
            case Seq:
                return new GenericNode(opType, children);
            case Select:
                return new SelectNode(children.length > 0 ? children[0] : null,
                        children.length > 1 ? children[1] : null);
            case Project:
                return new ProjectNode(children.length > 0 ? children[0] : null,
                        children.length > 1 ? children[1] : null);
            case ClassRef:
                return new ClassRefNode((String) null);
            case FieldRef:
                return new FieldRefNode(null, null, null);
            case CartesianProd:
                return new CartesianProdNode(children);
            case ConstTable:
                return new ConstTableNode();
            case CountStar:
                if (opType == OpType.CountStar) {
                    return new CountStarNode();
                }
                return new GenericNode(opType, children);
            default:
                throw new IllegalArgumentException("Unsupported op type: " + opType);
        }
    }
}
