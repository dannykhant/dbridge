package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import org.junit.jupiter.api.Test;
import soot.IntType;
import soot.Local;
import soot.jimple.Jimple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlTranslationTest {
    @Test
    void translatesRelationProjectionAndPredicate() throws Exception {
        Node relation = new SelectNode(
                new CartesianProdNode(new ClassRefNode("Project")),
                new BinaryOpNode(OpType.Eq,
                        new FieldRefNode("Project", "id", "Long"), new ParamNode()));

        assertEquals("(select count(*) from project as p where p.id = ?)",
                new ProjectNode(relation, new CountStarNode()).toSQLQuery());
    }

    @Test
    void translatesCartesianProductFromFieldType() throws Exception {
        Node relation = new CartesianProdNode(
                new ClassRefNode("Project"),
                new FieldRefNode("Participant", "project", "Participant"));

        assertEquals("from project as p, participant as pt", ((SQLTranslatable) relation).toSQLQuery());
    }

    @Test
    void translatesScalarNodesAndBooleanOperators() throws Exception {
        Node expression = new BinaryOpNode(OpType.And,
                new BinaryOpNode(OpType.Gt, new OneNode(), new ZeroNode()),
                new BinaryOpNode(OpType.Eq, new ParamNode(), new OneNode()));

        assertEquals("1 > 0 AND ? = 1", ((SQLTranslatable) expression).toSQLQuery());
    }

    @Test
    void rejectsNonSqlChildren() {
        assertThrows(Exception.class, () -> new BinaryOpNode(OpType.Eq,
                new GenericNode(OpType.Var), new OneNode()).toSQLQuery());
    }

    @Test
    void foldTranslatesTheTransformedExpression() throws Exception {
        Node relation = new SelectNode(
                new CartesianProdNode(new ClassRefNode("Project")),
                new BinaryOpNode(OpType.Eq,
                        new FieldRefNode("Project", "id", "Long"), new ParamNode()));
        Node expression = new FoldNode(
                new ProjectNode(relation, new CountStarNode()), null, null);

        assertEquals("(select count(*) from project as p where p.id = ?)",
                ((SQLTranslatable) expression).toSQLQuery());
    }

    @Test
    void foldParameterizesAccumulatorAndKeepsThreeChildren() {
        Local local = Jimple.v().newLocal("total", IntType.v());
        VarNode accumulator = new VarNode(local);
        FoldNode fold = new FoldNode(
                new BinaryOpNode(OpType.ArithAdd, accumulator, new OneNode()),
                accumulator, accumulator);

        assertEquals(3, fold.getNumChildren());
        assertEquals(OpType.FuncExpr, fold.getChild(0).getOpType());
        assertEquals(OpType.PlaceholderVar, fold.getBodyExpr().getChild(0).getOpType());
        assertEquals(accumulator, fold.getInitValue());
        assertEquals(accumulator, fold.getLoopCollection());
    }

    @Test
    void factoryRejectsUnboundVariables() {
        assertThrows(IllegalArgumentException.class,
                () -> NodeFactory.constructFromOpType(OpType.Var));
    }
}
