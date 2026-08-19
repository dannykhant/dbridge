package dbridge.analysis.jdbc.analysis;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.jdbc.expr.node.FoldNode;
import dbridge.analysis.jdbc.expr.node.IfNode;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.RetVarNode;
import dbridge.analysis.jdbc.expr.node.UnAlgNode;
import dbridge.analysis.jdbc.expr.node.VarNode;
import dbridge.analysis.jdbc.util.VarResolver;
import dbridge.analysis.region.exceptions.RegionAnalysisException;
import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.LoopRegion;
import soot.Unit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import soot.jimple.Stmt;

/**
 * Analyzer for a loop region. Detects variables aggregated over the loop (a
 * variable present in its own read set) and, when the loop-splitting
 * precondition is satisfied (the variable's only cyclic dependency is on
 * itself), replaces the loop with a {@link FoldNode}.
 */
public class DIRLoopRegionAnalyzer extends AbstractDIRRegionAnalyzer {

    public static final DIRLoopRegionAnalyzer INSTANCE = new DIRLoopRegionAnalyzer();

    private DIRLoopRegionAnalyzer() {
    }

    @Override
    public DIR constructDIR(ARegion region) throws RegionAnalysisException {
        assert region instanceof LoopRegion;
        ARegion head = region.getSubRegions().get(0);
        ARegion body = region.getSubRegions().get(1);

        DIR headDIR = (DIR) head.analyze();
        DIR bodyDIR = (DIR) body.analyze();

        VarNode loopVar = getLoopingCol(headDIR, bodyDIR);
        Map<VarNode, Set<VarNode>> varRsMap = fetchReadSets(bodyDIR);
        Set<VarNode> aggVars = findAggregatedVars(varRsMap);
        // The induction variable is self-referential (e.g. category = category - 1)
        // but is not an accumulator; exclude it from the cyclic-dependency test.
        if (loopVar != null) {
            aggVars.remove(loopVar);
        }

        DIR loopDIR = new DIR();
        // Loop exits: conditional branches whose target leaves the loop body
        // (the head may also contain post-loop statements such as a return).
        Set<Unit> bodyUnits = new HashSet<>(body.getUnits());
        List<Stmt> exits = new ArrayList<>();
        for (Unit unit : region.getUnits()) {
            if (unit instanceof soot.jimple.IfStmt
                    && !bodyUnits.contains(((soot.jimple.IfStmt) unit).getTarget())) {
                exits.add((Stmt) unit);
            }
        }
        Unit exitTarget = exits.isEmpty() ? null
                : ((soot.jimple.IfStmt) exits.get(0)).getTarget();
        // The guarded statements (head + body) drive the rewrite, but the exit
        // target and any other post-loop statements are not part of the loop.
        List<Stmt> statements = new ArrayList<>();
        for (Unit unit : region.getUnits()) {
            if (unit instanceof Stmt && unit != exitTarget) {
                statements.add((Stmt) unit);
            }
        }
        AnalyzedLoopCandidate candidate = new AnalyzedLoopCandidate(
                head.firstStmt(), statements, exits);
        for (VarNode aggVar : aggVars) {
            Set<VarNode> intersection = new HashSet<>(varRsMap.get(aggVar));
            intersection.retainAll(aggVars);

            if (intersection.size() == 1) {
                FoldNode foldNode = new FoldNode(bodyDIR.find(aggVar), aggVar, loopVar);
                foldNode.addLoopSwallowed((LoopRegion) region);
                loopDIR.insert(aggVar, foldNode, region.getUnits().toArray(new Unit[0]));
                loopDIR.addCandidate(candidate);
            } else {
                loopDIR.insert(aggVar, UnAlgNode.v());
            }
        }

        // The region graph can nest the loop exit (and hence the method's return
        // statement) inside the loop head. Propagate the return variable so the
        // enclosing analyzers can resolve the method's return value.
        VarNode retVar = RetVarNode.getARetVar();
        if (headDIR.contains(retVar)) {
            Node retNode = headDIR.find(retVar);
            while (retNode instanceof IfNode) {
                retNode = ((IfNode) retNode).getThenExpr();
            }
            // Resolve the return value against the loop DIR (post-loop value).
            retNode = retNode.accept(new VarResolver(loopDIR));
            loopDIR.insert(retVar, retNode);
        }

        return loopDIR;
    }

    private Set<VarNode> findAggregatedVars(Map<VarNode, Set<VarNode>> varRsMap) {
        Set<VarNode> aggVars = new HashSet<>();
        for (VarNode var : varRsMap.keySet()) {
            if (varRsMap.get(var).contains(var)) {
                aggVars.add(var);
            }
        }
        return aggVars;
    }

    private Map<VarNode, Set<VarNode>> fetchReadSets(DIR bodyDIR) {
        Map<VarNode, Set<VarNode>> varReadsetMap = new HashMap<>();
        for (VarNode var : bodyDIR.getVars()) {
            if (!var.isJimpleVar()) {
                continue;
            }
            varReadsetMap.put(var, bodyDIR.find(var).readSet());
        }
        return varReadsetMap;
    }

    /**
     * Find the induction variable of the loop: a Jimple variable that appears in
     * the loop condition and is also updated in the loop body.
     */
    private VarNode getLoopingCol(DIR headDIR, DIR bodyDIR) {
        VarNode condVar = VarNode.getACondVar();
        if (!headDIR.contains(condVar)) {
            return null;
        }
        Node cond = headDIR.find(condVar);
        Set<VarNode> condVars = Utils.collectVars(cond);

        VarNode loopVar = null;
        for (VarNode v : condVars) {
            if (v.isJimpleVar() && bodyDIR.contains(v)) {
                if (loopVar == null) {
                    loopVar = v;
                } else {
                    return null;
                }
            }
        }
        if (loopVar == null) {
            for (VarNode v : condVars) {
                if (v.isJimpleVar()) {
                    return v;
                }
            }
        }
        return loopVar;
    }
}
