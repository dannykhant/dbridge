package dbridge.analysis.region.regions;

import soot.Unit;
import soot.toolkits.graph.Block;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by K. Venkatesh Emani on 12/19/2016.
 */
public class Region extends ARegion {
    public Region(Block b) {
        this.head = b;
        this.regionType = RegionType.BasicBlockRegion;
    }

    @Override
    public Unit firstStmt() {
        return getHead().getHead();
    }

    @Override
    public Unit lastStmt() {
        return getHead().getTail();
    }

    @Override
    public List<Unit> getUnits() {
        List<Unit> units = new ArrayList<>();
        //modified by bhu

//        for (Unit aHead : head) {
//            units.add(aHead);
//        }
        Iterator unitIt=head.iterator();
        while(unitIt.hasNext()){
            units.add((Unit) unitIt.next());
        }
        return units;
    }

    @Override
    public String toString() {
        String toStr = super.toString() + "\n";
        for (Unit unit : getUnits()) {
            toStr += unit.toString() + "\n";
        }
        return toStr;
    }

}
