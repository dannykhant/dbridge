package dbridge.analysis.jdbc;

import dbridge.analysis.jdbc.analysis.DIRBranchRegionAnalyzer;
import dbridge.analysis.jdbc.analysis.DIRBranchRegionSpecialAnalyzer;
import dbridge.analysis.jdbc.analysis.DIRLoopRegionAnalyzer;
import dbridge.analysis.jdbc.analysis.DIRRegionAnalyzer;
import dbridge.analysis.jdbc.analysis.DIRSequentialRegionAnalyzer;
import dbridge.analysis.region.api.RegionAnalyzer;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.SQLTranslatable;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;
import dbridge.analysis.jdbc.trans.TransDriver;
import dbridge.rewrite.BodyRewriter;
import soot.G;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CHATransformer;
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
        Scene.v().setEntryPoints(java.util.Collections.singletonList(method));
        Scene.v().loadNecessaryClasses();
        CHATransformer.v().transform();
        new FuncStackInfoBuilder(fsa).build();
        fsa.run();
        return fsa;
    }

    /** Applies the complete author-style pipeline at one driver boundary. */
    public boolean rewrite(FuncStackAnalyzer analyzer) throws QueryTranslationException {
        if (!analyzer.isSuccess() || analyzer.getExpr() == null) {
            return false;
        }
        Node expression = TransDriver.applyAllTransRules(analyzer.getExpr());
        String query = getQuery(expression);
        BodyRewriter rewriter = new BodyRewriter(query, expression.getRegion(),
                analyzer.getBody(), analyzer.getRetType(), expression.getLoopsSwallowed(),
                analyzer.getAnalyzedLoopCandidates());
        return rewriter.rewriteBody();
    }

    public String getQuery(Node expression) throws QueryTranslationException {
        if (!(expression instanceof SQLTranslatable)) {
            throw new QueryTranslationException(expression + " is not SQL translatable");
        }
        String query = ((SQLTranslatable) expression).toSQLQuery();
        return query.startsWith("from") ? "select * " + query : query;
    }

    private void setupSoot() {
        G.reset();
        Options.v().set_soot_classpath(classPath);
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_no_bodies_for_excluded(true);
        Scene.v().loadBasicClasses();
    }
}
