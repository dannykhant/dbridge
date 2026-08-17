package dbridge.analysis.jdbc;

import dbridge.analysis.jdbc.analysis.DIRBranchRegionAnalyzer;
import dbridge.analysis.jdbc.analysis.DIRBranchRegionSpecialAnalyzer;
import dbridge.analysis.jdbc.analysis.DIRLoopRegionAnalyzer;
import dbridge.analysis.jdbc.analysis.DIRRegionAnalyzer;
import dbridge.analysis.jdbc.analysis.DIRSequentialRegionAnalyzer;
import dbridge.analysis.region.api.RegionAnalyzer;
import soot.G;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;

/**
 * Orchestrator for the DBridge analysis: sets up Soot, initializes the region
 * analysis framework, and analyzes a single function for set-oriented
 * transformation opportunities.
 */
public class JdbcDriver {

    private final String classPath;

    public JdbcDriver(String classPath) {
        this.classPath = classPath;
    }

    public FuncStackAnalyzer analyze(String className, String methodSubsignature) {
        setupSoot();
        RegionAnalyzer.initialize(
                DIRRegionAnalyzer.INSTANCE,
                DIRBranchRegionSpecialAnalyzer.INSTANCE,
                DIRBranchRegionAnalyzer.INSTANCE,
                DIRLoopRegionAnalyzer.INSTANCE,
                DIRSequentialRegionAnalyzer.INSTANCE);

        SootClass clazz = Scene.v().forceResolve(className, SootClass.BODIES);
        clazz.setApplicationClass();
        SootMethod method = clazz.getMethod(methodSubsignature);

        FuncStackAnalyzer fsa = new FuncStackAnalyzer(method);
        fsa.run();
        return fsa;
    }

    private void setupSoot() {
        G.reset();
        Options.v().set_soot_classpath(classPath);
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_no_bodies_for_excluded(true);
        Scene.v().loadBasicClasses();
    }
}
