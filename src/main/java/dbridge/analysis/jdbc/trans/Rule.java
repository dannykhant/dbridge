package dbridge.analysis.jdbc.trans;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.NodeFactory;
import dbridge.visitor.NodeVisitor;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A single transformation rule. Matches an {@link InputTree} pattern against an
 * expression DAG node, binds matched subnodes by id, checks preconditions, and
 * constructs the replacement expression from the {@link OutputTree}.
 */
public class Rule implements NodeVisitor {

    private final InputTree inPattern;
    private final OutputTree outPattern;
    protected Map<Integer, Node> binding;
    private Node inExpr;

    public Rule(InputTree inPattern, OutputTree outPattern) {
        this.inPattern = inPattern;
        this.outPattern = outPattern;
    }

    /**
     * Check whether the rule is applicable on this expression. The base
     * implementation returns true; individual rules may override to add
     * preconditions using the (already populated) bindings.
     */
    public boolean checkPreconds(Node root) {
        return true;
    }

    private boolean match(Node inExpr) {
        this.inExpr = inExpr;
        binding = new HashMap<>();
        return matchHelper(this.inExpr, this.inPattern);
    }

    private boolean matchHelper(Node inExpr, InputTree inPattern) {
        OpType exprOp = inExpr.getOpType();
        OpType patternOpType = inPattern.getOpType();
        boolean isMatch = false;

        if (patternOpType == OpType.Any || exprOp == patternOpType) {
            isMatch = true;
            binding.put(inPattern.getId(), inExpr);

            if (inPattern.hasChildren()) {
                if (inExpr.getNumChildren() != inPattern.getNumChildren()) {
                    doCleanup();
                    return false;
                }
                for (int i = 0; i < inPattern.getNumChildren(); i++) {
                    Node exprChild = inExpr.getChild(i);
                    InputTree patternChild = inPattern.getChild(i);
                    if (matchHelper(exprChild, patternChild)) {
                        binding.put(patternChild.getId(), exprChild);
                    } else {
                        doCleanup();
                        return false;
                    }
                }
            }
        }

        return isMatch;
    }

    private Node apply(Node inExpr) {
        boolean isMatch = match(inExpr);
        boolean isApplicable = isMatch && checkPreconds(inExpr);

        if (isApplicable) {
            Node outExpr = getOutputExpr(this.outPattern);
            doCleanup();
            for (Stmt stmt : inExpr.getStmts()) {
                outExpr.addStmts(stmt);
            }
            return outExpr;
        }
        return this.inExpr;
    }

    private void doCleanup() {
        binding.clear();
    }

    protected Node getOutputExpr(OutputTree outPattern) {
        if (outPattern.isFromInput()) {
            return binding.get(outPattern.getId());
        } else if (outPattern.isFromScratch()) {
            Node node = binding.get(outPattern.getId());
            return outPattern.getNode(node);
        } else {
            assert outPattern.isTree();
            OpType opType = outPattern.getOpType();
            List<Node> childrenExpr = new ArrayList<>();
            for (OutputTree childTree : outPattern.getChildren()) {
                childrenExpr.add(getOutputExpr(childTree));
            }
            return NodeFactory.constructFromOpType(opType, childrenExpr.toArray(new Node[0]));
        }
    }

    @Override
    public Node visit(Node node) {
        return apply(node);
    }

    @Override
    public String toString() {
        return "In Pattern:\n" + inPattern.toString() + "\n\nOut Pattern:\n" + outPattern.toString();
    }

    public InputTree getInPattern() {
        return inPattern;
    }

    public OutputTree getOutPattern() {
        return outPattern;
    }
}
