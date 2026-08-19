package dbridge.analysis.jdbc;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.RetVarNode;
import dbridge.analysis.jdbc.expr.node.UnAlgNode;
import dbridge.analysis.jdbc.expr.node.VarNode;
import dbridge.analysis.jdbc.trans.TransDriver;
import dbridge.analysis.region.RegionGraphBuilder;
import dbridge.analysis.region.exceptions.RegionAnalysisException;
import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.LoopRegion;
import soot.Body;
import soot.SootMethod;
import soot.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the result of analyzing a single function: its DIR, the expression for
 * its return value, and the loops that were "swallowed" by folding.
 */
public class FuncStackAnalyzer {

    private final SootMethod method;

    private Body body;
    private ARegion topRegion;
    private DIR dir;
    private Node retNode;
    private ARegion retRegion;
    private Type retType;
    private List<LoopRegion> loopsSwallowed = new ArrayList<>();
    private boolean success;

    public FuncStackAnalyzer(SootMethod method) {
        this.method = method;
    }

    public void run() {
        body = method.retrieveActiveBody();
        topRegion = RegionGraphBuilder.build(body);
        if (topRegion == null) {
            success = false;
            return;
        }
        try {
            dir = (DIR) topRegion.analyze();
        } catch (RegionAnalysisException e) {
            success = false;
            return;
        }
        VarNode retVar = RetVarNode.getARetVar();
        if (!dir.contains(retVar)) {
            success = false;
            return;
        }
        retNode = dir.find(retVar);
        retNode = TransDriver.applyAllTransRules(retNode);
        dir.insert(retVar, retNode);
        retRegion = retNode.getRegion();
        retType = dir.findRetVarType();
        loopsSwallowed = retNode.getLoopsSwallowed();
        success = !UnAlgNode.isUnAlgNode(retNode);
    }

    public boolean isSuccess() {
        return success;
    }

    public Node getRetNode() {
        return retNode;
    }

    public ARegion getRetRegion() {
        return retRegion;
    }

    public Type getRetType() {
        return retType;
    }

    public List<LoopRegion> getLoopsSwallowed() {
        return loopsSwallowed;
    }

    public Body getBody() {
        return body;
    }

    public DIR getDir() {
        return dir;
    }

    public ARegion getTopRegion() {
        return topRegion;
    }

    public SootMethod getMethod() {
        return method;
    }
}
