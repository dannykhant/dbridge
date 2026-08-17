package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import soot.Local;
import soot.Type;

import java.util.Collections;
import java.util.Set;

/**
 * A program variable. Used both as the key of the DIR map and as a leaf in
 * expression DAGs. Synthetic variables (condition var, update var, return var)
 * have no associated Jimple {@link Local}.
 */
public class VarNode extends Node {

    private static final VarNode COND_VAR = new VarNode(null, "#cond");
    private static final VarNode UPDATE_VAR = new VarNode(null, "#update");

    private final Local local;
    private final String name;

    public VarNode(Local local) {
        super(OpType.Var);
        this.local = local;
        this.name = local.getName();
    }

    protected VarNode(Local local, String name) {
        super(OpType.Var);
        this.local = local;
        this.name = name;
    }

    public static VarNode getACondVar() {
        return COND_VAR;
    }

    public static VarNode getUpdateVar() {
        return UPDATE_VAR;
    }

    public Local getLocal() {
        return local;
    }

    public boolean isJimpleVar() {
        return local != null;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return local != null ? local.getType() : null;
    }

    @Override
    public Set<VarNode> readSet() {
        return Collections.singleton(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VarNode)) {
            return false;
        }
        VarNode varNode = (VarNode) o;
        return name.equals(varNode.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
