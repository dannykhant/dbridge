package dbridge.rewrite;

import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.region.regions.LoopRegion;
import soot.Body;
import soot.BooleanType;
import soot.IntType;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethodRef;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.jimple.AssignStmt;
import soot.jimple.AddExpr;
import soot.jimple.IfStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.StringConstant;
import soot.jimple.StaticInvokeExpr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rewrites a Soot method body to replace iterative JDBC access with batched
 * (set-oriented) access: splits each swallowed loop into a parameter-binding
 * loop, an {@code executeBatch()} call, and a result-consumption loop.
 */
public class BodyRewriter {

    private static final Set<String> BIND_METHODS = new HashSet<>(Arrays.asList(
            "setInt", "setString", "setLong", "setDouble", "setObject", "bind"));

    private final Body body;
    private final List<LoopRegion> loopsSwallowed;

    public BodyRewriter(Body body, List<LoopRegion> loopsSwallowed) {
        this.body = body;
        this.loopsSwallowed = loopsSwallowed;
    }

    public void rewriteBody() {
        rewriteJdbcSetup();
        for (LoopRegion loop : loopsSwallowed) {
            splitLoop(loop);
        }
    }

    /**
     * Route the JDBC setup through the DBridge runtime: replace
     * {@code DriverManager.getConnection} with {@code DBridgeConnection.getConnection}
     * and retype the prepared statement to {@code DBridgePreparedStatement}.
     */
    private void rewriteJdbcSetup() {
        for (Unit unit : body.getUnits()) {
            if (!(unit instanceof AssignStmt)) {
                continue;
            }
            AssignStmt assign = (AssignStmt) unit;
            Value right = assign.getRightOp();
            if (right instanceof StaticInvokeExpr) {
                StaticInvokeExpr invoke = (StaticInvokeExpr) right;
                if (invoke.getMethod().getName().equals("getConnection")
                        && invoke.getMethod().getDeclaringClass().getName().equals("java.sql.DriverManager")) {
                    rewriteGetConnection(assign, invoke);
                }
            } else if (right instanceof InstanceInvokeExpr) {
                InstanceInvokeExpr invoke = (InstanceInvokeExpr) right;
                if (invoke.getMethod().getName().equals("prepareStatement")) {
                    rewritePrepareStatement(assign, invoke);
                }
            }
        }
    }

    private void rewriteGetConnection(AssignStmt assign, StaticInvokeExpr invoke) {
        Value url = invoke.getArg(0);
        String urlStr = url instanceof StringConstant ? ((StringConstant) url).value : url.toString();
        Value newUrl = StringConstant.v("dbr:" + urlStr);

        Local conn = (Local) assign.getLeftOp();
        conn.setType(RefType.v("dbridge.runtime.DBridgeConnection"));

        SootMethodRef ref = Scene.v().makeMethodRef(
                resolve("dbridge.runtime.DBridgeConnection"), "getConnection",
                List.of(RefType.v("java.lang.String")), RefType.v("dbridge.runtime.DBridgeConnection"), true);
        assign.setRightOp(Jimple.v().newStaticInvokeExpr(ref, List.of(newUrl)));
    }

    private void rewritePrepareStatement(AssignStmt assign, InstanceInvokeExpr invoke) {
        Local ps = (Local) assign.getLeftOp();
        ps.setType(RefType.v("dbridge.runtime.DBridgePreparedStatement"));

        SootMethodRef ref = Scene.v().makeMethodRef(
                resolve("dbridge.runtime.DBridgeConnection"), "prepareStatement",
                List.of(RefType.v("java.lang.String")), RefType.v("dbridge.runtime.DBridgePreparedStatement"), false);
        assign.setRightOp(Jimple.v().newVirtualInvokeExpr((Local) invoke.getBase(), ref, invoke.getArgs()));
    }

    private void splitLoop(LoopRegion loop) {
        ARegion headRegion = loop.getSubRegions().get(0);
        ARegion bodyRegion = loop.getSubRegions().get(1);

        IfStmt loopCond = null;
        for (Unit unit : headRegion.getUnits()) {
            if (unit instanceof IfStmt) {
                loopCond = (IfStmt) unit;
                break;
            }
        }
        if (loopCond == null) {
            return;
        }
        // In Soot's Jimple the loop head condition is at the bottom (do-while
        // form) and branches back to the body. The loop exit is the fall-through
        // unit following the condition.
        Unit exitTarget = body.getUnits().getSuccOf(loopCond);

        List<Unit> bindingStmts = new ArrayList<>();
        Unit queryExec = null;
        Unit nextStmt = null;
        Unit ifNext = null;
        Unit getIntStmt = null;
        Unit accumulate = null;

        for (Unit unit : bodyRegion.getUnits()) {
            if (unit instanceof InvokeStmt) {
                InvokeExpr invoke = ((InvokeStmt) unit).getInvokeExpr();
                if (BIND_METHODS.contains(invoke.getMethod().getName())) {
                    bindingStmts.add(unit);
                }
            } else if (unit instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) unit;
                Value right = assign.getRightOp();
                if (right instanceof InvokeExpr) {
                    String method = ((InvokeExpr) right).getMethod().getName();
                    if (method.equals("executeQuery")) {
                        queryExec = unit;
                    } else if (method.equals("next")) {
                        nextStmt = unit;
                    } else if (method.equals("getInt") || method.equals("getLong") || method.equals("getString")) {
                        getIntStmt = unit;
                    }
                } else if (right instanceof AddExpr) {
                    accumulate = unit;
                }
            } else if (unit instanceof IfStmt) {
                ifNext = unit;
            }
        }

