package dbridge.visitor;

/**
 * Marker interface for objects that can be visited by a {@link NodeVisitor}.
 */
public interface Visitable {
    dbridge.analysis.jdbc.expr.node.Node accept(NodeVisitor visitor);
}
