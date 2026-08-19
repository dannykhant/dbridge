package dbridge.analysis.jdbc.expr;

import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.RetVarNode;
import dbridge.analysis.jdbc.expr.node.VarNode;
import dbridge.analysis.jdbc.analysis.AnalyzedLoopCandidate;
import dbridge.analysis.region.regions.ARegion;
import soot.Type;
import soot.Unit;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG IR: maps each program variable to the algebraic expression (a {@link Node}
 * DAG) that defines its value. This is the central data structure on which the
 * transformation rules operate.
 */
public class DIR {

    private final Map<VarNode, Node> veMap;
    private ARegion region;
    private final List<AnalyzedLoopCandidate> candidates = new ArrayList<>();

    public DIR() {
        veMap = new HashMap<>();
    }

    /** Shallow copy constructor. */
    public DIR(DIR other) {
        this.veMap = new HashMap<>();
        for (Map.Entry<VarNode, Node> entry : other.getVeMap().entrySet()) {
            this.veMap.put(entry.getKey(), entry.getValue());
        }
        this.candidates.addAll(other.candidates);
    }

    public void insert(VarNode target, Node expr) {
        veMap.put(target, expr);
    }

    /**
     * Insert an entry into the veMap, also keeping track of the originating
     * program statements. This information is used during code generation.
     */
    public void insert(VarNode target, Node expr, Unit... units) {
        for (Unit unit : units) {
            if (unit instanceof Stmt) {
                expr.addStmts((Stmt) unit);
            }
        }
        insert(target, expr);
    }

    public Map<VarNode, Node> getVeMap() {
        return veMap;
    }

    public void addCandidate(AnalyzedLoopCandidate candidate) {
        if (candidate != null && !candidates.contains(candidate)) candidates.add(candidate);
    }

    public List<AnalyzedLoopCandidate> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    public boolean contains(VarNode key) {
        return veMap.containsKey(key);
    }

    public Node find(VarNode key) {
        return veMap.get(key);
    }

    public Set<VarNode> getVars() {
        if (isEmpty()) {
            return new HashSet<>();
        }
        return veMap.keySet();
    }

    public boolean isEmpty() {
        return veMap == null || veMap.isEmpty();
    }

    public boolean hasUpdate() {
        return veMap.containsKey(VarNode.getUpdateVar());
    }

    public ARegion getRegion() {
        return region;
    }

    /** Return the type of the return variable if present, else null. */
    public Type findRetVarType() {
        for (Map.Entry<VarNode, Node> entry : veMap.entrySet()) {
            if (entry.getKey() instanceof RetVarNode) {
                return ((RetVarNode) entry.getKey()).getOrigRetVarType();
            }
        }
        return null;
    }

    /** Update the region for each node in the DIR.veMap. */
    public void updateRegion(ARegion region) {
        for (Map.Entry<VarNode, Node> entry : veMap.entrySet()) {
            entry.getValue().setRegion(region);
        }
    }

    @Override
    public String toString() {
        List<VarNode> keys = new ArrayList<>(veMap.keySet());
        Collections.sort(keys, (a, b) -> a.toString().compareTo(b.toString()));

        StringBuilder sb = new StringBuilder();
        for (VarNode key : keys) {
            sb.append("~~~ ").append(key).append(" ~~~\n");
            sb.append(veMap.get(key)).append("\n\n");
        }
        return sb.toString();
    }
}
