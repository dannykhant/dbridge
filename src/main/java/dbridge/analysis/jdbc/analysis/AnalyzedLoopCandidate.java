package dbridge.analysis.jdbc.analysis;

import soot.Unit;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable rewrite input produced by loop-region analysis. */
public final class AnalyzedLoopCandidate {
    private final Unit head;
    private final List<Stmt> statements;
    private final List<Stmt> exits;

    public AnalyzedLoopCandidate(Unit head, List<Stmt> statements, List<Stmt> exits) {
        this.head = head;
        this.statements = Collections.unmodifiableList(new ArrayList<>(statements));
        this.exits = Collections.unmodifiableList(new ArrayList<>(exits));
    }

    public Unit getHead() { return head; }
    public List<Stmt> getStatements() { return statements; }
    public List<Stmt> getExits() { return exits; }
}
