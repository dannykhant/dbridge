package dbridge.analysis.jdbc.trans;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.node.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The right-hand side (consequent) pattern of a transformation rule. Three
 * kinds are supported: reference a bound input node by id (FROM_INPUT), reuse a
 * bound input node directly (FROM_SCRATCH), or build a fresh subtree (TREE).
 */
public class OutputTree {

    public enum Kind {
        FROM_INPUT, FROM_SCRATCH, TREE
    }

    private final Kind kind;
    private final int id;
    private final OpType opType;
    private final LeafConstructor leafConstructor;
    private final List<OutputTree> children = new ArrayList<>();

    /** Reference a node bound in the input pattern by its id. */
    public OutputTree(int id) {
        this.kind = Kind.FROM_INPUT;
        this.id = id;
        this.opType = null;
        this.leafConstructor = null;
    }

    private OutputTree(Kind kind, int id, LeafConstructor leafConstructor) {
        this.kind = kind;
        this.id = id;
        this.opType = null;
        this.leafConstructor = leafConstructor;
    }

    /** Reuse a bound input node directly. */
    public static OutputTree fromScratch(int id) {
        return new OutputTree(Kind.FROM_SCRATCH, id, bound -> bound);
    }

    /** Construct a fresh leaf using the node bound by the input id. */
    public OutputTree(int id, LeafConstructor leafConstructor) {
        this.kind = Kind.FROM_SCRATCH;
        this.id = id;
        this.opType = null;
        this.leafConstructor = leafConstructor;
    }

    /** Construct a fresh leaf without an input binding. */
    public OutputTree(LeafConstructor leafConstructor) {
        this(-1, leafConstructor);
    }

    /** Build a fresh subtree with the given op type and children. */
    public OutputTree(OpType opType, OutputTree... children) {
        this.kind = Kind.TREE;
        this.id = -1;
        this.opType = opType;
        this.leafConstructor = null;
        this.children.addAll(Arrays.asList(children));
    }

    public boolean isFromInput() {
        return kind == Kind.FROM_INPUT;
    }

    public boolean isFromScratch() {
        return kind == Kind.FROM_SCRATCH;
    }

    public boolean isTree() {
        return kind == Kind.TREE;
    }

    public int getId() {
        return id;
    }

    public OpType getOpType() {
        return opType;
    }

    public List<OutputTree> getChildren() {
        return children;
    }

    /** For FROM_SCRATCH: return the bound node itself. */
    public Node getNode(Node bound) {
        return leafConstructor == null ? bound : leafConstructor.construct(bound);
    }

    @Override
    public String toString() {
        if (isFromInput()) {
            return "in[" + id + "]";
        }
        if (isFromScratch()) {
            return "scratch[" + id + "]";
        }
        StringBuilder sb = new StringBuilder(opType.toString());
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
