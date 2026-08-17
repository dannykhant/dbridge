package dbridge.rewrite;

import soot.Body;
import soot.Local;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AddExpr;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.CastExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.LongConstant;
import soot.jimple.NewExpr;
import soot.jimple.NopStmt;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.GotoStmt;
import soot.jimple.Stmt;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.jimple.toolkits.annotation.logic.LoopFinder;
import soot.toolkits.graph.BriefUnitGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Best-effort Jimple-to-Java source renderer for the transformed body. Soot's
 * Dava decompiler does not run on JDK 9+; this produces readable Java for the
 * (regular) batched-JDBC shape emitted by {@link BodyRewriter}.
 */
public class JavaWriter {

    private JavaWriter() {
    }

    public static String toJava(Body body) {
        Renderer r = new Renderer(body);
        return r.render();
    }

    private static final class Renderer {
        private final Body body;
        private final List<Unit> units;
        private final Map<Unit, Integer> index = new HashMap<>();
        private final Map<Unit, Loop> headToLoop = new HashMap<>();
        private final StringBuilder out = new StringBuilder();
        private int indent = 0;

        Renderer(Body body) {
            this.body = body;
            this.units = new ArrayList<>(body.getUnits());
            for (int i = 0; i < units.size(); i++) {
                index.put(units.get(i), i);
            }
            BriefUnitGraph ug = new BriefUnitGraph(body);
            LoopFinder lf = new LoopFinder();
            for (Loop loop : lf.getLoops(ug)) {
                headToLoop.put(loop.getHead(), loop);
            }
        }

        String render() {
            out.append("    public static ").append(type(body.getMethod().getReturnType()))
                    .append(' ').append(body.getMethod().getName()).append('(');
            int p = 0;
            for (Local local : body.getLocals()) {
                if (isParameter(local)) {
                    if (p > 0) {
                        out.append(", ");
                    }
                    out.append(type(local.getType())).append(' ').append(local.getName());
                    p++;
                }
            }
            out.append(')');
            if (!body.getMethod().getExceptions().isEmpty()) {
                out.append(" throws ");
                int e = 0;
                for (soot.SootClass ex : body.getMethod().getExceptions()) {
                    if (e > 0) {
                        out.append(", ");
                    }
                    out.append(shortName(ex.getName()));
                    e++;
                }
            }
            out.append(" {\n");
            for (Local local : body.getLocals()) {
                if (!isParameter(local)) {
                    out.append("        ").append(type(local.getType())).append(' ')
                            .append(local.getName()).append(";\n");
                }
            }
            emitBlock(0, units.size());
            out.append("    }\n");
            return out.toString();
        }

