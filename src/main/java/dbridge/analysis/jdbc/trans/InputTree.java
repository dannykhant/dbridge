package dbridge.analysis.jdbc.trans;

import dbridge.analysis.jdbc.expr.OpType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The left-hand side (antecedent) pattern of a transformation rule. A tree of
 * {@link OpType}s with an integer id on each node used to bind matched nodes.
 */
public class InputTree {

    private final OpType opType;
    private final int id;
    private final List<InputTree> children = new ArrayList<>();

    public InputTree(OpType opType, int id) {
        this.opType = opType;
        this.id = id;
    }

    public InputTree(OpType opType, int id, InputTree... children) {
        this(opType, id);
        this.children.addAll(Arrays.asList(children));
    }

    public OpType getOpType() {
        return opType;
    }

    public int getId() {
        return id;
    }

    public List<InputTree> getChildren() {
        return children;
    }

    public InputTree getChild(int i) {
        return children.get(i);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public int getNumChildren() {
        return children.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(opType.toString()).append('[').append(id).append(']');
        if (!children.isEmpty()) {
            sb.append('(');
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(children.get(i));
            }
            sb.append(')');
        }
        return sb.toString();
    }
}
