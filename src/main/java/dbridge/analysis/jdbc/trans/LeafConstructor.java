package dbridge.analysis.jdbc.trans;

import dbridge.analysis.jdbc.expr.node.Node;

/** Constructs a fresh leaf from the node bound by an output reference. */
@FunctionalInterface
public interface LeafConstructor {
    Node construct(Node source);
}
