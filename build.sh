#!/usr/bin/env bash
# build.sh — Compile and run the SIlang v0.2 interpreter
set -euo pipefail

SRC_DIR="silang"
OUT_DIR="out"
MAIN_CLASS="silang.Main"
DEFAULT_FILE="hello.si"

if command -v javac &>/dev/null; then
    JAVAC=$(command -v javac); JAVA=$(command -v java)
else
    for candidate in /usr/lib/jvm/java-21-openjdk-amd64/bin/javac \
                     /usr/lib/jvm/java-17-openjdk-amd64/bin/javac; do
        if [[ -x "$candidate" ]]; then JAVAC="$candidate"; JAVA="${candidate%javac}java"; break; fi
    done
fi
[[ -z "${JAVAC:-}" ]] && { echo "ERROR: javac not found. Install JDK >= 17."; exit 1; }

compile() {
    # Always clean first — prevents stale .class files causing phantom errors
    rm -rf "$OUT_DIR"
    mkdir -p "$OUT_DIR"
    "$JAVAC" --release 21 -d "$OUT_DIR" \
        "$SRC_DIR"/*.java \
        "$SRC_DIR"/ast/*.java \
        "$SRC_DIR"/parser/*.java \
        "$SRC_DIR"/interpreter/*.java
    echo "Build OK → $OUT_DIR/"
}

ACTION="${1:-compile}"
case "$ACTION" in
  clean)   rm -rf "$OUT_DIR"; echo "Cleaned.";;
  run)     compile; "$JAVA" -cp "$OUT_DIR" "$MAIN_CLASS" "${2:-$DEFAULT_FILE}";;
  ast)     compile; "$JAVA" -cp "$OUT_DIR" "$MAIN_CLASS" --ast "${2:-$DEFAULT_FILE}";;
  tokens)  compile; "$JAVA" -cp "$OUT_DIR" "$MAIN_CLASS" --tokens "${2:-$DEFAULT_FILE}";;
  compile|*)
    compile
    echo "Run:    java -cp $OUT_DIR $MAIN_CLASS hello.si"
    echo "AST:    java -cp $OUT_DIR $MAIN_CLASS --ast hello.si"
    echo "Tokens: java -cp $OUT_DIR $MAIN_CLASS --tokens hello.si"
    echo "Inline: java -cp $OUT_DIR $MAIN_CLASS --expr 'out(\"hi\")'";;
esac
