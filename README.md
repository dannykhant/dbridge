# DBridge

A Java program-rewrite tool that transforms iterative (per-row) JDBC access into
set-oriented (batched) access. It is an implementation of the techniques
described in:

> Chavan, Guravannavar, Ramachandra, Sudarshan. *"DBridge: A Program Rewrite
> Tool for Set-Oriented Query Execution"*, ICDE 2011.

The architecture mirrors the reference project
[scirpy](https://github.com/lazyfatpandas/public/tree/main/scirpy/parsePythonIR/src)
(same pattern, repurposed for **Java + JDBC** instead of Python + HQL/SQL).

---

## Overview

Given a Java program that runs a parameterized JDBC query inside a loop:

```java
Connection con = DriverManager.getConnection(url);
PreparedStatement pstmt = con.prepare(
        "SELECT count(partkey) FROM part WHERE category = ?");
while (category != -1) {
    pstmt.setInt(1, category);
    ResultSet rs = pstmt.executeQuery();
    if (rs.next()) {
        partCount = rs.getInt(1);
        total += partCount;
    }
    category = getParent(category);
}
```

DBridge rewrites it so the query is executed once for all parameter bindings:

```java
DBridgeConnection con = DBridgeConnection.getConnection("dbr:" + url);
DBridgePreparedStatement pstmt = con.prepareStatement(
        "SELECT count(partkey) FROM part WHERE category = ?");
while (category != -1) {
    pstmt.setInt(1, category);
    pstmt.addBatch();
    category = getParent(category);
}
pstmt.executeBatch();
while (pstmt.getMoreResults()) {
    ResultSet rs = pstmt.getResultSet();
    if (rs.next()) {
        partCount = rs.getInt(1);
        total += partCount;
    }
}
```

---

## Pipeline

```mermaid
flowchart TD
    A[Source Java] -->|Soot| B[Jimple]
    B --> C[Region tree]
    C --> D["DIR (expression DAG)"]
    D -->|"DIR*RegionAnalyzers (fold detection)"| E["FoldNode + swallowed loops"]
    E -->|"TransDriver.applyAllTransRules"| F["BodyRewriter (split loop → batched JDBC)"]
    F --> G[Modified Jimple]
    G --> H[target file]
```

1. **Parsing / IR** — Soot parses Java bytecode into Jimple.
2. **Region tree** — a structured control-flow tree (`LoopRegion`,
   `BranchRegion`, `SequentialRegion`) is built over the Jimple body.
3. **DIR analysis** — per-region analyzers walk the region tree bottom-up and
   build a **DIR**, a `Map<VarNode, Node>` mapping each variable to its defining
   algebraic expression (a DAG).
4. **Fold detection** — `DIRLoopRegionAnalyzer` detects accumulator variables
   (variables present in their own read set) and, when the loop-splitting
   precondition holds (the variable's only cyclic dependency is on itself),
   replaces the loop with a `FoldNode`.
5. **Rule engine** — `TransDriver` applies pattern-matching `Rule` objects
   (simplification/fold) to refine the DAG.
6. **Rewrite** — `BodyRewriter` splits the loop into a parameter-binding loop,
   an `executeBatch()` call, and a result-consumption loop.
7. **Runtime** — `executeBatch()` materializes the bindings into a temporary
   table and rewrites the query to its set-oriented form.

The final output is the **optimized `.java` source** (readable Java emitted from
the modified Jimple).

---

## Package layout

```
dbridge/
├── Main.java                       CLI entry point
├── analysis/
│   ├── region/                     Region tree over Jimple (CFG structuring)
│   │   ├── regions/                ARegion, Region, SequentialRegion,
│   │   │                           BranchRegion, BranchRegionSpecial, LoopRegion
│   │   ├── api/                    RegionAnalysis, RegionAnalyzer (visitor API)
│   │   └── RegionGraphBuilder.java
│   └── jdbc/                       DBridge analysis (Java + JDBC)
│       ├── expr/                   DIR + OpType + expression DAG nodes
│       │   └── node/               Node, VarNode, FoldNode, IfNode, InvokeMethodNode, ...
│       ├── construct/              StmtDIRConstructionHandler (Jimple → node)
│       ├── analysis/               DIR*RegionAnalyzer implementations
│       ├── trans/                  Rule, InputTree, OutputTree, TransDriver
│       ├── util/                   VarResolver
│       ├── FuncStackAnalyzer.java
│       └── JdbcDriver.java         Orchestrator (port of EqSQLDriver)
├── rewrite/
│   ├── BodyRewriter.java           Loop splitting → batched JDBC
│   └── JavaWriter.java             Jimple → readable Java source emitter
├── runtime/                        Runtime JDBC wrapper library
│   ├── DBridgeConnection.java
│   ├── DBridgePreparedStatement.java  bind/addBatch/executeBatch/getMoreResults/
│   │                                  getResultSet + set-oriented query rewrite
│   ├── LoopContextTable.java       Ordered context table (order-sensitive ops)
│   └── Record.java
└── visitor/                        NodeVisitor, Visitable
```

---

## Key data structures

### Region tree (`dbridge.analysis.region`)

A structured representation of control flow, built from Soot's
`BriefBlockGraph`/`BriefUnitGraph`. Nodes: `Region` (basic block),
`SequentialRegion`, `BranchRegion`, `BranchRegionSpecial`, `LoopRegion`.

### DIR (`dbridge.analysis.jdbc.expr.DIR`)

A `Map<VarNode, Node>` mapping each program variable to the expression DAG that
defines its value. This is the central structure the transformation rules
operate on.

### Expression nodes (`dbridge.analysis.jdbc.expr.node.Node`)

Each node has an `OpType`, child nodes, an optional region, and the originating
Jimple statements. `readSet()` returns the set of variables read by a node.
Key nodes: `VarNode`, `RetVarNode`, `FoldNode` (loop aggregate), `IfNode`
(conditional), `InvokeMethodNode`, `BinaryOpNode`, `UnAlgNode`.

### Rule engine (`dbridge.analysis.jdbc.trans`)

A `Rule` matches an `InputTree` pattern (binding matched subnodes by id),
checks preconditions, and constructs the replacement from an `OutputTree`.
`TransDriver` applies simplification and fold rules iteratively.

---

## Region analyzers

`RegionAnalyzer.initialize(...)` registers one analyzer per region type:

| Analyzer | Purpose |
|---|---|
| `DIRRegionAnalyzer` | Basic block → DIR entries |
| `DIRSequentialRegionAnalyzer` | Merge two sub-DIRs (resolve variable refs) |
| `DIRBranchRegionAnalyzer` | Combine true/false branches into `IfNode` expressions |
| `DIRBranchRegionSpecialAnalyzer` | Single-branch conditional |
| `DIRLoopRegionAnalyzer` | Detect accumulators → `FoldNode` |

`DIRLoopRegionAnalyzer` is the core: it finds variables updated in the loop
body that read themselves (aggregation), identifies the loop induction
variable, and, if an accumulator has a cyclic dependency only on itself, emits
a `FoldNode` and records the "swallowed" loop.

---

## Runtime (set-oriented query rewrite)

`DBridgePreparedStatement.executeBatch()`:

1. Parses the SQL with JSqlParser.
2. Creates a temporary table `pb` and materializes all collected bindings.
3. Rewrites a scalar aggregate
   `SELECT <agg> FROM <t> WHERE <col> = ?` into the unnest form
   `SELECT <agg> FROM pb LEFT JOIN <t> ON pb.<col> = <t>.<col> GROUP BY pb.<col>`
   (the equivalent of the paper's `OUTER APPLY`/`LATERAL` rewrite).
4. Executes it once and exposes one single-row `ResultSet` per binding through
   `getMoreResults()`/`getResultSet()`.

Non-aggregate statements (e.g. `INSERT`) use the native JDBC batch.

---

## Building and running

Requires JDK 17 and Maven.

```bash
cd /path/to/dbridge
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

mvn test            # run all tests (7 tests)

# transform an example method
mvn -q compile test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP="target/classes:target/test-classes:$(cat /tmp/cp.txt)"
java -cp "$CP" dbridge.Main \
  "target/classes:target/test-classes" \
  dbridge.test.Example1 \
  "int getTotalPartCount(int)" \
  /tmp/out.java
```

This writes the transformed Java to `/tmp/out.java` and prints it to stdout.

---

## End-to-end example

`examples/run.sh` runs a complete, runnable demo end to end:

1. Compiles and runs the **original** application (`examples/PartCountApp.java`),
   which computes a category roll-up with one JDBC query per category.
2. Transforms its `computeTotal` method with DBridge.
3. Assembles, compiles and runs the **optimized** application
   (`PartCountAppOptimized.java`) against the DBridge runtime.
4. Compares the result (correctness) and query time (performance).

```bash
cd /path/to/dbridge
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

bash examples/run.sh          # default: 50000 categories
bash examples/run.sh 100000   # custom number of categories
```

Example output:

```
==> Categories: 50000
==> Building DBridge (skip tests)
==> Compiling original application
==> Running ORIGINAL application (iterative JDBC)
RESULT=150000
SETUP_MS=1007
QUERY_MS=225
==> Transforming computeTotal with DBridge
==> Assembling optimized application
==> Compiling optimized application
==> Running OPTIMIZED application (batched JDBC)
RESULT=150000
SETUP_MS=749
QUERY_MS=412

==> Comparison
Original result : 150000
Optimized result: 150000
CORRECTNESS: PASS (identical results)
Query time - original : 225 ms
Query time - optimized: 412 ms
```

The transformed method and the assembled optimized class are written to
`examples/build/` (`computeTotal.java` and `PartCountAppOptimized.java`).

---

## Tests

| Test | What it verifies |
|---|---|
| `JdbcDriverTest` | Fold detection (loop → `FoldNode`, 1 swallowed loop) |
| `BodyRewriterTest` | Transformed Jimple contains `addBatch`/`executeBatch`/`getMoreResults`/`getResultSet` |
| `JavaWriterTest` | Transformed body renders as readable Java (while loop, batch + result calls) |
| `DBridgeRuntimeTest` | Set-oriented rewrite correctness vs H2 (incl. LEFT JOIN zero-count), `mergeResults`, native INSERT batch |
| `IntegrationTest` | Original vs transformed produce identical results on H2 |

---

## Known limitations

- Query rewrite covers a scalar aggregate `SELECT … WHERE col = ?` and bulk
  `INSERT`; arbitrary SQL and multi-parameter `WHERE` predicates are not yet
  handled.
- Order-sensitive operations (paper Example 4) are supported by
  `LoopContextTable`/`mergeResults` but not covered by an end-to-end test.
- Nested loops and conditional blocks (Example 3) are the next extensions.
- Java output is produced by a lightweight Jimple→Java emitter (readable but
  uses Soot's temporary variable names). Soot's Dava decompiler — the paper's
  "Decompile" step — does not run on JDK 9+, so the custom emitter is used
  instead.