        private boolean isParameter(Local local) {
            for (Unit u : units) {
                if (u instanceof IdentityStmt) {
                    IdentityStmt s = (IdentityStmt) u;
                    if (s.getLeftOp().equals(local) && s.getRightOp() instanceof ParameterRef) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void line(String s) {
            for (int i = 0; i < indent; i++) {
                out.append("    ");
            }
            out.append(s).append('\n');
        }

        private void emitBlock(int from, int to) {
            int i = from;
            while (i < to) {
                Unit u = units.get(i);
                Loop loop = headToLoop.get(u);
                if (loop != null) {
                    emitLoop(loop);
                    i = lastIndex(loop) + 1;
                    continue;
                }
                emitStmt(u, null, null);
                i++;
            }
        }

        private int lastIndex(Loop loop) {
            int last = -1;
            for (Stmt s : loop.getLoopStatements()) {
                Integer idx = index.get(s);
                if (idx != null) {
                    last = Math.max(last, idx);
                }
            }
            return Math.max(last, index.getOrDefault(loop.getHead(), -1));
        }

        private void emitLoop(Loop loop) {
            Set<Stmt> bodySet = new HashSet<>(loop.getLoopStatements());

            IfStmt exitIf = findExitIf(loop, bodySet);

            List<Stmt> body = new ArrayList<>(loop.getLoopStatements());
            body.removeIf(s -> s instanceof GotoStmt);
            body.removeIf(s -> s instanceof NopStmt);
            body.sort((a, b) -> index.getOrDefault(a, 0) - index.getOrDefault(b, 0));

            // Hoist any leading loop-invariant constant assignments out of the
            // loop so the exit condition can become the while condition.
            List<Stmt> hoisted = new ArrayList<>();
            for (Stmt s : body) {
                if (s == exitIf) {
                    break;
                }
                if (s instanceof AssignStmt && isConstant(((AssignStmt) s).getRightOp())) {
                    hoisted.add(s);
                } else {
                    break;
                }
            }

            boolean exitIsFirst = exitIf != null && body.indexOf(exitIf) == hoisted.size();
            if (exitIsFirst) {
                for (Stmt s : hoisted) {
                    emitStmt(s, loop, exitIf);
                }
                body.removeAll(hoisted);
                body.remove(exitIf);
                line("while (" + negate(renderExpr(exitIf.getCondition())) + ") {");
                indent++;
                for (Stmt s : body) {
                    emitStmt(s, loop, exitIf);
                }
                indent--;
                line("}");
            } else {
                line("while (true) {");
                indent++;
                for (Stmt s : body) {
                    if (s == exitIf) {
                        line("if (" + renderExpr(((IfStmt) s).getCondition()) + ") break;");
                    } else {
                        emitStmt(s, loop, exitIf);
                    }
                }
                indent--;
                line("}");
            }
        }

        private boolean isConstant(Value v) {
            if (v instanceof CastExpr) {
                return isConstant(((CastExpr) v).getOp());
            }
            return v instanceof IntConstant || v instanceof LongConstant || v instanceof StringConstant
                    || v instanceof NullConstant || v instanceof soot.jimple.FloatConstant
                    || v instanceof soot.jimple.DoubleConstant;
        }

        private boolean isBoolean(Value v) {
            return v.getType() instanceof soot.BooleanType;
        }

        private IfStmt findExitIf(Loop loop, Set<Stmt> bodySet) {
            for (Stmt s : loop.getLoopStatements()) {
                if (s instanceof IfStmt && !bodySet.contains(((IfStmt) s).getTarget())) {
                    return (IfStmt) s;
                }
            }
            for (Stmt s : loop.getLoopExits()) {
                if (s instanceof IfStmt) {
                    return (IfStmt) s;
                }
            }
            return null;
        }

        private void emitStmt(Unit u, Loop loop, IfStmt exitIf) {
            if (u instanceof IdentityStmt) {
                IdentityStmt s = (IdentityStmt) u;
                if (s.getRightOp() instanceof ParameterRef) {
                    return;
                }
                line(s.getLeftOp() + " = " + renderExpr(s.getRightOp()) + ";");
            } else if (u instanceof AssignStmt) {
                AssignStmt s = (AssignStmt) u;
                line(s.getLeftOp() + " = " + renderExpr(s.getRightOp()) + ";");
            } else if (u instanceof InvokeStmt) {
                line(renderExpr(((InvokeStmt) u).getInvokeExpr()) + ";");
            } else if (u instanceof ReturnStmt) {
                line("return " + renderExpr(((ReturnStmt) u).getOp()) + ";");
            } else if (u instanceof IfStmt) {
                IfStmt s = (IfStmt) u;
                Unit target = s.getTarget();
                if (loop != null && target == loop.getHead()) {
                    line("if (" + renderExpr(s.getCondition()) + ") continue;");
                } else if (exitIf != null && u == exitIf) {
                    line("if (" + renderExpr(s.getCondition()) + ") break;");
                } else {
                    int t = index.get(target);
                    line("if (" + renderExpr(s.getCondition()) + ") goto L" + t + ";");
                }
            } else if (u instanceof GotoStmt) {
                int t = index.get(((GotoStmt) u).getTarget());
                line("goto L" + t + ";");
            } else if (!(u instanceof NopStmt)) {
                line("// " + u);
            }
        }

        private String renderExpr(Value v) {
            if (v instanceof StringConstant) {
                return "\"" + ((StringConstant) v).value + "\"";
            }
            if (v instanceof IntConstant || v instanceof LongConstant || v instanceof NullConstant) {
                return v.toString();
            }
            if (v instanceof Local || v instanceof ParameterRef) {
                return v.toString();
            }
            if (v instanceof StaticInvokeExpr) {
                StaticInvokeExpr ie = (StaticInvokeExpr) v;
                return shortName(ie.getMethod().getDeclaringClass().getName()) + "." + ie.getMethod().getName()
                        + args(ie.getArgs());
            }
            if (v instanceof InstanceInvokeExpr) {
                InstanceInvokeExpr ie = (InstanceInvokeExpr) v;
                return renderExpr(ie.getBase()) + "." + ie.getMethod().getName() + args(ie.getArgs());
            }
            if (v instanceof AddExpr) {
                AddExpr a = (AddExpr) v;
                return renderExpr(a.getOp1()) + " + " + renderExpr(a.getOp2());
            }
            if (v instanceof ConditionExpr) {
                ConditionExpr ce = (ConditionExpr) v;
                Value op1 = ce.getOp1();
                Value op2 = ce.getOp2();
                String sym = ce.getSymbol();
                // Jimple represents boolean comparisons as `b == 0` / `b == 1`.
                if (isBoolean(op1) && op2 instanceof IntConstant) {
                    boolean val = ((IntConstant) op2).value != 0;
                    if (sym.contains("==")) {
                        return val ? renderExpr(op1) : "!" + renderExpr(op1);
                    }
                    if (sym.contains("!=")) {
                        return val ? "!" + renderExpr(op1) : renderExpr(op1);
                    }
                }
                return renderExpr(op1) + " " + sym + " " + renderExpr(op2);
            }
            if (v instanceof BinopExpr) {
                return renderExpr(((BinopExpr) v).getOp1()) + " " + ((BinopExpr) v).getSymbol() + " "
                        + renderExpr(((BinopExpr) v).getOp2());
            }
            if (v instanceof CastExpr) {
                return "((" + type(v.getType()) + ") " + renderExpr(((CastExpr) v).getOp()) + ")";
            }
            if (v instanceof NewExpr) {
                return "new " + type(v.getType()) + "()";
            }
            return v.toString();
        }

        private String args(List<Value> list) {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(renderExpr(list.get(i)));
            }
            return sb.append(')').toString();
        }

        private String negate(String cond) {
            String trimmed = cond.trim();
            if (trimmed.startsWith("!") && !trimmed.startsWith("!=")) {
                return trimmed.substring(1);
            }
            String c = cond.replace(" ", "");
            if (c.contains("==")) {
                return cond.replaceFirst("==", "!=");
            }
            if (c.contains("!=")) {
                return cond.replaceFirst("!=", "==");
            }
            if (c.contains(">=")) {
                return cond.replaceFirst(">=", "<");
            }
            if (c.contains("<=")) {
                return cond.replaceFirst("<=", ">");
            }
            if (c.contains(">")) {
                return cond.replaceFirst(">", "<=");
            }
            if (c.contains("<")) {
                return cond.replaceFirst("<", ">=");
            }
            return "!(" + cond + ")";
        }

        private String shortName(String className) {
            int dot = className.lastIndexOf('.');
            return dot >= 0 ? className.substring(dot + 1) : className;
        }

        private String type(Type t) {
            return t.toString().replace('$', '.');
        }
    }
}
