package dbridge.analysis.jdbc.util;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.VarNode;
import dbridge.visitor.NodeVisitor;

/**
 * Resolves variable references in a node with their defining expressions from a
 * preceding DIR, thereby flattening data dependencies.
 */
public class VarResolver implements NodeVisitor {

    private final DIR dir;

    public VarResolver(DIR dir) {
        this.dir = dir;
    }

    @Override
    public Node visit(Node node) {
        if (node instanceof VarNode && dir.contains((VarNode) node)) {
            return dir.find((VarNode) node);
        }
        return node;
    }
}
