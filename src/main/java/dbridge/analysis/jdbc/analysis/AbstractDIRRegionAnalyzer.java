package dbridge.analysis.jdbc.analysis;

import dbridge.analysis.jdbc.expr.DIR;
import dbridge.analysis.region.api.RegionAnalysis;
import dbridge.analysis.region.exceptions.RegionAnalysisException;
import dbridge.analysis.region.regions.ARegion;

/**
 * Common logic for all DIR region analyzers.
 */
public abstract class AbstractDIRRegionAnalyzer implements RegionAnalysis<DIR> {

    @Override
    public DIR run(ARegion region) throws RegionAnalysisException {
        DIR dir = constructDIR(region);
        dir.updateRegion(region);
        return dir;
    }

    public abstract DIR constructDIR(ARegion region) throws RegionAnalysisException;
}
