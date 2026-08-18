package dbridge.analysis.region.regions;

import soot.Body;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.*;
import soot.jimple.internal.JIfStmt;
import soot.toolkits.graph.*;

import java.util.*;

public class RegionGraph implements DirectedGraph<ARegion> {

    private BlockGraph blockGraph;
    public UnitGraph ug;
    private List<ARegion> regions = new ArrayList<ARegion>();

    public RegionGraph(Body b) {
        this.blockGraph = new BriefBlockGraph(b);
        UnitGraph unitGraph = new BriefUnitGraph(b);
        this.ug = unitGraph;

        Body body = blockGraph.getBody();
        unitGraph = new BriefUnitGraph(body);
        ug = unitGraph;
        wrapBlocks(unitGraph);
        //bhu-a little hack to identify true region
        setTrueRegion(regions);
        if (regions.size() > 1) {
            constructRegions();
        }

    }


    private void constructRegions() {
        boolean moreIterations = true;

        while (moreIterations) {
            moreIterations = false;
            ARegion merged = null;

            for (ARegion r : regions) {
                if (!r.canMerge()) {
                    continue;
                }
                merged = r.merge();
                moreIterations = true;
                break;
            }
            if (moreIterations) {
                regions.add(merged);
                regions.removeAll(merged.getSubRegions());
            }
         }
    }

    //adds all blocks except exception catch blocks to 'regions', and 'basicRegions' - Tejas
    private void wrapBlocks(UnitGraph unitGraph) {
        Map<Block, ARegion> basicRegions = new HashMap<Block, ARegion>();
        for (Block b : blockGraph) {
            //Modified By bhu
//            if(isFunctionDefinitionStmt(b)){
//                FunctionRegion fr = new FunctionRegion(b);
//
//                regions.add(fr);
//            }
//            else if (!isCatchBlock(b)) {
            //modified by bhu Ends, uncomment next line
                if (!isCatchBlock(b)) {
                List<Block> preds = b.getPreds();
                List<Block> nonPreds = new ArrayList<Block>();
                boolean addBlock = true;
                int count = 0;
                for (Block blk : preds) {
                    if (!basicRegions.containsKey(blk)) {
                        //loop body - Tejas
                        if (!(b.getSuccs().size() == 1 && b.getPreds().size() == 1 && b.getSuccs().get(0).equals(b
								.getPreds().get(0)))) {
                            //when will this condition satisfy in addition to the above one? - Tejas
                            if (blk.getIndexInMethod() < b.getIndexInMethod()) {
                                count++;
                                nonPreds.add(blk);
                            }
                        }
                    }
                }
                // blocks other than loop body and first block- Tejas
                if (count == preds.size() && b.getIndexInMethod() != 0)
                    addBlock = false;
                // loop body. anything else? - Tejas
                if (count < preds.size()) {
                    List<Block> newPreds = new ArrayList<Block>();
                    for (Block blk : preds) {
                        if (!(nonPreds.contains(blk)))
                            newPreds.add(blk);

                    }
                    b.setPreds(newPreds);
                }
                // handling a block which has only one stmt - goto and 1 pred
                // and 1 succ : ignore such a block

                // Loop in catch block
                if (b.getSuccs().size() == 1 && b.getPreds().size() == 1 && b.getPreds().get(0).equals(b.getSuccs().get(0))) {
                    count = 0;
                    Block blkLoopHead = b.getPreds().get(0);
                    List<Block> preds2 = blkLoopHead.getPreds();
                    List<Block> nonPreds2 = new ArrayList<Block>();
                    for (Block blk : preds2) {
                        if (!basicRegions.containsKey(blk)) {
                            if (blk.getIndexInMethod() < blkLoopHead.getIndexInMethod()) {
                                count++;
                            }
                        }
                    }
                    if (count == preds2.size() && blkLoopHead.getIndexInMethod() != 0)
                        addBlock = false;
                }

                if (addBlock) {
                    if (b.getHead() instanceof IdentityStmt) {
                        IdentityStmt i = (IdentityStmt) b.getHead();

                        if (i.getRightOp().toString().equals("@caughtexception"))
                            continue;
                    }
                    Region r = new Region(b);

                    regions.add(r);
                    basicRegions.put(b, r);
                }
            }
        }
        for (ARegion r : regions) {
            r.init(basicRegions);
        }
    }

    private boolean isCatchBlock(Block blk) {
        if (blk.getHead() instanceof IdentityStmt) {
            IdentityStmt i = (IdentityStmt) blk.getHead();
            if (i.getRightOp().toString().equals("@caughtexception"))
                return true;
        }
        return false;
    }

    private void setTrueRegion(List<ARegion> regions){
        for (ARegion r : regions) {
            if(r.getSuccRegions().size()==2){
                r.getSuccRegions().get(0).setTrueRegion(true);
            }
        }
    }
    @Override
    public List<ARegion> getHeads() {
        return regions;
    }

    @Override
    public List<ARegion> getTails() {
        return null;
    }

    @Override
    public List<ARegion> getPredsOf(ARegion s) {
        return Arrays.asList(new ARegion[]{s.getParent()});
    }

    @Override
    public List<ARegion> getSuccsOf(ARegion s) {
        return s.getSubRegions();
    }

    @Override
    public int size() {
        return regions.size();
    }

    @Override
    public Iterator<ARegion> iterator() {
        return regions.iterator();
    }

    public String print() {
        return regions.get(0).print();
    }
}
