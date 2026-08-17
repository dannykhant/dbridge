#!/usr/bin/env bash
set -euo pipefail

# End-to-end DBridge demo: original iterative JDBC vs batched (rewritten).
# Requires JDK 17 and Maven.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

CATEGORIES="${1:-50000}"
mkdir -p examples/output

echo "==> Categories: $CATEGORIES"
echo "==> Building DBridge (skip tests)"
mvn -q -DskipTests package dependency:build-classpath -Dmdep.outputFile=examples/output/cp.txt

CP="$ROOT/target/classes:$(cat examples/output/cp.txt)"
mkdir -p examples/build

echo "==> Compiling original application"
javac -cp "$CP" -d examples/build examples/PartCountApp.java

echo "==> Running ORIGINAL application (iterative JDBC)"
java -cp "examples/build:$CP" dbridge.example.PartCountApp "$CATEGORIES" | tee examples/output/original.txt

echo "==> Transforming computeTotal with DBridge"
java -cp "$CP" dbridge.Main \
  "examples/build" \
  dbridge.example.PartCountApp \
  "int computeTotal(int)" \
  examples/build/computeTotal.java >/dev/null

echo "==> Assembling optimized application"
{
  cat <<'HEADER'
package dbridge.example;

import dbridge.runtime.DBridgeConnection;
import dbridge.runtime.DBridgePreparedStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class PartCountAppOptimized {

    static final String URL = "jdbc:h2:mem:dbridge;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        int categories = args.length > 0 ? Integer.parseInt(args[0]) : 50000;
        long t0 = System.nanoTime();
        setup(categories);
        long t1 = System.nanoTime();
        long q0 = System.nanoTime();
        int total = computeTotal(categories - 1);
        long q1 = System.nanoTime();
        System.out.println("RESULT=" + total);
        System.out.println("SETUP_MS=" + (t1 - t0) / 1_000_000);
        System.out.println("QUERY_MS=" + (q1 - q0) / 1_000_000);
    }

    static void setup(int categories) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE part(partkey INT PRIMARY KEY, category INT)");
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO part VALUES (?, ?)")) {
                for (int cat = 0; cat < categories; cat++) {
                    for (int k = 0; k < 3; k++) {
                        ins.setInt(1, cat * 3 + k);
                        ins.setInt(2, cat);
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }
            st.execute("CREATE INDEX part_cat ON part(category)");
        }
    }

HEADER
  cat examples/build/computeTotal.java
  echo "}"
} > examples/build/PartCountAppOptimized.java

echo "==> Compiling optimized application"
javac -cp "$CP" -d examples/build examples/build/PartCountAppOptimized.java

echo "==> Running OPTIMIZED application (batched JDBC)"
java -cp "examples/build:$CP" dbridge.example.PartCountAppOptimized "$CATEGORIES" | tee examples/output/optimized.txt

echo
echo "==> Comparison"
ORIG=$(grep RESULT examples/output/original.txt | cut -d= -f2)
OPT=$(grep RESULT examples/output/optimized.txt | cut -d= -f2)
OQ=$(grep QUERY_MS examples/output/original.txt | cut -d= -f2)
NQ=$(grep QUERY_MS examples/output/optimized.txt | cut -d= -f2)

echo "Original result : $ORIG"
echo "Optimized result: $OPT"
if [ "$ORIG" = "$OPT" ]; then
  echo "CORRECTNESS: PASS (identical results)"
else
  echo "CORRECTNESS: FAIL"
  exit 1
fi
echo "Query time - original : ${OQ} ms"
echo "Query time - optimized: ${NQ} ms"
