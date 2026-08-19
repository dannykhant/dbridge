package dbridge.analysis.jdbc.trans;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.node.GenericNode;
import dbridge.analysis.jdbc.expr.node.Node;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransDriverTest {
    @Test
    void registersTemporaryRulesInLegacyOrder() {
        assertRuleNames(TransDriver.getSimplificationRules(), "RuleS1A", "RuleS1B", "RuleS2", "RuleS3", "RuleS4", "RuleS5", "RuleS6", "RuleS7A", "RuleS7B", "RuleS7C", "RuleS8A", "RuleS8B", "RuleS9");
        assertRuleNames(TransDriver.getFoldTransRules(), "RuleT1A", "RuleT1B", "RuleT1C", "RuleT1D", "RuleT2", "RuleT3A", "RuleT3B", "RuleT4", "RuleT5");
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

    private static void assertRuleNames(List<Rule> rules, String... names) {
        assertEquals(names.length, rules.size());
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], rules.get(i).getClass().getSimpleName());
        }
    }
}
