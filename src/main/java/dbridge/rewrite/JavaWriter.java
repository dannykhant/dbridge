package dbridge.rewrite;

import soot.Body;
import soot.Local;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.CastExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.GotoStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.LongConstant;
import soot.jimple.NewExpr;
import soot.jimple.NopStmt;
import soot.jimple.NullConstant;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;
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

/** Minimal Java 8 source writer for the regular transformed method shape. */
public final class JavaWriter {
    private JavaWriter() { }

    public static String toJava(Body body) {
        return new Renderer(body).render();
    }

    private static final class Renderer {
        private final Body body;
        private final List<Unit> units = new ArrayList<>();
        private final Map<Unit, Integer> index = new HashMap<>();
        private final Map<Unit, Loop> loops = new HashMap<>();
        private final StringBuilder out = new StringBuilder();
        private int indent;

        Renderer(Body body) {
            this.body = body;
            for (Unit unit : body.getUnits()) units.add(unit);
            for (int i = 0; i < units.size(); i++) index.put(units.get(i), i);
            for (Loop loop : new LoopFinder().getLoops(new BriefUnitGraph(body))) {
                loops.put(loop.getHead(), loop);
            }
        }

        String render() {
            out.append("    ").append(visibility()).append(" ")
                    .append(type(body.getMethod().getReturnType())).append(' ')
                    .append(body.getMethod().getName()).append('(');
            int parameter = 0;
            for (Local local : body.getLocals()) {
                if (isParameter(local)) {
                    if (parameter++ != 0) out.append(", ");
                    out.append(type(local.getType())).append(' ').append(local.getName());
                }
            }
            out.append(')');
            if (!body.getMethod().getExceptions().isEmpty()) {
                out.append(" throws ");
                for (int i = 0; i < body.getMethod().getExceptions().size(); i++) {
                    if (i != 0) out.append(", ");
                    out.append(shortName(body.getMethod().getExceptions().get(i).getName()));
                }
            }
            out.append(" {\n");
            for (Local local : body.getLocals()) {
                if (!isParameter(local)) line(type(local.getType()) + " " + local.getName() + ";");
            }
            emit(0, units.size());
            out.append("    }\n");
            return out.toString();
        }

        private String visibility() {
            return body.getMethod().isPublic() ? "public static" : "static";
        }

        private boolean isParameter(Local local) {
            for (Unit unit : units) if (unit instanceof IdentityStmt
                    && ((IdentityStmt) unit).getLeftOp().equals(local)
                    && ((IdentityStmt) unit).getRightOp() instanceof ParameterRef) return true;
            return false;
        }

        private void line(String value) {
            for (int i = 0; i < indent + 2; i++) out.append("    ");
            out.append(value).append('\n');
        }

        private void emit(int from, int to) {
            for (int i = from; i < to;) {
                Loop loop = loops.get(units.get(i));
                if (loop != null) { emitLoop(loop); i = last(loop) + 1; }
                else { emitStmt(units.get(i), null, null); i++; }
            }
        }

        private int last(Loop loop) {
            int result = index.get(loop.getHead());
            for (Stmt stmt : loop.getLoopStatements()) result = Math.max(result, index.get(stmt));
            return result;
        }

        private void emitLoop(Loop loop) {
            Set<Stmt> members = new HashSet<>(loop.getLoopStatements());
            IfStmt exit = null;
            for (Stmt stmt : loop.getLoopStatements()) {
                if (stmt instanceof IfStmt && !members.contains(((IfStmt) stmt).getTarget())) {
                    exit = (IfStmt) stmt; break;
                }
            }
            List<Stmt> statements = new ArrayList<>(loop.getLoopStatements());
            statements.removeIf(s -> s instanceof GotoStmt || s instanceof NopStmt);
            statements.sort((a, b) -> index.get(a) - index.get(b));
            line("while (true) {"); indent++;
            for (Stmt stmt : statements) {
                if (stmt == exit) line("if (" + expr(exit.getCondition()) + ") break;");
                else emitStmt(stmt, loop, exit);
            }
            indent--; line("}");
        }

