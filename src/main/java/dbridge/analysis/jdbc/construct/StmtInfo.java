package dbridge.analysis.jdbc.construct;

import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.VarNode;

/**
 * Result of mapping a Jimple statement to a DIR entry: the variable being
 * defined (if any) and the defining expression node (if any).
 */
public class StmtInfo {

    public static final StmtInfo nullInfo = new StmtInfo(null, null);

    private final VarNode target;
    private final Node expr;

    public StmtInfo(VarNode target, Node expr) {
        this.target = target;
        this.expr = expr;
    }

    public VarNode getTarget() {
        return target;
    }

    public Node getExpr() {
        return expr;
    }

    public boolean isNull() {
        return target == null && expr == null;
    }
}
