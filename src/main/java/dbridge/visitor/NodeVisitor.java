package dbridge.visitor;

import dbridge.analysis.jdbc.expr.node.Node;

/**
 * Visitor over expression DAG nodes. A transformation rule or a resolver
 * implements this interface and is invoked through {@code Node.accept}.
 */
public interface NodeVisitor {
    Node visit(Node node);
}
