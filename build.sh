#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# build.sh — Compile and optionally run the SIlang v0.1 front-end
#
# Usage:
#   ./build.sh                  — compile only
#   ./build.sh run              — compile and parse hello.si
#   ./build.sh run <file>       — compile and parse a specific file
#   ./build.sh tokens <file>    — compile and lex-only a specific file
#   ./build.sh clean            — remove compiled output
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SRC_DIR="silang"
OUT_DIR="out"
MAIN_CLASS="silang.Main"
DEFAULT_FILE="hello.si"

# Locate javac
if command -v javac &>/dev/null; then
    JAVAC=$(command -v javac)
    JAVA=$(command -v java)
else
    for candidate in \
        /usr/lib/jvm/java-21-openjdk-amd64/bin/javac \
        /usr/lib/jvm/java-17-openjdk-amd64/bin/javac; do
        if [[ -x "$candidate" ]]; then
            JAVAC="$candidate"; JAVA="${candidate%javac}java"; break
        fi
    done
fi

if [[ -z "${JAVAC:-}" ]]; then
    echo "ERROR: javac not found. Install JDK >= 17."
    exit 1
fi

compile() {
    mkdir -p "$OUT_DIR"
    "$JAVAC" --release 21 -d "$OUT_DIR" \
        "$SRC_DIR"/*.java \
        "$SRC_DIR"/ast/*.java \
        "$SRC_DIR"/parser/*.java
    echo "Build OK → $OUT_DIR/"
}

ACTION="${1:-compile}"

case "$ACTION" in
  clean)
    rm -rf "$OUT_DIR"; echo "Cleaned.";;

  run)
    TARGET="${2:-$DEFAULT_FILE}"
    compile
    "$JAVA" -cp "$OUT_DIR" "$MAIN_CLASS" "$TARGET";;

  tokens)
    TARGET="${2:-$DEFAULT_FILE}"
    compile
    "$JAVA" -cp "$OUT_DIR" "$MAIN_CLASS" --tokens "$TARGET";;

  compile|*)
    compile
    echo "Usage:  java -cp $OUT_DIR $MAIN_CLASS <file.si>"
    echo "        java -cp $OUT_DIR $MAIN_CLASS --expr 'var x = 5'"
    echo "        java -cp $OUT_DIR $MAIN_CLASS --tokens hello.si";;
esac
