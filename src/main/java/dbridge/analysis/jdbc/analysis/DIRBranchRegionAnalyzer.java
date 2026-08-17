package dbridge.analysis.jdbc.analysis;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.jdbc.expr.node.IfNode;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.VarNode;
import dbridge.analysis.region.exceptions.RegionAnalysisException;
import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.BranchRegion;

import java.util.Map;

/**
 * Analyzer for a branch region: combines the true and false branch DIRs into
 * conditional (if-then-else) expressions keyed on the branch condition.
 */
public class DIRBranchRegionAnalyzer extends AbstractDIRRegionAnalyzer {

    public static final DIRBranchRegionAnalyzer INSTANCE = new DIRBranchRegionAnalyzer();

    private DIRBranchRegionAnalyzer() {
    }

    @Override
    public DIR constructDIR(ARegion region) throws RegionAnalysisException {
        assert region instanceof BranchRegion;
        ARegion headRegion = region.getSubRegions().get(0);
        ARegion trueRegion = region.getSubRegions().get(2);
        ARegion falseRegion = region.getSubRegions().get(1);

        DIR headDIR = (DIR) headRegion.analyze();
        DIR trueDIR = (DIR) trueRegion.analyze();
        DIR falseDIR = (DIR) falseRegion.analyze();
        Node condition = Utils.extractCondition(headDIR);

        DIR condRegDIR = new DIR();
        insertFromTrueDag(condRegDIR, condition, trueDIR, falseDIR);
        insertFromFalseDag(condRegDIR, condition, trueDIR, falseDIR);

        return Utils.mergeSeqDirs(headDIR, condRegDIR);
    }

    private void insertFromFalseDag(DIR condRegDIR, Node condition, DIR trueDIR, DIR falseDIR) {
        for (Map.Entry<VarNode, Node> entry : falseDIR.getVeMap().entrySet()) {
            VarNode var = entry.getKey();
            if (trueDIR.contains(var)) {
                continue;
            }
            condRegDIR.insert(var, new IfNode(condition, var, entry.getValue()));
        }
    }

    private void insertFromTrueDag(DIR condRegDIR, Node condition, DIR trueDIR, DIR falseDIR) {
        for (Map.Entry<VarNode, Node> entry : trueDIR.getVeMap().entrySet()) {
            VarNode var = entry.getKey();
            Node trueDag = entry.getValue();
            Node falseDag = falseDIR.contains(var) ? falseDIR.find(var) : var;
            condRegDIR.insert(var, new IfNode(condition, trueDag, falseDag));
        }
    }
}
