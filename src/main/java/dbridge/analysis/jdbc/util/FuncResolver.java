package dbridge.analysis.jdbc.util;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.jdbc.expr.node.InvokeMethodNode;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.RetVarNode;
import dbridge.visitor.NodeVisitor;

import java.util.Map;

/** Replaces supported application calls with the callee's return expression. */
public final class FuncResolver implements NodeVisitor {
    private final Map<String, DIR> functions;

    public FuncResolver(Map<String, DIR> functions) {
        this.functions = functions;
    }

    @Override
    public Node visit(Node node) {
        if (!(node instanceof InvokeMethodNode)) {
            return node;
        }
        InvokeMethodNode call = (InvokeMethodNode) node;
        DIR dir = functions.get(call.getMethodSignature());
        if (dir == null && call.getMethodSignature() == null) {
            dir = findByName(call.getMethodName());
        }
        if (dir == null || !dir.contains(RetVarNode.getARetVar())) {
            return node;
        }
        return dir.find(RetVarNode.getARetVar()).accept(this);
    }

    private DIR findByName(String name) {
        DIR result = null;
        for (Map.Entry<String, DIR> entry : functions.entrySet()) {
            if (entry.getKey().contains(" " + name + "(")) {
                if (result != null) {
                    return null;
                }
                result = entry.getValue();
            }
        }
        return result;
    }
}
