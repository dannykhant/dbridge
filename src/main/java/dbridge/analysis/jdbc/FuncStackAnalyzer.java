package dbridge.analysis.jdbc;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.RetVarNode;
import dbridge.analysis.jdbc.expr.node.UnAlgNode;
import dbridge.analysis.jdbc.analysis.AnalyzedLoopCandidate;
import dbridge.analysis.jdbc.util.FuncResolver;
import dbridge.analysis.region.exceptions.RegionAnalysisException;
import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.LoopRegion;
import soot.Body;
import soot.SootMethod;
import soot.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/** Holds DIRs for the analyzed method and its supported application callees. */
public class FuncStackAnalyzer {
    private final SootMethod method;
    private final Stack<String> funcCallStack = new Stack<>();
    private final Map<String, ARegion> funcRegionMap = new HashMap<>();
    private final Map<String, Body> funcBodyMap = new HashMap<>();
    private final Map<String, DIR> funcDIRMap = new HashMap<>();

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

    public void addFunction(SootMethod function, Body functionBody, ARegion region) {
        String signature = function.getSignature();
        funcCallStack.add(signature);
        funcBodyMap.put(signature, functionBody);
        funcRegionMap.put(signature, region);
        if (function == method) {
            body = functionBody;
            topRegion = region;
        }
    }

    public void run() {
        try {
            while (!funcCallStack.isEmpty()) {
                String signature = funcCallStack.pop();
                DIR functionDir = (DIR) funcRegionMap.get(signature).analyze();
                if (functionDir == null) {
                    success = false;
                    return;
                }
                Node functionReturn = functionDir.find(RetVarNode.getARetVar());
                funcDIRMap.put(signature, functionDir);
            }
            dir = funcDIRMap.get(method.getSignature());
            if (dir == null || !dir.contains(RetVarNode.getARetVar())) {
                return;
            }
            Node original = dir.find(RetVarNode.getARetVar());
            retNode = original.accept(new FuncResolver(funcDIRMap));
            if (original.getRegion() != null) {
                retNode.setRegion(original.getRegion());
            }
            for (dbridge.analysis.region.regions.LoopRegion loop : original.getLoopsSwallowed()) {
                retNode.addLoopSwallowed(loop);
            }
            dir.insert(RetVarNode.getARetVar(), retNode);
            retRegion = retNode.getRegion();
            retType = dir.findRetVarType();
            loopsSwallowed = retNode.getLoopsSwallowed();
            success = !UnAlgNode.isUnAlgNode(retNode);
        } catch (RegionAnalysisException | RuntimeException e) {
            success = false;
        }
    }

    public boolean isSuccess() { return success; }
    public Node getRetNode() { return retNode; }
    public ARegion getRetRegion() { return retRegion; }
    public Type getRetType() { return retType; }
    public List<LoopRegion> getLoopsSwallowed() { return loopsSwallowed; }
    public Body getBody() { return body; }
    public Body getTopLevelFuncBody() { return body; }
    public DIR getDir() { return dir; }
    public ARegion getTopRegion() { return topRegion; }
    public SootMethod getMethod() { return method; }
    public Node getExpr() { return retNode; }
    public List<AnalyzedLoopCandidate> getAnalyzedLoopCandidates() {
        return dir == null ? java.util.Collections.<AnalyzedLoopCandidate>emptyList() : dir.getCandidates();
    }
}
