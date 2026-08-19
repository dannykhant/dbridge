package dbridge.analysis.jdbc;

import dbridge.analysis.region.RegionGraphBuilder;
import dbridge.analysis.region.regions.ARegion;
import soot.Body;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/** Builds bodies and regions for the application-method call stack. */
public final class FuncStackInfoBuilder extends SceneTransformer {
    private final FuncStackAnalyzer analyzer;

    public FuncStackInfoBuilder(FuncStackAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public void build() {
        internalTransform("dbridge.funcs", new java.util.HashMap());
    }

    @Override
    protected void internalTransform(String phaseName, java.util.Map options) {
        Queue<SootMethod> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(analyzer.getMethod());
        CallGraph callGraph = Scene.v().getCallGraph();
        while (!pending.isEmpty()) {
            SootMethod method = pending.remove();
            if (!visited.add(method.getSignature()) || !isInteresting(method)) {
                continue;
            }
            Body body = method.retrieveActiveBody();
            ARegion region = RegionGraphBuilder.build(body);
            if (region == null) {
                continue;
            }
            analyzer.addFunction(method, body, region);
            java.util.Iterator<?> edges = callGraph.edgesOutOf(method);
            while (edges.hasNext()) {
                SootMethod callee = (SootMethod) ((Edge) edges.next()).getTgt();
                if (isInteresting(callee)) {
                    pending.add(callee);
                }
            }
        }
    }

    private boolean isInteresting(SootMethod method) {
        String className = method.getDeclaringClass().getName();
        return method.getDeclaringClass().isApplicationClass()
                && !method.isConstructor()
                && !className.startsWith("java.")
                && !className.startsWith("javax.")
                && !className.startsWith("sun.")
                && !className.startsWith("soot.");
    }
}
