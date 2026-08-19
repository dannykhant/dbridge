package dbridge.analysis.jdbc.trans;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.node.GenericNode;
import dbridge.analysis.jdbc.expr.node.BinaryOpNode;
import dbridge.analysis.jdbc.expr.node.FoldNode;
import dbridge.analysis.jdbc.expr.node.IfNode;
import dbridge.analysis.jdbc.expr.node.JdbcSetQueryNode;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.OneNode;
import dbridge.analysis.jdbc.expr.node.PlaceholderVarNode;
import dbridge.analysis.jdbc.expr.node.VarNode;
import dbridge.analysis.jdbc.expr.node.StringConstNode;
import org.junit.jupiter.api.Test;
import soot.IntType;
import soot.Local;
import soot.jimple.Jimple;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransDriverTest {
    @Test
    void registersTemporaryRulesInLegacyOrder() {
        assertRuleNames(TransDriver.getSimplificationRules(), "RuleS1A", "RuleS1B", "RuleS2", "RuleS3", "RuleS4", "RuleS5", "RuleS6", "RuleS7A", "RuleS7B", "RuleS7C", "RuleS8A", "RuleS8B", "RuleS9");
        assertRuleNames(TransDriver.getFoldTransRules(), "RuleT1A", "RuleT1B", "RuleT1C", "RuleT1D", "RuleT2", "RuleT3A", "RuleT3B", "RuleT4", "RuleT5", "RuleT6");
    }

    @Test
    void appliesStructuralRuleToGenericNodes() {
        Node input = new GenericNode(OpType.CartesianProd,
                new GenericNode(OpType.CartesianProd, new GenericNode(OpType.Var)),
                new GenericNode(OpType.Var));

        Node result = input.accept(TransDriver.getSimplificationRules().get(7));

        assertEquals(OpType.CartesianProd, result.getOpType());
        assertEquals(2, result.getNumChildren());
        assertEquals(OpType.Var, result.getChild(0).getOpType());
    }

    @Test
    void pushesConditionalFoldPredicateIntoCollection() {
        Local totalLocal = Jimple.v().newLocal("total", IntType.v());
        Local categoryLocal = Jimple.v().newLocal("category", IntType.v());
        VarNode total = new VarNode(totalLocal);
        VarNode category = new VarNode(categoryLocal);
        Node condition = new GenericNode(OpType.Var);
        Node body = new IfNode(condition,
                new BinaryOpNode(OpType.ArithAdd, total, new OneNode()), total);

        Node result = TransDriver.applyFoldRules(new FoldNode(body, total, category));

        assertEquals(OpType.Fold, result.getOpType());
        assertEquals(OpType.FuncExpr, result.getChild(0).getOpType());
        assertEquals(OpType.ArithAdd, result.getChild(0).getChild(0).getOpType());
        assertEquals(OpType.Select, result.getChild(2).getOpType());
        assertEquals(OpType.PlaceholderVar,
                result.getChild(0).getChild(0).getChild(0).getOpType());
    }

    @Test
    void rewritesJdbcFoldAtAnalysisTime() throws Exception {
        Local totalLocal = Jimple.v().newLocal("total", IntType.v());
        Local categoryLocal = Jimple.v().newLocal("category", IntType.v());
        VarNode total = new VarNode(totalLocal);
        VarNode category = new VarNode(categoryLocal);
        FoldNode fold = new FoldNode(new StringConstNode(
                "SELECT count(partkey) FROM part WHERE category = ?"), total, category);

        Node result = TransDriver.applyFoldRules(fold);

        assertEquals(OpType.Select, result.getOpType());
        assertEquals("/* dbridge-prebuilt */ SELECT count(partkey), pb.category AS category, "
                        + "pb.batch_ordinal FROM pb LEFT JOIN part ON pb.category = part.category "
                        + "GROUP BY pb.batch_ordinal, pb.category",
                ((JdbcSetQueryNode) result).toSQLQuery());
    }

    private static void assertRuleNames(List<Rule> rules, String... names) {
        assertEquals(names.length, rules.size());
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], rules.get(i).getClass().getSimpleName());
        }
    }
}