        if (queryExec == null || nextStmt == null || getIntStmt == null || accumulate == null) {
            return;
        }

        Local pstmt = (Local) ((InstanceInvokeExpr) ((AssignStmt) queryExec).getRightOp()).getBase();
        Local rs = (Local) ((AssignStmt) queryExec).getLeftOp();
        Local nextResult = (Local) ((AssignStmt) nextStmt).getLeftOp();
        Local resultVal = (Local) ((AssignStmt) getIntStmt).getLeftOp();
        Local agg = (Local) ((AssignStmt) accumulate).getLeftOp();

        // Insert addBatch after each binding statement.
        SootClass dbps = resolve("dbridge.runtime.DBridgePreparedStatement");
        for (Unit binding : bindingStmts) {
            InvokeStmt invokeStmt = (InvokeStmt) binding;
            InstanceInvokeExpr oldInvoke = (InstanceInvokeExpr) invokeStmt.getInvokeExpr();
            SootMethodRef setRef = Scene.v().makeMethodRef(dbps, oldInvoke.getMethod().getName(),
                    oldInvoke.getMethod().getParameterTypes(), VoidType.v(), false);
            Local base = (Local) oldInvoke.getBase();
            invokeStmt.setInvokeExpr(Jimple.v().newVirtualInvokeExpr(base, setRef, oldInvoke.getArgs()));

            SootMethodRef addBatchRef = Scene.v().makeMethodRef(dbps, "addBatch",
                    List.of(), VoidType.v(), false);
            body.getUnits().insertAfter(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(base, addBatchRef, new ArrayList<Value>())), binding);
        }

        // Remove result-consumption statements from the loop body.
        for (Unit unit : new Unit[]{queryExec, nextStmt, ifNext, getIntStmt, accumulate}) {
            if (unit != null) {
                body.getUnits().remove(unit);
            }
        }

        // Build the batch + result loop and insert before the loop exit.
        List<Unit> newUnits = buildBatchAndResultLoop(pstmt, rs, nextResult, resultVal, agg, exitTarget);
        for (Unit newUnit : newUnits) {
            body.getUnits().insertBefore(newUnit, exitTarget);
        }
        // insertBefore redirects branches that target the anchor, so re-assert
        // the exit target of the "no more results" branch.
        ((IfStmt) newUnits.get(2)).setTarget(exitTarget);
    }

    private List<Unit> buildBatchAndResultLoop(Local pstmt, Local rs, Local nextResult,
                                               Local resultVal, Local agg, Unit exitTarget) {
        SootClass dbps = resolve("dbridge.runtime.DBridgePreparedStatement");
        SootClass rsClass = resolve("java.sql.ResultSet");

        SootMethodRef executeBatchRef = Scene.v().makeMethodRef(dbps, "executeBatch",
                List.of(), VoidType.v(), false);
        SootMethodRef getMoreResultsRef = Scene.v().makeMethodRef(dbps, "getMoreResults",
                List.of(), BooleanType.v(), false);
        SootMethodRef getResultSetRef = Scene.v().makeMethodRef(dbps, "getResultSet",
                List.of(), RefType.v("java.sql.ResultSet"), false);
        SootMethodRef rsNextRef = Scene.v().makeMethodRef(rsClass, "next",
                List.of(), BooleanType.v(), false);
        SootMethodRef rsGetIntRef = Scene.v().makeMethodRef(rsClass, "getInt",
                List.of(IntType.v()), IntType.v(), false);

        List<Unit> units = new ArrayList<>();

        Unit executeBatch = Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(pstmt, executeBatchRef, new ArrayList<Value>()));
        units.add(executeBatch);

        Unit getMoreResults = Jimple.v().newAssignStmt(nextResult,
                Jimple.v().newVirtualInvokeExpr(pstmt, getMoreResultsRef, new ArrayList<Value>()));
        units.add(getMoreResults);

        IfStmt ifNoMore = Jimple.v().newIfStmt(Jimple.v().newEqExpr(nextResult, IntConstant.v(0)), exitTarget);
        units.add(ifNoMore);

        Unit getResultSet = Jimple.v().newAssignStmt(rs,
                Jimple.v().newVirtualInvokeExpr(pstmt, getResultSetRef, new ArrayList<Value>()));
        units.add(getResultSet);

        Unit rsNext = Jimple.v().newAssignStmt(nextResult,
                Jimple.v().newInterfaceInvokeExpr(rs, rsNextRef, new ArrayList<Value>()));
        units.add(rsNext);

        IfStmt ifNextEmpty = Jimple.v().newIfStmt(Jimple.v().newEqExpr(nextResult, IntConstant.v(0)), getMoreResults);
        units.add(ifNextEmpty);

        Unit getInt = Jimple.v().newAssignStmt(resultVal,
                Jimple.v().newInterfaceInvokeExpr(rs, rsGetIntRef, List.of(IntConstant.v(1))));
        units.add(getInt);

        Unit accumulate = Jimple.v().newAssignStmt(agg, Jimple.v().newAddExpr(agg, resultVal));
        units.add(accumulate);

        units.add(Jimple.v().newGotoStmt(getMoreResults));

        return units;
    }

    private SootClass resolve(String className) {
        return Scene.v().forceResolve(className, SootClass.BODIES);
    }
}
