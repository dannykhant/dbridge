package dbridge.analysis.jdbc.trans;

import dbridge.analysis.jdbc.expr.node.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Iteratively applies the simplification and fold transformation rules to an
 * expression DAG. In DBridge the actual loop splitting is performed by the loop
 * region analyzer; this driver applies the (simplification/fold) rules that
 * refine the resulting DAG.
 */
public class TransDriver {

    private static final int NUM_TRANS_ITERS = 2;

    private static final List<Rule> simpliRules = getSimplificationRules();
    private static final List<Rule> foldRules = getFoldTransRules();

    public static Node applyAllTransRules(Node expr) {
        expr = applySimpliRules(expr);
        expr = applyFoldRules(expr);
        expr = applySimpliRules(expr);
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
        for (int i = 0; i < NUM_TRANS_ITERS; i++) {
            for (Rule rule : rules) {
                transNode = transNode.accept(rule);
            }
        }
        return transNode;
    }

    public static List<Rule> getSimplificationRules() {
        List<Rule> rules = new ArrayList<>();
        // Simplification rules (RuleS1..RuleS9) can be added here.
        return rules;
    }

    public static List<Rule> getFoldTransRules() {
        List<Rule> rules = new ArrayList<>();
        // Fold rules (RuleT1A..RuleT5) can be added here.
        return rules;
    }
}
