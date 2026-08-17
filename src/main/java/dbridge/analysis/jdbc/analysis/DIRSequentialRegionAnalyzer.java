package dbridge.analysis.jdbc.analysis;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.region.exceptions.RegionAnalysisException;
import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.SequentialRegion;

/**
 * Analyzer for a sequential region: merges the DIRs of its two sub-regions.
 */
public class DIRSequentialRegionAnalyzer extends AbstractDIRRegionAnalyzer {

    public static final DIRSequentialRegionAnalyzer INSTANCE = new DIRSequentialRegionAnalyzer();

    private DIRSequentialRegionAnalyzer() {
    }

    @Override
    public DIR constructDIR(ARegion region) throws RegionAnalysisException {
        assert region instanceof SequentialRegion;
        DIR merged = null;
        for (ARegion sub : region.getSubRegions()) {
            DIR subDir = (DIR) sub.analyze();
            merged = merged == null ? subDir : Utils.mergeSeqDirs(merged, subDir);
        }
        return merged == null ? new DIR() : merged;
    }
}
