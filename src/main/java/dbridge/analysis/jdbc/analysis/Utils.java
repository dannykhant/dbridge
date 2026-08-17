package dbridge.analysis.jdbc.analysis;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.UnAlgNode;
import dbridge.analysis.jdbc.expr.node.VarNode;
import dbridge.analysis.jdbc.util.VarResolver;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Package-local helpers shared by the DIR region analyzers.
 */
final class Utils {

    private Utils() {
    }

    /**
     * Resolve variable references in {@code followingDIR} using
     * {@code precedingDIR}, then merge the two DIRs into {@code precedingDIR}.
     */
    static DIR mergeSeqDirs(DIR precedingDIR, DIR followingDIR) {
        VarResolver resolver = new VarResolver(precedingDIR);
        for (Map.Entry<VarNode, Node> entry : followingDIR.getVeMap().entrySet()) {
            Node resolved = entry.getValue().accept(resolver);
            followingDIR.insert(entry.getKey(), resolved);
        }

        for (Map.Entry<VarNode, Node> entry : followingDIR.getVeMap().entrySet()) {
            VarNode key = entry.getKey();
            if (UnAlgNode.isUnAlgNode(precedingDIR.find(key))) {
                continue;
            }
            precedingDIR.insert(key, entry.getValue());
        }
        return precedingDIR;
    }

    static Node extractCondition(DIR dir) {
        VarNode condVar = VarNode.getACondVar();
        assert dir.contains(condVar);
        return dir.find(condVar);
    }

    static Set<VarNode> collectVars(Node node, Set<VarNode> out) {
        if (node instanceof VarNode) {
            out.add((VarNode) node);
        }
        for (Node child : node.getChildren()) {
            if (child != null) {
                collectVars(child, out);
            }
        }
        return out;
    }

    static Set<VarNode> collectVars(Node node) {
        return collectVars(node, new HashSet<>());
    }
}
