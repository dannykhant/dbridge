package dbridge.analysis.jdbc.trans;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.node.ClassRefNode;
import dbridge.analysis.jdbc.expr.node.ConstTableNode;
import dbridge.analysis.jdbc.expr.node.CountStarNode;
import dbridge.analysis.jdbc.expr.node.FieldRefNode;
import dbridge.analysis.jdbc.expr.node.GenericNode;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.ProjectNode;
import dbridge.analysis.jdbc.expr.node.ZeroNode;
import dbridge.analysis.jdbc.trans.fold.RuleT1C;
import dbridge.analysis.jdbc.trans.fold.RuleT4;
import dbridge.analysis.jdbc.trans.fold.RuleT5;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleOutputConstructionTest {
    @Test
    void t1cBuildsClassRefFromFieldPayload() {
        FieldRefNode field = new FieldRefNode("Order", "customer", "Customer");
        Node input = new GenericNode(OpType.Fold,
                new GenericNode(OpType.FuncExpr,
                        new GenericNode(OpType.InvokeMethod,
                                new GenericNode(OpType.PlaceholderVar),
                                new GenericNode(OpType.MethodInsert),
                                new GenericNode(OpType.FuncParams,
                                        new GenericNode(OpType.LazyFetch, field)))),
                new GenericNode(OpType.Bottom), new GenericNode(OpType.ClassRef));

        Node result = input.accept(new RuleT1C());
        ClassRefNode classRef = (ClassRefNode) result.getChild(0).getChild(0);
        assertEquals("Customer", classRef.getClassName());
    }

    @Test
    void t4BuildsTranslatableScratchLeaves() {
        Node input = fold(OpType.One, new GenericNode(OpType.ClassRef));
        Node result = input.accept(new RuleT4());
        ProjectNode outer = (ProjectNode) result;

        assertInstanceOf(ConstTableNode.class, outer.getChild(0).getChild(0));
        assertInstanceOf(CountStarNode.class, outer.getChild(1).getChild(0).getChild(1));
        assertInstanceOf(ZeroNode.class, outer.getChild(1).getChild(1));
    }

    @Test
    void t5BuildsCountStarNode() {
        Node input = new GenericNode(OpType.Fold,
                new GenericNode(OpType.FuncExpr,
                        new GenericNode(OpType.ArithAdd, new GenericNode(OpType.ClassRef), new GenericNode(OpType.One))),
                new GenericNode(OpType.Zero), new GenericNode(OpType.ClassRef));

        Node result = input.accept(new RuleT5());
        assertInstanceOf(CountStarNode.class, result.getChild(1));
    }

    private static Node fold(OpType accumulator, Node relation) {
        return new GenericNode(OpType.Fold,
                new GenericNode(OpType.FuncExpr, new GenericNode(accumulator)),
                new GenericNode(OpType.Zero), relation);
    }
}
