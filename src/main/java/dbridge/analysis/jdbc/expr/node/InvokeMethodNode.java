package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;

/**
 * A method invocation. child[0] (if present) is the receiver, remaining
 * children are the arguments.
 */
public class InvokeMethodNode extends Node {

    private final String methodName;
    private final String methodSignature;

    public InvokeMethodNode(String methodName, Node... children) {
        this(methodName, null, children);
    }

    public InvokeMethodNode(String methodName, String methodSignature, Node... children) {
        super(OpType.InvokeMethod, children);
        this.methodName = methodName;
        this.methodSignature = methodSignature;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public Node getReceiver() {
        return getNumChildren() > 0 ? getChild(0) : null;
    }

    public Node getArg(int i) {
        return getChild(i + 1);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(methodName).append('(');
        for (int i = 0; i < getNumChildren(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(getChild(i));
        }
        sb.append(')');
        return sb.toString();
    }
}
