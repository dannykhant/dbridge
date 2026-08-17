package dbridge.analysis.region;

import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.RegionGraph;
import soot.Body;

/**
 * Utility to build a region graph (structured control flow tree) for a Soot
 * method body. The returned region is the top-most region of the method.
 */
public class RegionGraphBuilder {

    private RegionGraphBuilder() {
    }

    /**
     * Build the region graph for the given method body and return the top-most
     * region, or {@code null} if the body could not be structured.
     */
    public static ARegion build(Body body) {
        RegionGraph regionGraph = new RegionGraph(body);
        if (regionGraph.getHeads().isEmpty()) {
            return null;
        }
        return regionGraph.getHeads().get(0);
    }
}
