package dbridge.rewrite;

import dbridge.analysis.region.regions.LoopRegion;
import dbridge.analysis.region.regions.ARegion;
import dbridge.analysis.jdbc.analysis.AnalyzedLoopCandidate;
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
import soot.jimple.AddExpr;
import soot.jimple.AssignStmt;
import soot.jimple.ConditionExpr;
import soot.jimple.IfStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.Stmt;
import soot.jimple.SubExpr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rewrites a Soot method body to replace iterative JDBC access with batched
 * (set-oriented) access. Handles both the simple case (loop splitting + query
 * rewrite) and the conditional/order-sensitive case (guarded statements +
 * LoopContextTable), using Soot's {@link LoopFinder} for robust loop
 * identification.
 */
public class BodyRewriter {

    private static final Set<String> BIND_METHODS = new HashSet<>(Arrays.asList(
            "setInt", "setString", "setLong", "setDouble", "setObject", "bind"));

    private final Body body;
    private final List<LoopRegion> loopsSwallowed;
    private final String query;
    private final ARegion region;
    private final soot.Type varType;
    private final List<AnalyzedLoopCandidate> analyzedCandidates;

    public BodyRewriter(Body body, List<LoopRegion> loopsSwallowed) {
        this(null, null, body, null, loopsSwallowed, null);
    }

    public BodyRewriter(String query, ARegion region, Body body, soot.Type varType,
                        List<LoopRegion> loopsSwallowed) {
        this(query, region, body, varType, loopsSwallowed, null);
    }

    public BodyRewriter(String query, ARegion region, Body body, soot.Type varType,
                        List<LoopRegion> loopsSwallowed,
                        List<AnalyzedLoopCandidate> analyzedCandidates) {
        this.query = query;
        this.region = region;
        this.body = body;
        this.loopsSwallowed = loopsSwallowed;
        this.varType = varType;
        this.analyzedCandidates = analyzedCandidates == null
                ? candidatesFromRegions(loopsSwallowed) : analyzedCandidates;
    }

    private static List<AnalyzedLoopCandidate> candidatesFromRegions(List<LoopRegion> regions) {
        List<AnalyzedLoopCandidate> result = new ArrayList<>();
        if (regions == null) return result;
        for (LoopRegion region : regions) {
            // Loop exits: conditional branches whose target leaves the loop
            // body (the head may also contain post-loop statements such as a
            // return statement).
            List<Stmt> exits = new ArrayList<>();
            Set<Unit> bodyUnits = new HashSet<>(region.getSubRegions().get(1).getUnits());
            for (Unit unit : region.getUnits()) {
                if (unit instanceof IfStmt && !bodyUnits.contains(((IfStmt) unit).getTarget())) {
                    exits.add((Stmt) unit);
                }
            }
            Unit exitTarget = exits.isEmpty() ? null : ((IfStmt) exits.get(0)).getTarget();
            // The guarded statements (head + body) drive the rewrite, but the
            // exit target and any other post-loop statements are not part of
            // the loop.
            List<Stmt> statements = new ArrayList<>();
            for (Unit unit : region.getUnits()) {
                if (unit instanceof Stmt && unit != exitTarget) {
                    statements.add((Stmt) unit);
                }
            }
            result.add(new AnalyzedLoopCandidate(region.getSubRegions().get(0).firstStmt(), statements, exits));
        }
        return result;
    }

