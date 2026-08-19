package dbridge.analysis.jdbc.construct;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.node.BinaryOpNode;
import dbridge.analysis.jdbc.expr.node.InvokeMethodNode;
import dbridge.analysis.jdbc.expr.node.Node;
import dbridge.analysis.jdbc.expr.node.RetVarNode;
import dbridge.analysis.jdbc.expr.node.StringConstNode;
import dbridge.analysis.jdbc.expr.node.ValueNode;
import dbridge.analysis.jdbc.expr.node.VarNode;
import soot.Local;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.CastExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps Jimple statements to DIR entries. Assignments produce a target variable
 * and a defining expression; void invocations (e.g. {@code pstmt.setInt(...)})
 * produce a side-effect node with no target.
 */
public final class StmtDIRConstructionHandler {

    private StmtDIRConstructionHandler() {
    }

    public static StmtInfo constructDagSS(Unit unit) {
        if (!(unit instanceof Stmt)) {
            return StmtInfo.nullInfo;
        }
        Stmt stmt = (Stmt) unit;

        if (stmt instanceof IdentityStmt) {
            return handleIdentity((IdentityStmt) stmt);
        } else if (stmt instanceof AssignStmt) {
            return handleAssign((AssignStmt) stmt);
        } else if (stmt instanceof InvokeStmt) {
            return handleInvoke((InvokeStmt) stmt);
        } else if (stmt instanceof ReturnStmt) {
            return handleReturn((ReturnStmt) stmt);
        } else if (stmt instanceof IfStmt) {
            return handleIf((IfStmt) stmt);
        }
        // IfStmt, ReturnVoidStmt, GotoStmt, NopStmt, etc.
        return StmtInfo.nullInfo;
    }

    private static StmtInfo handleIdentity(IdentityStmt stmt) {
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();
        VarNode target = left instanceof Local ? new VarNode((Local) left) : null;
        return new StmtInfo(target, new ValueNode(right));
    }

    private static StmtInfo handleAssign(AssignStmt stmt) {
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();
        VarNode target = left instanceof Local ? new VarNode((Local) left) : null;
        Node expr = buildValueNode(right);
        return new StmtInfo(target, expr);
    }

    private static StmtInfo handleInvoke(InvokeStmt stmt) {
        Node expr = buildInvokeNode(stmt.getInvokeExpr());
        return new StmtInfo(null, expr);
    }

    private static StmtInfo handleIf(IfStmt stmt) {
        Value condition = stmt.getCondition();
        if (condition instanceof ConditionExpr) {
            ConditionExpr ce = (ConditionExpr) condition;
            OpType op = invertCondition(ce.getSymbol());
            Node left = buildValueNode(ce.getOp1());
            Node right = buildValueNode(ce.getOp2());
            return new StmtInfo(VarNode.getACondVar(), new BinaryOpNode(op, left, right));
        }
        return StmtInfo.nullInfo;
    }

    /**
     * Jimple stores the loop/branch exit condition, which is the negation of the
     * source condition. Invert the symbol to recover the source condition.
     */
    private static OpType invertCondition(String symbol) {
        switch (symbol) {
            case "==":
                return OpType.NotEq;
            case "!=":
                return OpType.Eq;
            case ">":
                return OpType.Lt;
            case "<":
                return OpType.Gt;
            case ">=":
                return OpType.Lt;
            case "<=":
                return OpType.Gt;
            default:
                return OpType.Eq;
        }
    }

    private static StmtInfo handleReturn(ReturnStmt stmt) {
        Value op = stmt.getOp();
        RetVarNode retVar = RetVarNode.getARetVar();
        retVar.setOrigRetVarType(op.getType());
        return new StmtInfo(retVar, buildValueNode(op));
    }

    private static Node buildValueNode(Value v) {
        if (v instanceof Local) {
            return new VarNode((Local) v);
        }
        if (v instanceof InvokeExpr) {
            return buildInvokeNode((InvokeExpr) v);
        }
        if (v instanceof BinopExpr) {
            BinopExpr binop = (BinopExpr) v;
            return new BinaryOpNode(opTypeFor(binop), buildValueNode(binop.getOp1()), buildValueNode(binop.getOp2()));
        }
        if (v instanceof CastExpr) {
            return buildValueNode(((CastExpr) v).getOp());
        }
        if (v instanceof StringConstant) {
            return new StringConstNode(((StringConstant) v).value);
        }
        return new ValueNode(v);
    }

    private static Node buildInvokeNode(InvokeExpr invoke) {
        String methodName = invoke.getMethod().getName();
        List<Node> children = new ArrayList<>();
        if (invoke instanceof InstanceInvokeExpr) {
            children.add(buildValueNode(((InstanceInvokeExpr) invoke).getBase()));
        }
        for (Value arg : invoke.getArgs()) {
            children.add(buildValueNode(arg));
        }
        return new InvokeMethodNode(methodName, invoke.getMethod().getSignature(), children.toArray(new Node[0]));
    }

    private static OpType opTypeFor(BinopExpr binop) {
        switch (binop.getSymbol().trim()) {
            case "+":
                return OpType.ArithAdd;
            case "-":
                return OpType.ArithSub;
            case "%":
                return OpType.ArithMod;
            case "==":
                return OpType.Eq;
            case "!=":
                return OpType.NotEq;
            case ">":
                return OpType.Gt;
            case "<":
                return OpType.Lt;
            case "&&":
                return OpType.And;
            case "||":
                return OpType.Or;
            default:
                throw new IllegalArgumentException("Unsupported Jimple operator: " + binop.getSymbol());
        }
    }
}