        private void emitStmt(Unit unit, Loop loop, IfStmt exit) {
            if (unit instanceof IdentityStmt) {
                IdentityStmt stmt = (IdentityStmt) unit;
                if (!(stmt.getRightOp() instanceof ParameterRef)) line(stmt.getLeftOp() + " = " + expr(stmt.getRightOp()) + ";");
            } else if (unit instanceof AssignStmt) {
                AssignStmt stmt = (AssignStmt) unit; line(stmt.getLeftOp() + " = " + expr(stmt.getRightOp()) + ";");
            } else if (unit instanceof InvokeStmt) {
                InvokeExpr invoke = ((InvokeStmt) unit).getInvokeExpr();
                if (!"<init>".equals(invoke.getMethod().getName())) line(expr(invoke) + ";");
            } else if (unit instanceof ReturnStmt) line("return " + expr(((ReturnStmt) unit).getOp()) + ";");
            else if (unit instanceof IfStmt) {
                IfStmt stmt = (IfStmt) unit;
                if (loop != null && stmt.getTarget() == loop.getHead()) line("if (" + expr(stmt.getCondition()) + ") continue;");
                else if (exit != null && unit == exit) line("if (" + expr(stmt.getCondition()) + ") break;");
                else line("if (" + expr(stmt.getCondition()) + ") goto L" + index.get(stmt.getTarget()) + ";");
            } else if (unit instanceof GotoStmt) line("goto L" + index.get(((GotoStmt) unit).getTarget()) + ";");
            else if (!(unit instanceof NopStmt)) line("/* " + unit + " */");
        }

        private String expr(Value value) {
            if (value instanceof StringConstant) return "\"" + ((StringConstant) value).value + "\"";
            if (value instanceof IntConstant || value instanceof LongConstant || value instanceof NullConstant) return value.toString();
            if (value instanceof Local || value instanceof ParameterRef) return value.toString();
            if (value instanceof StaticInvokeExpr) {
                StaticInvokeExpr invoke = (StaticInvokeExpr) value;
                return invoke.getMethod().getDeclaringClass().getName().replace('$', '.') + "." + invoke.getMethod().getName() + args(invoke.getArgs());
            }
            if (value instanceof InstanceInvokeExpr) {
                InstanceInvokeExpr invoke = (InstanceInvokeExpr) value;
                return expr(invoke.getBase()) + "." + invoke.getMethod().getName() + args(invoke.getArgs());
            }
            if (value instanceof CastExpr) return "((" + type(value.getType()) + ") " + expr(((CastExpr) value).getOp()) + ")";
            if (value instanceof NewExpr) return "new " + type(value.getType()) + "()";
            if (value instanceof ConditionExpr || value instanceof BinopExpr) {
                BinopExpr binary = (BinopExpr) value;
                if (value instanceof ConditionExpr && binary.getOp1().getType() instanceof soot.BooleanType
                        && binary.getOp2() instanceof IntConstant) {
                    boolean one = ((IntConstant) binary.getOp2()).value != 0;
                    if (binary.getSymbol().contains("==")) return one ? expr(binary.getOp1()) : "!" + expr(binary.getOp1());
                    if (binary.getSymbol().contains("!=")) return one ? "!" + expr(binary.getOp1()) : expr(binary.getOp1());
                }
                return expr(binary.getOp1()) + " " + binary.getSymbol() + " " + expr(binary.getOp2());
            }
            return value.toString();
        }

        private String args(List<Value> values) {
            StringBuilder result = new StringBuilder("(");
            for (int i = 0; i < values.size(); i++) result.append(i == 0 ? "" : ", ").append(expr(values.get(i)));
            return result.append(')').toString();
        }

        private String type(Type type) { return type.toString().replace('$', '.'); }
        private String shortName(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? name : name.substring(dot + 1); }
    }
}