    public boolean rewriteBody() {
        for (AnalyzedLoopCandidate candidate : analyzedCandidates) {
            Parts parts = classify(candidate);
            if (parts != null && validate(candidate, parts)) {
                reorderStatements(parts);
                rewriteJdbcSetup();
                transformLoop(candidate, parts);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // JDBC setup routing
    // ------------------------------------------------------------------

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
                Arrays.asList(RefType.v("java.lang.String")), RefType.v("dbridge.runtime.DBridgeConnection"), true);
        assign.setRightOp(Jimple.v().newStaticInvokeExpr(ref, Arrays.asList(newUrl)));
    }

    private void rewritePrepareStatement(AssignStmt assign, InstanceInvokeExpr invoke) {
        Local ps = (Local) assign.getLeftOp();
        ps.setType(RefType.v("dbridge.runtime.DBridgePreparedStatement"));

        SootMethodRef ref = Scene.v().makeMethodRef(
                resolve("dbridge.runtime.DBridgeConnection"), "prepareStatement",
                Arrays.asList(RefType.v("java.lang.String")), RefType.v("dbridge.runtime.DBridgePreparedStatement"), false);
        List<Value> args = invoke.getArgs();
        if (query != null && args.size() == 1) {
            args = Arrays.<Value>asList(StringConstant.v(query));
        }
        assign.setRightOp(Jimple.v().newVirtualInvokeExpr((Local) invoke.getBase(), ref, args));
    }

    // ------------------------------------------------------------------
    // Loop classification
    // ------------------------------------------------------------------

    private static final class Parts {
        IfStmt headIf;
        Unit exitTarget;
        Unit headConst;
        Unit condAssign;
        IfStmt outerIf;
        Unit binding;
        Unit queryExec;
        Unit nextStmt;
        IfStmt innerIf;
        Unit getIntStmt;
        Unit accumulate;
        List<InvokeStmt> orderSensitive = new ArrayList<>();
        Unit loopVarUpdate;
        Local pstmt;
        Local rs;
        Local nextResult;
        Local resultVal;
        String resultGetter;
        Local agg;
        Local loopVar;
        Local flag;
        boolean conditional;
    }

    private Parts classify(AnalyzedLoopCandidate candidate) {
        List<Stmt> stmts = new ArrayList<>(candidate.getStatements());
        stmts.sort((a, b) -> indexOf(a) - indexOf(b));

        Parts p = new Parts();
        for (Stmt s : candidate.getExits()) {
            if (s instanceof IfStmt) {
                p.headIf = (IfStmt) s;
                break;
            }
        }
        if (p.headIf == null) {
            return null;
        }
        p.exitTarget = p.headIf.getTarget();

        boolean pastHead = true;
        for (Stmt s : stmts) {
            if (s == p.headIf) {
                pastHead = true;
                continue;
            }
            if (!pastHead) {
                if (s instanceof AssignStmt) {
                    p.headConst = s;
                }
                continue;
            }
            if (s instanceof IfStmt) {
                IfStmt ifs = (IfStmt) s;
                if (p.condAssign != null && usesCondition(ifs, (Local) ((AssignStmt) p.condAssign).getLeftOp())) {
                    p.outerIf = ifs;
                } else if (p.nextStmt != null && usesResult(ifs, (Local) ((AssignStmt) p.nextStmt).getLeftOp())) {
                    p.innerIf = ifs;
                }
            } else if (s instanceof InvokeStmt) {
                InvokeExpr ie = ((InvokeStmt) s).getInvokeExpr();
                if (BIND_METHODS.contains(ie.getMethod().getName())) {
                    p.binding = s;
                } else {
                    p.orderSensitive.add((InvokeStmt) s);
                }
            } else if (s instanceof AssignStmt) {
                AssignStmt as = (AssignStmt) s;
                Value r = as.getRightOp();
                if (r instanceof InstanceInvokeExpr) {
                    String m = ((InstanceInvokeExpr) r).getMethod().getName();
                    if (m.equals("executeQuery")) {
                        p.queryExec = s;
                    } else if (m.equals("next")) {
                        p.nextStmt = s;
                    } else if (m.equals("getInt") || m.equals("getLong") || m.equals("getString")) {
                        p.getIntStmt = s;
                        p.resultGetter = m;
                    }
                } else if (r instanceof StaticInvokeExpr) {
                    if (((AssignStmt) s).getLeftOp().getType() instanceof BooleanType) {
                        p.condAssign = s;
                    } else {
                        p.loopVarUpdate = s;
                    }
                } else if (r instanceof AddExpr) {
                    p.accumulate = s;
                } else if (r instanceof SubExpr) {
                    p.loopVarUpdate = s;
                }
            }
        }

        if (p.innerIf == null && p.nextStmt != null) {
            Local nextLocal = (Local) ((AssignStmt) p.nextStmt).getLeftOp();
            for (Stmt s : stmts) {
                if (s instanceof IfStmt && usesResult((IfStmt) s, nextLocal)) {
                    p.innerIf = (IfStmt) s;
                    break;
                }
            }
        }

        if (p.queryExec == null || p.nextStmt == null || p.getIntStmt == null || p.accumulate == null
                || p.loopVarUpdate == null || p.binding == null) {
            return null;
        }

        p.pstmt = (Local) ((InstanceInvokeExpr) ((InvokeStmt) p.binding).getInvokeExpr()).getBase();
        p.rs = (Local) ((AssignStmt) p.queryExec).getLeftOp();
        p.nextResult = (Local) ((AssignStmt) p.nextStmt).getLeftOp();
        p.resultVal = (Local) ((AssignStmt) p.getIntStmt).getLeftOp();
        p.agg = (Local) ((AssignStmt) p.accumulate).getLeftOp();
        p.loopVar = (Local) ((AssignStmt) p.loopVarUpdate).getLeftOp();
        p.flag = p.condAssign != null ? (Local) ((AssignStmt) p.condAssign).getLeftOp() : null;

        p.conditional = p.condAssign != null || !p.orderSensitive.isEmpty();
        if (!p.orderSensitive.isEmpty() && p.flag == null) {
            return null;
        }
        return p;
    }

    private boolean validate(AnalyzedLoopCandidate candidate, Parts p) {
        Set<Unit> recognized = new HashSet<>(Arrays.asList(
                p.headIf, p.binding, p.queryExec, p.nextStmt, p.innerIf,
                p.getIntStmt, p.accumulate, p.loopVarUpdate));
        if (p.outerIf != null) recognized.add(p.outerIf);
        if (p.condAssign != null) recognized.add(p.condAssign);
        if (p.headConst != null) recognized.add(p.headConst);
        recognized.addAll(p.orderSensitive);
        for (InvokeStmt stmt : p.orderSensitive) {
            if (!isReplayable(stmt, p)) return false;
        }
        for (Stmt stmt : candidate.getStatements()) {
            if (stmt == p.exitTarget || stmt instanceof soot.jimple.ReturnStmt) continue;
            if (recognized.contains(stmt) || isLoopBookkeeping(stmt)) continue;
            return false;
        }
        return true;
    }

    private boolean isReplayable(InvokeStmt stmt, Parts p) {
        InvokeExpr invoke = stmt.getInvokeExpr();
        for (Value arg : invoke.getArgs()) {
            if (!arg.equals(p.loopVar) && !arg.equals(p.resultVal)
                    && !(arg instanceof soot.jimple.Constant)) return false;
        }
        return true;
    }

    /** Move only independent binding/update statements; dependent code stays put. */
    private void reorderStatements(Parts p) {
        if (p.binding == null || p.loopVarUpdate == null) return;
        if (body.getUnits().getPredOf(p.binding) == p.loopVarUpdate
                && !usesLocal(p.binding, (Local) ((AssignStmt) p.loopVarUpdate).getLeftOp())) {
            body.getUnits().remove(p.binding);
            body.getUnits().insertBefore(p.binding, p.loopVarUpdate);
        }
    }

    private boolean usesLocal(Unit unit, Local local) {
        for (soot.ValueBox box : unit.getUseBoxes()) {
            if (box.getValue().equals(local)) return true;
        }
        return false;
    }

    private boolean isLoopBookkeeping(Stmt stmt) {
        if (stmt instanceof soot.jimple.GotoStmt || stmt instanceof soot.jimple.NopStmt) return true;
        if (!(stmt instanceof AssignStmt)) return false;
        Value right = ((AssignStmt) stmt).getRightOp();
        if (right instanceof soot.jimple.CastExpr) {
            right = ((soot.jimple.CastExpr) right).getOp();
        }
        return right instanceof StaticInvokeExpr || right instanceof AddExpr
                || right instanceof SubExpr || right instanceof soot.jimple.Constant;
    }

    private boolean usesResult(IfStmt ifs, Local result) {
        Value condition = ifs.getCondition();
        return condition instanceof ConditionExpr
                && (((ConditionExpr) condition).getOp1().equals(result)
                || ((ConditionExpr) condition).getOp2().equals(result));
    }

    private boolean usesCondition(IfStmt ifs, Local condLocal) {
        Value c = ifs.getCondition();
        if (c instanceof ConditionExpr) {
            ConditionExpr ce = (ConditionExpr) c;
            return ce.getOp1().equals(condLocal) || ce.getOp2().equals(condLocal);
        }
        return false;
    }

    private int indexOf(Unit u) {
        int i = 0;
        for (Unit x : body.getUnits()) {
            if (x == u) {
                return i;
            }
            i++;
        }
        return Integer.MAX_VALUE;
    }

    // ------------------------------------------------------------------
    // Transformation dispatch
    // ------------------------------------------------------------------

    private void transformLoop(AnalyzedLoopCandidate candidate, Parts p) {
        if (p.conditional) {
            transformConditionalLoop(candidate, p);
        } else {
            transformSimpleLoop(candidate, p);
        }
    }

    // ------------------------------------------------------------------
    // Simple path: loop splitting + query rewrite
    // ------------------------------------------------------------------

    private void transformSimpleLoop(AnalyzedLoopCandidate candidate, Parts p) {
        SootClass dbps = resolve("dbridge.runtime.DBridgePreparedStatement");

        // addBatch after each binding
        InvokeStmt binding = (InvokeStmt) p.binding;
        InstanceInvokeExpr oldInvoke = (InstanceInvokeExpr) binding.getInvokeExpr();
        SootMethodRef setRef = Scene.v().makeMethodRef(dbps, oldInvoke.getMethod().getName(),
                oldInvoke.getMethod().getParameterTypes(), VoidType.v(), false);
        Local base = (Local) oldInvoke.getBase();
        binding.setInvokeExpr(Jimple.v().newVirtualInvokeExpr(base, setRef, oldInvoke.getArgs()));
        SootMethodRef addBatchRef = Scene.v().makeMethodRef(dbps, "addBatch", Arrays.asList(), VoidType.v(), false);
        body.getUnits().insertAfter(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(base, addBatchRef, new ArrayList<Value>())), binding);

        for (Unit unit : new Unit[]{p.queryExec, p.nextStmt, p.innerIf, p.getIntStmt, p.accumulate}) {
            if (unit != null) {
                body.getUnits().remove(unit);
            }
        }

        List<Unit> newUnits = buildBatchAndResultLoop(p.pstmt, p.rs, p.nextResult, p.resultVal,
                p.agg, p.resultGetter, p.exitTarget);
        for (Unit newUnit : newUnits) {
            body.getUnits().insertBefore(newUnit, p.exitTarget);
        }
        ((IfStmt) newUnits.get(4)).setTarget(p.exitTarget);
    }

