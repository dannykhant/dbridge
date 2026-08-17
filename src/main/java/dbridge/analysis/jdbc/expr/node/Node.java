package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.LoopRegion;
import dbridge.visitor.NodeVisitor;
import dbridge.visitor.Visitable;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A node in the expression DAG (DIR). Each node has an {@link OpType} and zero
 * or more children. Nodes may remember the region and the Jimple statements
 * that produced them, which is used later during code generation.
 */
public abstract class Node implements Visitable {

    private final OpType opType;
    private final List<Node> children = new ArrayList<>();
    private ARegion region;
    private final List<Stmt> stmts = new ArrayList<>();

    protected Node(OpType opType) {
        this.opType = opType;
    }

    protected Node(OpType opType, Node... children) {
        this(opType);
        this.children.addAll(Arrays.asList(children));
    }

    public OpType getOpType() {
        return opType;
    }

    public void addChild(Node child) {
        children.add(child);
    }

    public Node getChild(int i) {
        return children.get(i);
    }

    public int getNumChildren() {
        return children.size();
    }

    public List<Node> getChildren() {
        return children;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Visitor pattern: post-order depth-first traversal. Visits each child,
     * replaces it with the returned node if changed, and finally visits this
     * node. Tailored to resolvers and transformation rules.
     */
    @Override
    public Node accept(NodeVisitor visitor) {
        if (isLeaf()) {
            return visitor.visit(this);
        }
        for (int i = 0; i < children.size(); i++) {
            Node child = children.get(i);
            if (child == null) {
                continue;
            }
            Node result = child.accept(visitor);
            if (child != result) {
                children.set(i, result);
            }
        }
        return visitor.visit(this);
    }

    /**
     * The set of variables read by this expression. Default: union of the read
     * sets of the children. {@link VarNode} overrides this to return itself.
     */
    public Set<VarNode> readSet() {
        Set<VarNode> readSet = new HashSet<>();
        for (Node child : children) {
            readSet.addAll(child.readSet());
        }
        return readSet;
    }

    public ARegion getRegion() {
        return region;
    }

    public void setRegion(ARegion region) {
        this.region = region;
    }

    public void addStmts(Stmt... units) {
        stmts.addAll(Arrays.asList(units));
    }

    public List<Stmt> getStmts() {
        return stmts;
    }

    /**
     * Loops that have been "swallowed" (removed) by a fold transformation.
     * Only {@link FoldNode} overrides this.
     */
    public List<LoopRegion> getLoopsSwallowed() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
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
