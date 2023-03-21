# SIlang Interpreter — Version 0.1

Full front-end + interpreter for the SIlang programming language, in Java 21.

## Project Structure

```
silang/
├── TokenType.java              — All token types
├── Token.java                  — Immutable token record
├── LexerError.java             — Lexer error codes (L001–L004)
├── Lexer.java                  — Single-pass scanner
├── Main.java                   — CLI: run / --ast / --tokens modes
│
├── ast/
│   ├── Expr.java               — Expression nodes (sealed + Visitor)
│   ├── Stmt.java               — Statement nodes (sealed + Visitor)
│   └── AstPrinter.java         — S-expression debug printer
│
├── parser/
│   ├── ParseError.java         — Parse error codes (P001–P006)
│   └── Parser.java             — Recursive-descent LL(1) parser
│
└── interpreter/
    ├── RuntimeError.java       — Runtime error codes (R001–R007)
    ├── Environment.java        — Variable scope with parent-chain
    ├── SiCallable.java         — Interface for callable values
    └── Interpreter.java        — Tree-walk interpreter

hello.si                        — Sample SIlang source file
build.sh                        — Compile and run script
```

## Build & Run

```bash
chmod +x build.sh

# Compile
./build.sh

# Run a program
./build.sh run hello.si

# Print AST (no execution)
./build.sh ast hello.si

# Print tokens (no parsing)
./build.sh tokens hello.si
```

## Manual

```bash
javac --release 21 -d out \
    silang/*.java silang/ast/*.java \
    silang/parser/*.java silang/interpreter/*.java

# Run a program
java -cp out silang.Main hello.si

# Inline source
java -cp out silang.Main --expr 'var x = 5 + 3'
java -cp out silang.Main --expr 'out("Hello, World!")'

# Print AST (no execution)
java -cp out silang.Main --ast hello.si

# Print tokens
java -cp out silang.Main --tokens hello.si
```

## Example Programs

**Hello World:**
```silang
out("Hello, World!")
```
```
Hello, World!
```

**Arithmetic:**
```silang
var a = 5 + 3
var b = 10
out(a * b)
```
```
80
```

**All features:**
```silang
// Variable declarations
var name = "Alice"
var score = 42
var pi = 3.14
var active = true

// Arithmetic with precedence
var result = (5 + 3) * 10
out(result)

// String concatenation
out(name + " scored " + score + " points")

// Unary negation
out(-score)
```
```
80
Alice scored 42 points
-42
```

## Runtime Type System

| SIlang type | Java type | Example | `out()` output |
|-------------|-----------|---------|----------------|
| `int` | `Integer` | `42` | `42` |
| `float` | `Double` | `3.14` | `3.14` |
| `string` | `String` | `"hi"` | `hi` |
| `boolean` | `Boolean` | `true` | `true` |

**Arithmetic rules:**
- `int OP int` → `int`
- `float OP numeric` → `float`
- `string + anything` → `string` (concatenation)
- `anything + string` → `string` (concatenation)
- All other combos → `RuntimeError R003`

**Float display:** Whole-number floats strip the `.0` suffix (`5.0` prints as `5`).

## Error Codes

### Lexer (L)
| Code | Meaning |
|------|---------|
| L001 | Unterminated string literal |
| L002 | Unterminated block comment |
| L003 | Unexpected character |
| L004 | Invalid escape sequence |

### Parser (P)
| Code | Meaning |
|------|---------|
| P001 | Unexpected token |
| P002 | Expected expression |
| P003 | Missing `)` in grouping |
| P004 | Missing `)` in call |
| P005 | Too many arguments |
| P006 | Missing `=` in var declaration |

### Runtime (R)
| Code | Meaning |
|------|---------|
| R001 | Division by zero |
| R002 | Unary `-` on non-numeric |
| R003 | Binary operator type mismatch |
| R004 | Undefined variable |
| R005 | Assignment to undefined variable |
| R006 | Unknown function |
| R007 | Wrong number of arguments |

## Pipeline Status

```
Lexer       ✅  Tokens with line/column positions
Parser      ✅  Recursive-descent LL(1), panic-mode recovery
AST         ✅  Sealed hierarchy + Visitor pattern
Interpreter ✅  Tree-walk with Environment scope chain
```

## Next Steps (Roadmap)

| Version | Feature |
|---------|---------|
| 0.2 | `if`/`else`, `while`, `{ }` blocks, comparison operators |
| 0.3 | User-defined functions (`fun`), `return` |
| 0.4 | Static typing, type annotations |
| 0.5 | Classes, constructors, methods |
| 0.6 | Inheritance, interfaces |
| 0.7 | Generics |
| 0.8 | Modules, imports |
