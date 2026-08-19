package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import org.junit.jupiter.api.Test;

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
    void factoryRejectsUnboundVariables() {
        assertThrows(IllegalArgumentException.class,
                () -> NodeFactory.constructFromOpType(OpType.Var));
    }
}
