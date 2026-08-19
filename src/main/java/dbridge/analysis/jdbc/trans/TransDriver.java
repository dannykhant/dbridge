package dbridge.analysis.jdbc.trans;

import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.trans.fold.*;
import dbridge.analysis.jdbc.trans.simplify.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Iteratively applies the simplification and fold transformation rules to an
 * expression DAG. In DBridge the actual loop splitting is performed by the loop
 * region analyzer; this driver applies the (simplification/fold) rules that
 * refine the resulting DAG.
 */
public class TransDriver {

    private static final int MAX_TRANS_ITERS = 32;

    private static final List<Rule> simpliRules = getSimplificationRules();
    private static final List<Rule> foldRules = getFoldTransRules();

    public static Node applyAllTransRules(Node expr) {
        if (expr == null) {
            return null;
        }
        dbridge.analysis.region.regions.ARegion region = expr.getRegion();
        List<dbridge.analysis.region.regions.LoopRegion> loops =
                new ArrayList<>(expr.getLoopsSwallowed());
        for (int i = 0; i < MAX_TRANS_ITERS; i++) {
            String before = expr.toString();
            expr = applySimpliRules(expr);
            expr = applyFoldRules(expr);
            expr = applySimpliRules(expr);
            if (before.equals(expr.toString())) {
                break;
            }
        }
        if (region != null) {
            expr.setRegion(region);
        }
        for (dbridge.analysis.region.regions.LoopRegion loop : loops) {
            expr.addLoopSwallowed(loop);
        }
        return expr;
    }

    public static Node applySimpliRules(Node expr) {
        return applyTransRules(expr, simpliRules);
    }

    public static Node applyFoldRules(Node expr) {
        return applyTransRules(expr, foldRules);
    }

    private static Node applyTransRules(Node inNode, List<Rule> rules) {
        Node transNode = inNode;
        for (Rule rule : rules) {
            transNode = transNode.accept(rule);
        }
        return transNode;
    }

    public static List<Rule> getSimplificationRules() {
        List<Rule> rules = new ArrayList<>();
        rules.add(new RuleS1A());
        rules.add(new RuleS1B());
        rules.add(new RuleS2());
        rules.add(new RuleS3());
        rules.add(new RuleS4());
        rules.add(new RuleS5());
        rules.add(new RuleS6());
        rules.add(new RuleS7A());
        rules.add(new RuleS7B());
        rules.add(new RuleS7C());
        rules.add(new RuleS8A());
        rules.add(new RuleS8B());
        rules.add(new RuleS9());
        return rules;
    }

    public static List<Rule> getFoldTransRules() {
        List<Rule> rules = new ArrayList<>();
        rules.add(new RuleT1A());
        rules.add(new RuleT1B());
        rules.add(new RuleT1C());
        rules.add(new RuleT1D());
        rules.add(new RuleT2());
        rules.add(new RuleT3A());
        rules.add(new RuleT3B());
        rules.add(new RuleT4());
        rules.add(new RuleT5());
        rules.add(new RuleT6());
        return rules;
    }
}
