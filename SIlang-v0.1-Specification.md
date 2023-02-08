# SIlang Language Specification

**Version:** 0.1  
**Status:** Draft  
**Author:** SIlang Language Design Team  
**Date:** 2023-02-08

---

## Table of Contents

1. [Overview](#overview)
2. [Design Goals](#design-goals)
3. [Language Philosophy](#language-philosophy)
4. [Lexical Structure](#lexical-structure)
5. [Tokens](#tokens)
6. [Syntax Rules](#syntax-rules)
7. [Grammar (EBNF)](#grammar-ebnf)
8. [Expressions](#expressions)
9. [Built-in Functions](#built-in-functions)
10. [Example Programs](#example-programs)
11. [Future Language Roadmap](#future-language-roadmap)

---

## Overview

SIlang is a statically-typed, Java-inspired programming language designed from the ground up for clarity, simplicity, and long-term extensibility. Version 0.1 defines the minimal viable core of the language: variable declarations, arithmetic expressions, string literals, boolean values, and function calls.

The language is intentionally minimal at this stage. Every grammar decision in Version 0.1 has been made with careful consideration for the features that will be introduced in later versions — including classes, methods, inheritance, generics, and modules — without requiring parser rewrites.

---

## Design Goals

| Goal                 | Description                                                                        |
| -------------------- | ---------------------------------------------------------------------------------- |
| **Simplicity**       | Remove Java boilerplate while preserving familiar structure                        |
| **Readability**      | Code should read like intent, not ceremony                                         |
| **Extensibility**    | Grammar must accommodate future OOP features without breaking changes              |
| **Parser-Friendly**  | LL(1)-compatible grammar suitable for recursive descent parsing                    |
| **Type-Safe Future** | Variable declarations are designed to support optional and mandatory static typing |

---

## Language Philosophy

SIlang is built on five core principles:

1. **Java-Inspired Structure** — Familiar to Java/C# developers; brace-free where possible in early versions.
2. **Simple and Readable Syntax** — Prefer keyword clarity over symbolic density.
3. **Minimal Boilerplate** — No `public static void main`. No `System.out.println`. Just `out()`.
4. **Parser-Friendly Grammar** — Every construct begins with a unique, unambiguous token, enabling clean LL(1) parsing.
5. **Designed for Future OOP** — The grammar reserves space for `class`, `new`, access modifiers, and generics from day one, even if unused in 0.1.

---

## Lexical Structure

The lexer processes raw source text into a flat stream of tokens. Whitespace and comments are consumed and discarded between tokens (except within string literals).

### Source Encoding

SIlang source files are encoded in **UTF-8**. The canonical file extension is `.si`.

### Whitespace

Whitespace characters (space `U+0020`, tab `U+0009`, carriage return `U+000D`, newline `U+000A`) are used as token delimiters and are otherwise ignored.

```
WHITESPACE ::= ( ' ' | '\t' | '\r' | '\n' )+
```

### Comments

SIlang supports two comment styles. Comments are stripped by the lexer and do not produce tokens.

**Line comments** — from `//` to end of line:

```
// This is a line comment
var x = 5  // inline comment
```

**Block comments** — from `/*` to `*/` (non-nestable):

```
/*
  This is a block comment.
  It spans multiple lines.
*/
var y = 10
```

```
LINE_COMMENT  ::= '//' ( any character except '\n' )* '\n'?
BLOCK_COMMENT ::= '/*' ( any character, non-greedy ) '*/'
```

### Identifiers

Identifiers name variables, functions, and (in future versions) types, classes, and modules.

```
IDENTIFIER ::= LETTER ( LETTER | DIGIT | '_' )*
LETTER     ::= [a-zA-Z]
DIGIT      ::= [0-9]
```

**Valid identifiers:**

```
x
name
totalSum
user_age
myVar2
```

**Invalid identifiers:**

```
2fast      // starts with digit
my-var     // hyphen not allowed
_private   // reserved for future use (underscore-leading identifiers)
```

> **Design note:** Underscore-leading identifiers (`_name`) are reserved for future compiler-generated or internal symbols and must not be used by user code in Version 0.1.

### Integer Literals

```
INTEGER ::= DIGIT+
```

Examples: `0`, `5`, `42`, `1000`

### Floating-Point Literals

```
FLOAT ::= DIGIT+ '.' DIGIT+
```

Examples: `3.14`, `0.5`, `100.0`

> Bare `.5` (without a leading digit) is **not** valid in Version 0.1.

### String Literals

Strings are sequences of characters enclosed in double quotes. Escape sequences are supported.

```
STRING     ::= '"' STRING_CHAR* '"'
STRING_CHAR ::= ( any Unicode character except '"' and '\' )
              | ESCAPE_SEQ
ESCAPE_SEQ  ::= '\' ( '"' | '\' | 'n' | 't' | 'r' )
```

Supported escape sequences:

| Sequence | Meaning         |
| -------- | --------------- |
| `\"`     | Double quote    |
| `\\`     | Backslash       |
| `\n`     | Newline         |
| `\t`     | Tab             |
| `\r`     | Carriage return |

Examples:

```
"Hello, World!"
"She said \"hi\""
"Line one\nLine two"
```

### Boolean Literals

```
BOOLEAN ::= 'true' | 'false'
```

Boolean literals are **keywords** and cannot be used as identifiers.

### Keywords

The following words are reserved and may not be used as identifiers:

```
var   true   false
```

**Reserved for future versions (must not be used as identifiers today):**

```
class   interface   extends   implements
new     this        super      return
if      else        while      for
int     float       string     boolean
void    null        static     public
private protected   import     module
```

Reserving these keywords now prevents identifier clashes when these features are introduced.

---

## Tokens

The following tokens are produced by the lexer. Each token has a **type**, an optional **lexeme** (raw text), and a **position** (line and column) for error reporting.

| Token Type   | Lexeme            | Description                                  |
| ------------ | ----------------- | -------------------------------------------- |
| `IDENTIFIER` | variable text     | A user-defined name                          |
| `INTEGER`    | e.g. `42`         | An integer literal                           |
| `FLOAT`      | e.g. `3.14`       | A floating-point literal                     |
| `STRING`     | e.g. `"hello"`    | A string literal (quotes included in lexeme) |
| `BOOLEAN`    | `true` or `false` | A boolean literal                            |
| `VAR`        | `var`             | Variable declaration keyword                 |
| `PLUS`       | `+`               | Addition / string concatenation operator     |
| `MINUS`      | `-`               | Subtraction / unary negation operator        |
| `STAR`       | `*`               | Multiplication operator                      |
| `SLASH`      | `/`               | Division operator                            |
| `EQUAL`      | `=`               | Assignment operator                          |
| `LPAREN`     | `(`               | Left parenthesis                             |
| `RPAREN`     | `)`               | Right parenthesis                            |
| `COMMA`      | `,`               | Argument separator                           |
| `EOF`        | _(none)_          | End of file sentinel                         |

> **Design note:** `EQUAL` (`=`) is the **assignment** operator only. A future equality operator (`==`) will be a separate token `EQUAL_EQUAL`. The lexer must be implemented with 2-character lookahead to distinguish them cleanly.

### Token Examples

```
var x = 5 + 3
```

Produces:

```
VAR        "var"
IDENTIFIER "x"
EQUAL      "="
INTEGER    "5"
PLUS       "+"
INTEGER    "3"
EOF
```

---

```
out("Hello " + name)
```

Produces:

```
IDENTIFIER "out"
LPAREN     "("
STRING     "\"Hello \""
PLUS       "+"
IDENTIFIER "name"
RPAREN     ")"
EOF
```

---

## Syntax Rules

### Program Structure

A SIlang 0.1 program is a sequence of **statements**. There is no required entry point wrapper (no `main` function or class). Execution begins at the first statement and proceeds top-to-bottom.

```
program ::= statement* EOF
```

### Statements

In Version 0.1, there are two kinds of statements:

1. **Variable declarations** — bind a name to a value
2. **Expression statements** — evaluate an expression for side effects (e.g., `out(...)`)

Statements are **newline-terminated** (a logical line ends at a newline). Blank lines are ignored.

> **Design note:** Semicolons are **not** required and **not** supported in Version 0.1. A future version may introduce optional semicolons for multi-statement lines, but that decision is deferred. The grammar does not need to change — a future `SEMICOLON` token can be added as an optional statement terminator without restructuring the grammar.

### Variable Declarations

```
variableDecl ::= VAR IDENTIFIER EQUAL expression NEWLINE
```

Example:

```
var x = 5
var name = "Alice"
var price = 3.14
var active = true
var result = (5 + 3) * 10
```

> **Forward compatibility:** Future versions will support type-annotated declarations:
>
> ```
> int x = 5
> string name = "Alice"
> ```
>
> This is handled by allowing a **type prefix** before the identifier. Since type names (`int`, `string`, etc.) are reserved keywords, the parser can distinguish `int x = 5` from `var x = 5` by the leading token — no grammar ambiguity.

### Expression Statements

An expression that appears as a standalone statement (not assigned to a variable) is evaluated purely for its side effects. In Version 0.1, this is most commonly a function call.

```
exprStatement ::= expression NEWLINE
```

Example:

```
out("Hello")
out(x + y)
```

---

## Grammar (EBNF)

This is the complete formal grammar for SIlang Version 0.1, written in Extended Backus-Naur Form (EBNF).

The grammar is designed for **LL(1) recursive descent parsing**. Every production can be predicted from a single lookahead token.

```ebnf
(* ========================================================
   SIlang Version 0.1 — Complete EBNF Grammar
   ======================================================== *)

(* Top-level program *)
program         ::= statement* EOF

(* A statement is a declaration or an expression statement *)
statement       ::= variableDecl
                  | exprStatement

(* Variable declaration: var <name> = <expr> *)
variableDecl    ::= VAR IDENTIFIER EQUAL expression

(* Expression used as a statement (e.g. function call) *)
exprStatement   ::= expression

(* --------------------------------------------------------
   Expressions — ordered by precedence (lowest to highest)
   -------------------------------------------------------- *)

(* Additive: + and - (left-associative) *)
expression      ::= term ( ( PLUS | MINUS ) term )*

(* Multiplicative: * and / (left-associative, higher precedence) *)
term            ::= unary ( ( STAR | SLASH ) unary )*

(* Unary: optional negation *)
unary           ::= MINUS unary
                  | primary

(* Primary: atomic values and grouped expressions *)
primary         ::= INTEGER
                  | FLOAT
                  | STRING
                  | BOOLEAN
                  | IDENTIFIER
                  | functionCall
                  | LPAREN expression RPAREN

(* --------------------------------------------------------
   Function calls
   -------------------------------------------------------- *)

(* A call is an identifier followed by a parenthesised argument list *)
functionCall    ::= IDENTIFIER LPAREN argumentList RPAREN

(* Argument list: zero or more comma-separated expressions *)
argumentList    ::= ( expression ( COMMA expression )* )?

(* --------------------------------------------------------
   Terminals (produced by the lexer)
   -------------------------------------------------------- *)

VAR        ::= 'var'
BOOLEAN    ::= 'true' | 'false'
IDENTIFIER ::= LETTER ( LETTER | DIGIT | '_' )*
INTEGER    ::= DIGIT+
FLOAT      ::= DIGIT+ '.' DIGIT+
STRING     ::= '"' STRING_CHAR* '"'
PLUS       ::= '+'
MINUS      ::= '-'
STAR       ::= '*'
SLASH      ::= '/'
EQUAL      ::= '='
LPAREN     ::= '('
RPAREN     ::= ')'
COMMA      ::= ','
EOF        ::= (* end of input *)

LETTER     ::= [a-zA-Z]
DIGIT      ::= [0-9]
STRING_CHAR::= (* any Unicode except '"' and '\' *) | ESCAPE_SEQ
ESCAPE_SEQ ::= '\' ( '"' | '\' | 'n' | 't' | 'r' )
```

### Grammar Disambiguation: `IDENTIFIER` vs `functionCall`

In the `primary` rule, both `IDENTIFIER` and `functionCall` begin with an `IDENTIFIER` token. The parser resolves this with **one token of lookahead**:

- If `IDENTIFIER` is followed by `LPAREN` → parse as `functionCall`
- Otherwise → parse as a plain `IDENTIFIER` (variable reference)

This is a clean LL(1) distinction and requires no backtracking.

### Operator Precedence Table

| Precedence  | Operator              | Associativity | Grammar Rule |
| ----------- | --------------------- | ------------- | ------------ |
| 1 (lowest)  | `+` `-`               | Left          | `expression` |
| 2           | `*` `/`               | Left          | `term`       |
| 3           | unary `-`             | Right         | `unary`      |
| 4 (highest) | literals, `()`, calls | —             | `primary`    |

### Forward-Compatibility Notes

The grammar is structured to absorb future features naturally:

| Future Feature         | Grammar Change Required                             |
| ---------------------- | --------------------------------------------------- |
| `if` statements        | Add `ifStmt` alternative to `statement`             |
| `while` loops          | Add `whileStmt` alternative to `statement`          |
| User-defined functions | Add `funcDecl` alternative to top-level `program`   |
| Classes                | Add `classDecl` alternative to top-level `program`  |
| Return statements      | Add `returnStmt` alternative to `statement`         |
| Static typing          | Replace or augment `VAR` with a `typePrefix` rule   |
| Method calls           | Extend `primary` with `IDENTIFIER DOT functionCall` |
| Generics               | Extend type grammar with `<typeArgList>`            |

---

## Expressions

### Arithmetic Expressions

SIlang supports four arithmetic operators with standard mathematical precedence. Multiplication and division bind more tightly than addition and subtraction. Parentheses override precedence.

```
var a = 5 + 3         // 8
var b = 5 + 3 * 10    // 35  (multiplication first)
var c = (5 + 3) * 10  // 80  (parentheses override)
var d = 10 / 2 - 1    // 4
var e = -5 + 3        // -2  (unary negation)
```

### Division Semantics

In Version 0.1:

- If both operands are `INTEGER`, the result is **integer division** (truncated toward zero).
- If either operand is `FLOAT`, the result is **floating-point division**.

```
var a = 10 / 3        // 3  (integer division)
var b = 10.0 / 3      // 3.3333...  (float division)
```

Division by zero is a **runtime error** in Version 0.1. The error message must include the source line and column.

### String Concatenation

The `+` operator is overloaded for string concatenation when either operand is a `STRING`.

```
var firstName = "Alice"
var greeting = "Hello " + firstName   // "Hello Alice"
var info = "Value: " + 42             // "Value: 42"
```

When a non-string value is concatenated with a string, the non-string value is **implicitly converted to its string representation**.

Implicit conversions for `+` with a string operand:

| Type      | String Representation            |
| --------- | -------------------------------- |
| `INTEGER` | Decimal digits, no leading zeros |
| `FLOAT`   | Decimal notation                 |
| `BOOLEAN` | `"true"` or `"false"`            |

### Unary Negation

The `-` prefix operator negates a numeric value.

```
var x = -5
var y = -(3 + 4)   // -7
```

Applying unary `-` to a non-numeric type is a **runtime type error** in Version 0.1.

### Type Compatibility

| Operator | Left Type | Right Type | Result Type |
| -------- | --------- | ---------- | ----------- |
| `+`      | INTEGER   | INTEGER    | INTEGER     |
| `+`      | FLOAT     | FLOAT      | FLOAT       |
| `+`      | INTEGER   | FLOAT      | FLOAT       |
| `+`      | STRING    | any        | STRING      |
| `-`      | INTEGER   | INTEGER    | INTEGER     |
| `-`      | FLOAT     | FLOAT      | FLOAT       |
| `*`      | INTEGER   | INTEGER    | INTEGER     |
| `*`      | FLOAT     | FLOAT      | FLOAT       |
| `/`      | INTEGER   | INTEGER    | INTEGER     |
| `/`      | FLOAT     | FLOAT      | FLOAT       |

---

## Built-in Functions

Version 0.1 provides one built-in function.

### `out(value)`

Prints the string representation of `value` to standard output, followed by a newline.

**Signature (future typed form):**

```
out(value: any) -> void
```

**Behavior:**

- Accepts exactly **one argument** in Version 0.1. Passing zero or more than one argument is a compile-time error.
- If the argument is a `STRING`, prints it directly (without surrounding quotes).
- If the argument is `INTEGER`, `FLOAT`, or `BOOLEAN`, converts to string and prints.
- If the argument is an expression, evaluates it fully before printing.

**Examples:**

```
out("Hello, World!")        // Hello, World!
out(42)                     // 42
out(3.14)                   // 3.14
out(true)                   // true
out(5 + 3)                  // 8
out("Result: " + (2 * 10))  // Result: 20
```

**Design note:** `out` is implemented as a **built-in function**, not a keyword or special form. This means the parser treats it identically to any user-defined function call. The runtime resolves `out` as a special binding in the global scope. This design allows future versions to shadow or override `out` if needed, and it sets a clean precedent for how the standard library will work.

---

## Example Programs

### Example 1 — Hello World

```silang
out("Hello, World!")
```

**Output:**

```
Hello, World!
```

---

### Example 2 — Variables and Output

```silang
var x = 5
var y = 10
out(x + y)
```

**Output:**

```
15
```

---

### Example 3 — Arithmetic Expressions

```silang
var result = (5 + 3) * 10
out(result)
```

**Output:**

```
80
```

---

### Example 4 — String Variables

```silang
var name = "Aung"
out("Hello " + name)
```

**Output:**

```
Hello Aung
```

---

### Example 5 — Floating-Point Arithmetic

```silang
var price = 19.99
var tax = 1.08
var total = price * tax
out("Total: " + total)
```

**Output:**

```
Total: 21.5892
```

---

### Example 6 — Boolean Values

```silang
var active = true
var verified = false
out(active)
out(verified)
```

**Output:**

```
true
false
```

---

### Example 7 — Operator Precedence

```silang
var a = 5 + 3 * 10
var b = (5 + 3) * 10
out("Without parens: " + a)
out("With parens: " + b)
```

**Output:**

```
Without parens: 35
With parens: 80
```

---

### Example 8 — Unary Negation

```silang
var x = -7
var y = -(3 + 4)
out(x)
out(y)
```

**Output:**

```
-7
-7
```

---

### Example 9 — String Concatenation with Numbers

```silang
var score = 100
var player = "Alice"
out(player + " scored " + score + " points")
```

**Output:**

```
Alice scored 100 points
```

---

### Example 10 — Chained Expressions

```silang
var a = 10
var b = 3
var sum = a + b
var product = a * b
var difference = a - b
out("Sum: " + sum)
out("Product: " + product)
out("Difference: " + difference)
```

**Output:**

```
Sum: 13
Product: 30
Difference: 7
```

---

### Example 11 — Integer Division

```silang
var a = 10
var b = 3
var quotient = a / b
out("10 / 3 = " + quotient)
```

**Output:**

```
10 / 3 = 3
```

---

### Example 12 — Complex Expression

```silang
var x = 2
var y = 3
var z = x * x + y * y
out("x^2 + y^2 = " + z)
```

**Output:**

```
x^2 + y^2 = 13
```

---

## Future Language Roadmap

Each version listed below introduces new features while remaining **fully backward-compatible** with previous versions. Existing valid programs must continue to compile and produce identical output.

---

### Version 0.2 — Control Flow

Introduces conditional branching and loops.

```silang
if (x > 0) {
    out("positive")
} else {
    out("non-positive")
}

while (x > 0) {
    out(x)
    x = x - 1
}
```

**Grammar additions:**

- `ifStmt`, `elseClause`, `whileStmt` alternatives in `statement`
- `LBRACE`, `RBRACE` tokens
- Comparison and logical operators: `==`, `!=`, `<`, `>`, `<=`, `>=`, `&&`, `||`, `!`

---

### Version 0.3 — User-Defined Functions

Introduces function declarations with parameters and return values.

```silang
fun add(a, b) {
    return a + b
}

var result = add(3, 5)
out(result)
```

**Grammar additions:**

- `funcDecl` at the top level of `program`
- `returnStmt` in `statement`
- `FUN` keyword

---

### Version 0.4 — Static Typing

Introduces optional (then mandatory) type annotations on variables and function parameters.

```silang
int x = 5
string name = "Alice"
float price = 3.14
bool active = true

fun multiply(int a, int b) -> int {
    return a * b
}
```

**Grammar additions:**

- `typeAnnotation` rule: `INT | FLOAT_KW | STRING_KW | BOOLEAN_KW | IDENTIFIER`
- Variable declaration upgrades from `VAR` to `typeAnnotation IDENTIFIER EQUAL expression`
- Function parameter list adds `typeAnnotation` prefix
- Return type annotation: `ARROW typeAnnotation`

---

### Version 0.5 — Classes and Objects

Introduces class declarations, constructors, fields, and methods.

```silang
class Person {
    string name
    int age

    Person(string name, int age) {
        this.name = name
        this.age = age
    }

    fun greet() {
        out("Hello, I am " + this.name)
    }
}

var p = new Person("Alice", 30)
p.greet()
```

**Grammar additions:**

- `classDecl` at the top level of `program`
- `CLASS`, `NEW`, `THIS` keywords (already reserved)
- `fieldDecl`, `constructorDecl`, `methodDecl` within `classBody`
- `DOT` token for member access
- `memberAccess` and `methodCall` variants in `primary`

---

### Version 0.6 — Inheritance and Interfaces

```silang
interface Printable {
    fun print()
}

class Employee extends Person implements Printable {
    string role

    Employee(string name, int age, string role) {
        super(name, age)
        this.role = role
    }

    fun print() {
        out(this.name + " - " + this.role)
    }
}
```

**Grammar additions:**

- `EXTENDS`, `IMPLEMENTS`, `INTERFACE`, `SUPER` keywords (already reserved)
- `interfaceDecl` at the top level
- `extends` and `implements` clauses on `classDecl`

---

### Version 0.7 — Generics

```silang
class Box<T> {
    T value

    Box(T value) {
        this.value = value
    }

    fun get() -> T {
        return this.value
    }
}

var intBox = new Box<int>(42)
out(intBox.get())
```

**Grammar additions:**

- `typeParameters`: `LT IDENTIFIER (COMMA IDENTIFIER)* GT`
- `typeArguments`: same form, used at instantiation
- Parameterized type syntax in field declarations and method return types

---

### Version 0.8 — Modules and Imports

```silang
import math
import io.streams

module myapp {
    fun main() {
        out(math.sqrt(16))
    }
}
```

**Grammar additions:**

- `importDecl` and `moduleDecl` at the top level
- `IMPORT`, `MODULE` keywords (already reserved)
- Qualified name rule: `IDENTIFIER (DOT IDENTIFIER)*`

---

### Versioning Commitment

> **SIlang guarantees full backward compatibility across all minor versions within a major version.** A program valid in Version 0.1 will compile without modification in Version 0.8. Grammar extensions are strictly additive.

---

## Appendix A — Lexer State Machine Summary

The lexer operates as a single-pass state machine over the UTF-8 source. Key transitions:

| State    | Trigger          | Action                                                                    |
| -------- | ---------------- | ------------------------------------------------------------------------- |
| `START`  | letter           | → `IDENT`                                                                 |
| `START`  | digit            | → `NUMBER`                                                                |
| `START`  | `"`              | → `STRING`                                                                |
| `START`  | `+`              | emit `PLUS`                                                               |
| `START`  | `-`              | emit `MINUS`                                                              |
| `START`  | `*`              | emit `STAR`                                                               |
| `START`  | `/`              | peek next: `/` → `LINE_COMMENT`, `*` → `BLOCK_COMMENT`, else emit `SLASH` |
| `START`  | `=`              | peek next: `=` → emit `EQUAL_EQUAL` (future), else emit `EQUAL`           |
| `START`  | `(`              | emit `LPAREN`                                                             |
| `START`  | `)`              | emit `RPAREN`                                                             |
| `START`  | `,`              | emit `COMMA`                                                              |
| `START`  | whitespace       | skip                                                                      |
| `START`  | EOF              | emit `EOF`                                                                |
| `IDENT`  | letter/digit/`_` | accumulate                                                                |
| `IDENT`  | other            | check reserved: emit `VAR`/`BOOLEAN` or `IDENTIFIER`, return to `START`   |
| `NUMBER` | digit            | accumulate                                                                |
| `NUMBER` | `.` + digit      | switch to `FLOAT` accumulation                                            |
| `NUMBER` | other            | emit `INTEGER`, return to `START`                                         |
| `STRING` | `\`              | read escape sequence                                                      |
| `STRING` | `"`              | emit `STRING`, return to `START`                                          |
| `STRING` | EOF              | **error: unterminated string**                                            |

---

## Appendix B — Error Messages

The compiler must produce clear, actionable error messages. All errors include **file name, line number, and column number**.

**Lexer errors:**

```
error[L001]: unterminated string literal
  --> hello.si:3:5
   |
 3 | var x = "Hello
   |         ^ string started here, never closed
```

**Parser errors:**

```
error[P001]: expected '=' after variable name in declaration
  --> hello.si:5:9
   |
 5 | var price 3.14
   |           ^ expected '='
```

**Runtime errors:**

```
error[R001]: division by zero
  --> hello.si:7:15
   |
 7 | var x = 10 / 0
   |              ^ divisor is zero
```

---

_End of SIlang Version 0.1 Specification_
