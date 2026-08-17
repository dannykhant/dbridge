package dbridge.analysis.jdbc.analysis;

import dbridge.analysis.jdbc.construct.StmtDIRConstructionHandler;
import dbridge.analysis.jdbc.construct.StmtInfo;
import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.VarNode;
import dbridge.analysis.jdbc.util.VarResolver;
import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.Region;
import soot.Unit;

import java.util.Iterator;

/**
 * Analyzer for a basic-block region: maps each assignment statement to a DIR
 * entry, resolving variable references with the current DIR as it goes.
 */
public class DIRRegionAnalyzer extends AbstractDIRRegionAnalyzer {

    public static final DIRRegionAnalyzer INSTANCE = new DIRRegionAnalyzer();

    private DIRRegionAnalyzer() {
    }

    @Override
    public DIR constructDIR(ARegion region) {
        assert region instanceof Region;
        DIR dir = new DIR();

        Iterator<Unit> iterator = ((Region) region).getHead().iterator();
        while (iterator.hasNext()) {
            Unit unit = iterator.next();
            StmtInfo stmtInfo = StmtDIRConstructionHandler.constructDagSS(unit);
            if (stmtInfo == null || stmtInfo.isNull()) {
                continue;
            }
            VarNode dest = stmtInfo.getTarget();
            Node source = stmtInfo.getExpr();
            if (dest == null || source == null) {
                continue;
            }
            Node resolved = source.accept(new VarResolver(dir));
            dir.insert(dest, resolved, unit);
        }
        return dir;
    }
}
