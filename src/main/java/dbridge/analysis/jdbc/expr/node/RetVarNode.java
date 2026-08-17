package dbridge.analysis.jdbc.expr.node;

import soot.Type;

/**
 * Synthetic variable that represents the return value of the function being
 * analyzed. Carries the original return type for later code generation.
 */
public class RetVarNode extends VarNode {

    private static final RetVarNode RET_VAR = new RetVarNode();

    private Type origRetVarType;

    private RetVarNode() {
        super(null, "#ret");
    }

    public static RetVarNode getARetVar() {
        return RET_VAR;
    }

    public Type getOrigRetVarType() {
        return origRetVarType;
    }

    public void setOrigRetVarType(Type origRetVarType) {
        this.origRetVarType = origRetVarType;
    }
}
