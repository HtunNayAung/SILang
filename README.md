# SIlang Compiler Front-End — Version 0.1

Lexer + Parser + AST for the SIlang programming language, implemented in Java 21.

## Project Structure

```
silang/
├── TokenType.java          — All token types (v0.1 + reserved futures)
├── Token.java              — Immutable token: type, lexeme, literal, line, col
├── LexerError.java         — Lexer error codes (L001–L004) + formatting
├── Lexer.java              — Single-pass scanner → List<Token>
├── Main.java               — CLI: Lexer → Parser → AST printer
│
├── ast/
│   ├── Expr.java           — Expression nodes (sealed hierarchy + Visitor)
│   ├── Stmt.java           — Statement nodes (sealed hierarchy + Visitor)
│   └── AstPrinter.java     — S-expression debug printer
│
└── parser/
    ├── ParseError.java     — Parse error codes (P001–P006) + formatting
    └── Parser.java         — Recursive-descent LL(1) parser → List<Stmt>

hello.si                    — Sample SIlang source file
build.sh                    — Compile and run script
```

## Requirements

Java 21 JDK (`javac` + `java` on PATH).

## Build & Run

```bash
chmod +x build.sh

# Compile
./build.sh

# Parse hello.si — print AST
./build.sh run

# Parse any file
./build.sh run myprogram.si

# Lex only (tokens, no parsing)
./build.sh tokens hello.si
```

## Manual Run

```bash
javac --release 21 -d out silang/*.java silang/ast/*.java silang/parser/*.java

# Parse a file → print AST
java -cp out silang.Main hello.si

# Parse inline source → print AST
java -cp out silang.Main --expr "var x = 5 + 3"

# Lex only → print tokens
java -cp out silang.Main --tokens hello.si

# Lex inline → print tokens
java -cp out silang.Main --tokens --expr "var x = 5"
```

## Example Output

**Input:**
```silang
var x = 5 + 3
out(x)
```

**AST output:**
```
============================================================
  SIlang AST — <inline>  (2 statement(s))
============================================================
  (var x (+ 5 3))
  (call out x)
============================================================
```

**More complex:**
```silang
var result = (5 + 3) * 10
out("Result: " + result)
```
```
  (var result (* (group (+ 5 3)) 10))
  (call out (+ "Result: " result))
```

## AST Node Types

| Node | Fields | Example |
|------|--------|---------|
| `Expr.Binary` | left, operator, right | `5 + 3` |
| `Expr.Unary` | operator, right | `-x` |
| `Expr.Literal` | value (Integer/Double/String/Boolean) | `42`, `"hi"` |
| `Expr.Variable` | name (Token) | `x` |
| `Expr.Grouping` | expression | `(5 + 3)` |
| `Expr.Call` | callee, paren, arguments | `out("hi")` |
| `Stmt.Var` | name (Token), initializer | `var x = 5` |
| `Stmt.Expression` | expression | `out("hi")` |

## Error Reporting

Parse errors use the same spec-format as lexer errors:

```
error[P006]: expected '=' after variable name in declaration but found '3'
  --> hello.si:1:9
   |
 1 | var price 3.14
   |         ^
```

| Code | Meaning |
|------|---------|
| P001 | Unexpected token |
| P002 | Expected expression |
| P003 | Missing `)` in grouping |
| P004 | Missing `)` in call |
| P005 | Too many arguments (> 255) |
| P006 | Missing `=` in var declaration |

## Visitor Pattern

Both `Expr` and `Stmt` expose a `Visitor<R>` interface.  Implement it to add
new compiler phases without modifying the AST node classes:

```java
class MyPhase implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    public Void visitBinary(Expr.Binary e) { ... }
    public Void visitLiteral(Expr.Literal e) { ... }
    // etc.
}
```

## Forward-Compatibility

The sealed class hierarchies make adding future nodes safe:

| Future feature | Node to add |
|----------------|-------------|
| `if` / `else` | `Stmt.If` |
| `while` | `Stmt.While` |
| `{ block }` | `Stmt.Block` |
| User functions | `Stmt.Function` |
| `return` | `Stmt.Return` |
| Assignment `x = v` | `Expr.Assign` |
| `&&` / `\|\|` | `Expr.Logical` |
| `obj.field` | `Expr.Get` |
| `obj.field = v` | `Expr.Set` |
| `this` | `Expr.This` |
