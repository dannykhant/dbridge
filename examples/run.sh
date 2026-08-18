#!/usr/bin/env bash
set -euo pipefail

# End-to-end DBridge demo: original iterative JDBC vs batched (rewritten).
# Exercises all five rewrite methods: statement reordering, loop splitting,
# query rewrite, conditional blocks, and order-sensitive operations.
# Requires JDK 8 and Maven.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

CATEGORIES="${1:-50000}"
BUILD_DIR="$ROOT/examples/build"
CP_FILE="$BUILD_DIR/cp.txt"
ORIGINAL_OUTPUT="$BUILD_DIR/original.txt"
OPTIMIZED_OUTPUT="$BUILD_DIR/optimized.txt"
TRANSFORM_OUTPUT="$BUILD_DIR/transform.txt"
TRANSFORM_SOURCE="$BUILD_DIR/transformed-PartCountApp.java"
OPTIMIZED_SOURCE="$BUILD_DIR/PartCountApp.java"
ORIGINAL_CLASSES="$BUILD_DIR/original-classes"
OPTIMIZED_CLASSES="$BUILD_DIR/optimized-classes"

prepare_build_directories() {
    mkdir -p "$BUILD_DIR"
    rm -rf "$ORIGINAL_CLASSES" "$OPTIMIZED_CLASSES"
    mkdir -p "$ORIGINAL_CLASSES" "$OPTIMIZED_CLASSES"
}

build_runtime() {
    echo "==> Building DBridge runtime (skip tests)"
    mvn -q -DskipTests package dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
    CP="$ROOT/target/classes:$(<"$CP_FILE")"
}

compile_original_application() {
    echo "==> Compiling original application"
    javac -source 1.8 -target 1.8 -cp "$CP" -d "$ORIGINAL_CLASSES" examples/PartCountApp.java
}

transform_compute_total() {
    echo "==> Transforming PartCountApp.computeTotal with DBridge"
    java -cp "$ORIGINAL_CLASSES:$CP" dbridge.Main \
        "$ORIGINAL_CLASSES:$CP" \
        dbridge.example.PartCountApp \
        "int computeTotal(int)" \
        "$TRANSFORM_SOURCE" examples/PartCountApp.java > "$TRANSFORM_OUTPUT"
}

generate_optimized_source() {
    echo "==> Using source-preserving transformed application source"
    cp "$TRANSFORM_SOURCE" "$OPTIMIZED_SOURCE"
}

compile_optimized_application() {
    echo "==> Compiling optimized application"
    javac -source 1.8 -target 1.8 -cp "$CP" -d "$OPTIMIZED_CLASSES" "$OPTIMIZED_SOURCE"
}

run_application() {
    local label="$1"
    local class_directory="$2"
    local output_file="$3"

    echo "==> Running $label"
    java -cp "$class_directory:$CP" dbridge.example.PartCountApp "$CATEGORIES" | tee "$output_file"
}

compare_results() {
    local original_result optimized_result
    local original_log_hash optimized_log_hash
    local original_query_time optimized_query_time

    echo
    echo "==> Comparison"
    original_result=$(grep RESULT "$ORIGINAL_OUTPUT" | cut -d= -f2)
    optimized_result=$(grep RESULT "$OPTIMIZED_OUTPUT" | cut -d= -f2)
    original_log_hash=$(grep LOG_HASH "$ORIGINAL_OUTPUT" | cut -d= -f2)
    optimized_log_hash=$(grep LOG_HASH "$OPTIMIZED_OUTPUT" | cut -d= -f2)
    original_query_time=$(grep QUERY_MS "$ORIGINAL_OUTPUT" | cut -d= -f2)
    optimized_query_time=$(grep QUERY_MS "$OPTIMIZED_OUTPUT" | cut -d= -f2)

    echo "Result - original : $original_result"
    echo "Result - optimized: $optimized_result"
    echo "Log hash - original : $original_log_hash"
    echo "Log hash - optimized: $optimized_log_hash"

    local checks_passed=1
    [ "$original_result" = "$optimized_result" ] || { echo "CORRECTNESS (result): FAIL"; checks_passed=0; }
    [ "$original_log_hash" = "$optimized_log_hash" ] || { echo "CORRECTNESS (order) : FAIL"; checks_passed=0; }
    [ "$checks_passed" = "1" ] && echo "CORRECTNESS: PASS (result and order match)"

    echo "Query time - original : ${original_query_time} ms"
    echo "Query time - optimized: ${optimized_query_time} ms"

    echo
    echo "Round-trips:"
    echo " - original  : ~N queries"
    echo " - optimized : 1 batch"
    echo "NOTE: H2 is in-memory, so a per-row indexed lookup is already ~microseconds;"
    echo "      the batched form wins on databases with network round-trip latency."
}

echo "==> Categories: $CATEGORIES"
prepare_build_directories
build_runtime
compile_original_application
transform_compute_total
generate_optimized_source
compile_optimized_application
run_application "ORIGINAL application (iterative JDBC)" "$ORIGINAL_CLASSES" "$ORIGINAL_OUTPUT"
run_application "OPTIMIZED application (batched JDBC)" "$OPTIMIZED_CLASSES" "$OPTIMIZED_OUTPUT"
compare_results
