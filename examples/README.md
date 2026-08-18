# Part Count Examples

`PartCountApp.java` is the iterative JDBC program. `run.sh` invokes DBridge on
its `computeTotal(int)` method and writes the generated method to
`examples/build/computeTotal.java`. It then replaces that method in a copy of
the original source and compiles the generated application into a separate
class directory. No optimized source is compiled before the transformation.

The mapping is:

- **Statement reordering:** the generated `computeTotal` saves the
  current category in `tempCat`, updates `category`, and records the iteration
  after the guarded query work. This separates the loop-variable update from
  the query while retaining the original value.
- **Loop splitting:** the first loop collects parameter bindings and records
  context; the second result loop processes the completed batch.
- **Query rewrite:** categories use `addBatch()` followed by one
  `executeBatch()` call instead of one `executeQuery()` per category.
- **Guarded conditional flag and restoration:** the first loop stores
  `boolean f = isActive(category)` in each `Record`; the result loop checks that
  flag before restoring the active branch's count, total update, and `log()`.
- **`LoopContextTable` order preservation:** `ctx.addRecord(...)` captures each
  iteration in traversal order, and iterating `ctx` after `mergeResults` lets
  the order-sensitive `log()` calls observe the original descending order.

## Run

From the repository root:

```bash
bash examples/run.sh
```

An optional category count can be supplied, for example
`bash examples/run.sh 1000`. The script builds the runtime, compiles the
original before analysis, generates and compiles the transformed application
afterward, and runs both against the same in-memory H2 dataset.
Generated classes, Java output, dependency classpath, and captured `.txt`
results are written to `examples/build/`.

The files under `original-classes/` and `optimized-classes/` are compiled JVM
bytecode (`.class` files). VS Code may display them as readable Java through
its decompiler; the files themselves remain binary class files. The generated
Java source is stored separately as `computeTotal.java` and the optimized
application source copy.

Expected correctness checks:

- `CORRECTNESS: PASS (result and order match)` is printed.
- The `Result - original` and `Result - optimized` values match.
- The `Log hash - original` and `Log hash - optimized` values match, verifying
  order-sensitive output as well as the aggregate result.
