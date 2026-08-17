package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.region.regions.LoopRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the aggregation (fold) performed over the iterations of a loop.
 * Created by the loop region analyzer when the loop-splitting precondition is
 * satisfied, and remembers the loop(s) it has "swallowed".
 */
public class FoldNode extends Node {

    private final VarNode aggVar;
    private final VarNode loopVar;
    private final List<LoopRegion> loopsSwallowed = new ArrayList<>();

    public FoldNode(Node bodyExpr, VarNode aggVar, VarNode loopVar) {
        super(OpType.Fold, bodyExpr);
        this.aggVar = aggVar;
        this.loopVar = loopVar;
    }

    public Node getBodyExpr() {
        return getChild(0);
    }

    public VarNode getAggVar() {
        return aggVar;
    }

    public VarNode getLoopVar() {
        return loopVar;
    }

    public void addLoopSwallowed(LoopRegion region) {
        loopsSwallowed.add(region);
    }

    @Override
    public List<LoopRegion> getLoopsSwallowed() {
        return loopsSwallowed;
    }
}
