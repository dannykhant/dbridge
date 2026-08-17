package dbridge.analysis.jdbc.analysis;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.jdbc.expr.node.IfNode;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.VarNode;
import dbridge.analysis.region.exceptions.RegionAnalysisException;
import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.BranchRegionSpecial;

import java.util.Map;

/**
 * Analyzer for the special branch region (a single branch with no sibling).
 */
public class DIRBranchRegionSpecialAnalyzer extends AbstractDIRRegionAnalyzer {

    public static final DIRBranchRegionSpecialAnalyzer INSTANCE = new DIRBranchRegionSpecialAnalyzer();

    private DIRBranchRegionSpecialAnalyzer() {
    }

    @Override
    public DIR constructDIR(ARegion region) throws RegionAnalysisException {
        assert region instanceof BranchRegionSpecial;
        ARegion headRegion = region.getSubRegions().get(0);
        ARegion trueRegion = region.getSubRegions().get(1);

        DIR headDIR = (DIR) headRegion.analyze();
        DIR trueDIR = (DIR) trueRegion.analyze();
        Node condition = Utils.extractCondition(headDIR);

        DIR condDag = new DIR();
        for (Map.Entry<VarNode, Node> entry : trueDIR.getVeMap().entrySet()) {
            condDag.insert(entry.getKey(), new IfNode(condition, entry.getValue(), entry.getKey()));
        }
        return Utils.mergeSeqDirs(headDIR, condDag);
    }
}