    private List<Unit> buildBatchAndResultLoop(Local pstmt, Local rs, Local nextResult,
                                               Local resultVal, Local agg, String resultGetter,
                                               Unit exitTarget) {
        SootClass dbps = resolve("dbridge.runtime.DBridgePreparedStatement");
        SootClass rsClass = resolve("java.sql.ResultSet");

        SootMethodRef executeBatchRef = Scene.v().makeMethodRef(dbps, "executeBatch",
                Arrays.asList(), VoidType.v(), false);
        SootMethodRef getResultSetRef = Scene.v().makeMethodRef(dbps, "getResultSet",
                Arrays.asList(), RefType.v("java.sql.ResultSet"), false);
        SootMethodRef rsNextRef = Scene.v().makeMethodRef(rsClass, "next",
                Arrays.asList(), BooleanType.v(), false);
        soot.Type resultType = resultVal.getType();
        SootMethodRef rsGetRef = Scene.v().makeMethodRef(rsClass, resultGetter,
                Arrays.asList(IntType.v()), resultType, false);

        List<Unit> units = new ArrayList<>();
        units.add(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(pstmt, executeBatchRef, new ArrayList<Value>())));
        units.add(Jimple.v().newAssignStmt(rs,
                Jimple.v().newVirtualInvokeExpr(pstmt, getResultSetRef, new ArrayList<Value>())));
        units.add(Jimple.v().newIfStmt(Jimple.v().newEqExpr(rs, soot.jimple.NullConstant.v()), exitTarget));
        Unit rsNext = Jimple.v().newAssignStmt(nextResult,
                Jimple.v().newInterfaceInvokeExpr(rs, rsNextRef, new ArrayList<Value>()));
        units.add(rsNext);
        units.add(Jimple.v().newIfStmt(Jimple.v().newEqExpr(nextResult, IntConstant.v(0)), exitTarget));
        units.add(Jimple.v().newAssignStmt(resultVal,
                Jimple.v().newInterfaceInvokeExpr(rs, rsGetRef, Arrays.asList(IntConstant.v(1)))));
        units.add(Jimple.v().newAssignStmt(agg, Jimple.v().newAddExpr(agg, resultVal)));
        units.add(Jimple.v().newGotoStmt(rsNext));
        return units;
    }

    // ------------------------------------------------------------------
    // Conditional + order-sensitive path (the paper's Example 3 -> 4)
    // ------------------------------------------------------------------

    private void transformConditionalLoop(AnalyzedLoopCandidate candidate, Parts p) {
        SootClass dbps = resolve("dbridge.runtime.DBridgePreparedStatement");
        SootClass lct = resolve("dbridge.runtime.LoopContextTable");

        // 1. ctx = new LoopContextTable() before the loop.
        Local ctx = Jimple.v().newLocal("ctx", RefType.v("dbridge.runtime.LoopContextTable"));
        body.getLocals().add(ctx);
        SootMethodRef lctInit = Scene.v().makeMethodRef(lct, "<init>", Arrays.asList(), VoidType.v(), false);
        Unit newLct = Jimple.v().newAssignStmt(ctx,
                Jimple.v().newNewExpr(RefType.v("dbridge.runtime.LoopContextTable")));
        Unit callInit = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(ctx, lctInit));
        Unit loopHead = candidate.getHead();
        Unit beforeLoop = body.getUnits().getPredOf(loopHead);
        if (beforeLoop == null) {
            body.getUnits().insertBefore(newLct, loopHead);
            body.getUnits().insertBefore(callInit, loopHead);
        } else {
            body.getUnits().insertAfter(newLct, beforeLoop);
            body.getUnits().insertAfter(callInit, newLct);
        }

        // 2. tempCat = loopVar before the loop-var update.
        Local tempCat = Jimple.v().newLocal("tempCat", IntType.v());
        body.getLocals().add(tempCat);
        Unit tempAssign = Jimple.v().newAssignStmt(tempCat, p.loopVar);
        body.getUnits().insertBefore(tempAssign, p.loopVarUpdate);

        // 3. addBatch after the binding (guarded block).
        InvokeStmt binding = (InvokeStmt) p.binding;
        InstanceInvokeExpr oldInvoke = (InstanceInvokeExpr) binding.getInvokeExpr();
        SootMethodRef setRef = Scene.v().makeMethodRef(dbps, oldInvoke.getMethod().getName(),
                oldInvoke.getMethod().getParameterTypes(), VoidType.v(), false);
        Local base = (Local) oldInvoke.getBase();
        binding.setInvokeExpr(Jimple.v().newVirtualInvokeExpr(base, setRef, oldInvoke.getArgs()));
        SootMethodRef addBatchRef = Scene.v().makeMethodRef(dbps, "addBatch", Arrays.asList(), VoidType.v(), false);
        body.getUnits().insertAfter(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(base, addBatchRef, new ArrayList<Value>())), binding);

        // Keep inactive records in the batch. The result loop filters them while
        // retaining the original iteration order for order-sensitive operations.
        if (p.outerIf != null) {
            body.getUnits().remove(p.outerIf);
        }

        // 5. ctx.addRecord(tempCat, flag, tempCat) after the loop-var update.
        SootMethodRef addRecordRef = Scene.v().makeMethodRef(lct, "addRecord",
                Arrays.asList(IntType.v(), BooleanType.v(), IntType.v()), VoidType.v(), false);
        Unit addRecord = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(ctx, addRecordRef,
                Arrays.asList(tempCat, p.flag, tempCat)));
        body.getUnits().insertAfter(addRecord, p.loopVarUpdate);

        // 6. Remove the query/result/order-sensitive statements from the loop.
        for (Unit unit : new Unit[]{p.queryExec, p.nextStmt, p.innerIf, p.getIntStmt, p.accumulate}) {
            if (unit != null) {
                body.getUnits().remove(unit);
            }
        }
        for (InvokeStmt orderSensitive : p.orderSensitive) {
            body.getUnits().remove(orderSensitive);
        }

        // 7. executeBatch + mergeResults + result loop before the loop exit.
        List<Unit> newUnits = buildCtxResultLoop(ctx, p, p.exitTarget);
        for (Unit newUnit : newUnits) {
            body.getUnits().insertBefore(newUnit, p.exitTarget);
        }
        ((IfStmt) newUnits.get(4)).setTarget(p.exitTarget);
    }

    private List<Unit> buildCtxResultLoop(Local ctx, Parts p, Unit exitTarget) {
        SootClass dbps = resolve("dbridge.runtime.DBridgePreparedStatement");
        SootClass lct = resolve("dbridge.runtime.LoopContextTable");
        SootClass rec = resolve("dbridge.runtime.Record");
        SootClass iter = resolve("java.util.Iterator");

        SootMethodRef executeBatchRef = Scene.v().makeMethodRef(dbps, "executeBatch",
                Arrays.asList(), VoidType.v(), false);
        SootMethodRef mergeRef = Scene.v().makeMethodRef(lct, "mergeResults",
                Arrays.asList(RefType.v("dbridge.runtime.DBridgePreparedStatement")), VoidType.v(), false);
        SootMethodRef iteratorRef = Scene.v().makeMethodRef(lct, "iterator",
                Arrays.asList(), RefType.v("java.util.Iterator"), false);
        SootMethodRef hasNextRef = Scene.v().makeMethodRef(iter, "hasNext",
                Arrays.asList(), BooleanType.v(), false);
        SootMethodRef nextRef = Scene.v().makeMethodRef(iter, "next",
                Arrays.asList(), RefType.v("java.lang.Object"), false);
        String getter = p.resultGetter.equals("getLong") ? "getLong"
                : p.resultGetter.equals("getString") ? "getString" : "getInt";
        soot.Type resultType = p.resultVal.getType();
        SootMethodRef getResultRef = Scene.v().makeMethodRef(rec, getter,
                Arrays.asList(IntType.v()), resultType, false);
        SootMethodRef getBoolRef = Scene.v().makeMethodRef(rec, "getBoolean",
                Arrays.asList(IntType.v()), BooleanType.v(), false);
        SootMethodRef getIntRef = Scene.v().makeMethodRef(rec, "getInt",
                Arrays.asList(IntType.v()), IntType.v(), false);

        Local it = Jimple.v().newLocal("it", RefType.v("java.util.Iterator"));
        Local nextObject = Jimple.v().newLocal("nextObject", RefType.v("java.lang.Object"));
        Local record = Jimple.v().newLocal("record", RefType.v("dbridge.runtime.Record"));
        Local has = Jimple.v().newLocal("has", BooleanType.v());
        body.getLocals().add(it);
        body.getLocals().add(nextObject);
        body.getLocals().add(record);
        body.getLocals().add(has);

        List<Unit> units = new ArrayList<>();

        // executeBatch()
        units.add(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(p.pstmt, executeBatchRef, new ArrayList<Value>())));
        // ctx.mergeResults(pstmt)
        units.add(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(ctx, mergeRef, Arrays.asList(p.pstmt))));
        // it = ctx.iterator()
        units.add(Jimple.v().newAssignStmt(it,
                Jimple.v().newVirtualInvokeExpr(ctx, iteratorRef, new ArrayList<Value>())));

        // loop head: has = it.hasNext()
        Unit hasNext = Jimple.v().newAssignStmt(has,
                Jimple.v().newInterfaceInvokeExpr(it, hasNextRef, new ArrayList<Value>()));
        units.add(hasNext);

        // if has == 0 goto exit
        IfStmt ifNoMore = Jimple.v().newIfStmt(Jimple.v().newEqExpr(has, IntConstant.v(0)), exitTarget);
        units.add(ifNoMore);

        // nextObject = it.next(); record = (Record) nextObject
        Value next = Jimple.v().newInterfaceInvokeExpr(it, nextRef, new ArrayList<Value>());
        units.add(Jimple.v().newAssignStmt(nextObject, next));
        units.add(Jimple.v().newAssignStmt(record,
                Jimple.v().newCastExpr(nextObject, RefType.v("dbridge.runtime.Record"))));

        // flag = record.getBoolean(1); if flag == 0 goto loop head
        units.add(Jimple.v().newAssignStmt(p.flag,
                Jimple.v().newVirtualInvokeExpr(record, getBoolRef, Arrays.asList(IntConstant.v(1)))));
        IfStmt ifInactive = Jimple.v().newIfStmt(Jimple.v().newEqExpr(p.flag, IntConstant.v(0)), hasNext);
        units.add(ifInactive);

        // loopVar = record.getInt(2); resultVal = record.getInt(3)
        units.add(Jimple.v().newAssignStmt(p.loopVar,
                Jimple.v().newVirtualInvokeExpr(record, getIntRef, Arrays.asList(IntConstant.v(2)))));
        units.add(Jimple.v().newAssignStmt(p.resultVal,
                Jimple.v().newVirtualInvokeExpr(record, getResultRef, Arrays.asList(IntConstant.v(3)))));

        // accumulate: agg = agg + resultVal
        units.add(Jimple.v().newAssignStmt(p.agg, Jimple.v().newAddExpr(p.agg, p.resultVal)));

        // Replay every retained side effect in source order.
        for (InvokeStmt orderSensitive : p.orderSensitive) {
            units.add(rewriteOrderSensitive(orderSensitive, p, record));
        }

        // goto loop head
        units.add(Jimple.v().newGotoStmt(hasNext));

        return units;
    }

    private Unit rewriteOrderSensitive(InvokeStmt orderStmt, Parts p, Local record) {
        InvokeExpr ie = orderStmt.getInvokeExpr();
        List<Value> args = new ArrayList<>();
        for (Value arg : ie.getArgs()) {
            if (arg.equals(p.loopVar)) {
                args.add(p.loopVar);
            } else if (arg.equals(p.resultVal)) {
                args.add(p.resultVal);
            } else {
                args.add(arg);
            }
        }
        if (ie instanceof StaticInvokeExpr) {
            return Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(ie.getMethodRef(), args));
        }
        return Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(
                (Local) ((InstanceInvokeExpr) ie).getBase(), ie.getMethodRef(), args));
    }

    private SootClass resolve(String className) {
        return Scene.v().forceResolve(className, SootClass.BODIES);
    }
}
