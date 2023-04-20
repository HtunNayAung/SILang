package silang.ast;

import silang.Token;
import java.util.List;

/**
 * Base class for every expression node in the SIlang AST.
 *
 * <h2>Expression hierarchy (SIlang v0.2)</h2>
 * <ul>
 *   <li>{@link Binary}     — {@code a + b}, {@code a * b}, etc.</li>
 *   <li>{@link Comparison} — {@code a > b}, {@code a == b}, etc. → always boolean</li>
 *   <li>{@link Logical}    — {@code a && b}, {@code a || b}      → short-circuit boolean</li>
 *   <li>{@link Unary}      — {@code -x}, {@code !b}</li>
 *   <li>{@link Literal}    — {@code 42}, {@code "hi"}, {@code true}</li>
 *   <li>{@link Variable}   — {@code x}</li>
 *   <li>{@link Grouping}   — {@code (expr)}</li>
 *   <li>{@link Call}       — {@code out(x)}</li>
 * </ul>
 *
 * <h2>Expression grammar (SIlang v0.2)</h2>
 * <pre>
 *   expression   →  logical
 *   logical      →  comparison ( ( "&&" | "||" ) comparison )*
 *   comparison   →  additive ( ( "==" | "!=" | "<" | "<=" | ">" | ">=" ) additive )*
 *   additive     →  term ( ( "+" | "-" ) term )*
 *   term         →  unary ( ( "*" | "/" ) unary )*
 *   unary        →  ( "-" | "!" ) unary | primary
 *   primary      →  INTEGER | FLOAT | STRING | BOOLEAN
 *                |  IDENTIFIER ( "(" argumentList ")" )?
 *                |  "(" expression ")"
 * </pre>
 *
 * <h2>Operator precedence (lowest → highest)</h2>
 * <pre>
 *   1. Logical       &&  ||      short-circuit
 *   2. Comparison    ==  !=  <  <=  >  >=
 *   3. Additive      +   -
 *   4. Multiplicative *  /
 *   5. Unary         -   !
 *   6. Primary       literals, variables, calls, groupings
 * </pre>
 *
 * <h2>Forward-compatibility</h2>
 * <ul>
 *   <li>{@code Get}   — {@code obj.field}        (v0.5)</li>
 *   <li>{@code Set}   — {@code obj.field = val}  (v0.5)</li>
 *   <li>{@code This}  — {@code this}             (v0.5)</li>
 *   <li>{@code Super} — {@code super.method()}   (v0.6)</li>
 * </ul>
 */
public sealed abstract class Expr
    permits Expr.Binary,
            Expr.Comparison,
            Expr.Logical,
            Expr.Unary,
            Expr.Literal,
            Expr.Variable,
            Expr.Grouping,
            Expr.Call {

    // ------------------------------------------------------------------ //
    //  Visitor interface                                                  //
    // ------------------------------------------------------------------ //

    /**
     * Visitor over all expression types.
     *
     * @param <R> return type of each visit method
     */
    public interface Visitor<R> {
        R visitBinary(Binary expr);
        R visitComparison(Comparison expr);
        R visitLogical(Logical expr);
        R visitUnary(Unary expr);
        R visitLiteral(Literal expr);
        R visitVariable(Variable expr);
        R visitGrouping(Grouping expr);
        R visitCall(Call expr);
    }

    /** Accepts a visitor and dispatches to the matching visit method. */
    public abstract <R> R accept(Visitor<R> visitor);

    // ================================================================== //
    //  Concrete node types                                                //
    // ================================================================== //

    // ------------------------------------------------------------------ //
    //  Binary  —  arithmetic operators  (+, -, *, /)                    //
    // ------------------------------------------------------------------ //

    /**
     * An arithmetic binary expression.
     *
     * <p>Only arithmetic operators live here: {@code +}, {@code -}, {@code *},
     * {@code /}.  Comparison and logical operators have their own node types
     * so the type-checker can validate them independently.
     *
     * <pre>
     *   5 + 3    a * b    x / 2.0
     * </pre>
     */
    public static final class Binary extends Expr {
        public final Expr  left;
        public final Token operator;
        public final Expr  right;

        public Binary(Expr left, Token operator, Expr right) {
            this.left     = left;
            this.operator = operator;
            this.right    = right;
        }

        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visitBinary(this); }
        @Override public String toString() { return "(" + operator.lexeme + " " + left + " " + right + ")"; }
    }

    // ------------------------------------------------------------------ //
    //  Comparison  —  relational and equality  (==, !=, <, <=, >, >=)  //
    // ------------------------------------------------------------------ //

    /**
     * A comparison expression — always evaluates to {@link Boolean}.
     *
     * <p>Kept separate from {@link Binary} so:
     * <ul>
     *   <li>The type-checker ({@link silang.interpreter.TypeSystem}) can enforce
     *       that only compatible types are compared (numeric vs numeric,
     *       string vs string, etc.).</li>
     *   <li>Code generators can emit different bytecode for comparisons.</li>
     *   <li>Pattern matching switches stay exhaustive at the type level.</li>
     * </ul>
     *
     * <pre>
     *   x > 0
     *   a == b
     *   name != "Alice"
     * </pre>
     */
    public static final class Comparison extends Expr {
        public final Expr  left;
        public final Token operator;   // ==  !=  <  <=  >  >=
        public final Expr  right;

        public Comparison(Expr left, Token operator, Expr right) {
            this.left     = left;
            this.operator = operator;
            this.right    = right;
        }

        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visitComparison(this); }
        @Override public String toString() { return "(" + operator.lexeme + " " + left + " " + right + ")"; }
    }

    // ------------------------------------------------------------------ //
    //  Logical  —  short-circuit boolean operators  (&& , ||)           //
    // ------------------------------------------------------------------ //

    /**
     * A short-circuit logical expression.
     *
     * <p>Both operands must be {@code boolean}; the right operand is only
     * evaluated if necessary:
     * <ul>
     *   <li>{@code &&} — right is skipped if left is {@code false}</li>
     *   <li>{@code ||} — right is skipped if left is {@code true}</li>
     * </ul>
     *
     * <p>Kept separate from {@link Binary} because its evaluation semantics
     * are fundamentally different: sub-expressions may not be evaluated.
     *
     * <pre>
     *   x > 0 && x < 10
     *   done || count > max
     * </pre>
     */
    public static final class Logical extends Expr {
        public final Expr  left;
        public final Token operator;   // &&  ||
        public final Expr  right;

        public Logical(Expr left, Token operator, Expr right) {
            this.left     = left;
            this.operator = operator;
            this.right    = right;
        }

        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visitLogical(this); }
        @Override public String toString() { return "(" + operator.lexeme + " " + left + " " + right + ")"; }
    }

    // ------------------------------------------------------------------ //
    //  Unary  —  prefix operators  (-, !)                               //
    // ------------------------------------------------------------------ //

    /**
     * A unary prefix expression.
     *
     * <ul>
     *   <li>{@code -}  negation — int/float only</li>
     *   <li>{@code !}  logical NOT — boolean only (v0.2)</li>
     * </ul>
     *
     * <pre>
     *   -x     !done     -(a + b)
     * </pre>
     */
    public static final class Unary extends Expr {
        public final Token operator;
        public final Expr  right;

        public Unary(Token operator, Expr right) {
            this.operator = operator;
            this.right    = right;
        }

        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visitUnary(this); }
        @Override public String toString() { return "(" + operator.lexeme + " " + right + ")"; }
    }

    // ------------------------------------------------------------------ //
    //  Literal  —  compile-time constant                                 //
    // ------------------------------------------------------------------ //

    /**
     * A literal value parsed directly from source.
     *
     * <p>The {@link #value} field holds the Java runtime object:
     * {@link Integer}, {@link Double}, {@link String}, {@link Boolean},
     * or {@code null}.
     */
    public static final class Literal extends Expr {
        public final Object value;

        public Literal(Object value) { this.value = value; }

        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visitLiteral(this); }
        @Override public String toString() {
            return value == null ? "null"
                 : value instanceof String ? "\"" + value + "\""
                 : value.toString();
        }
    }

    // ------------------------------------------------------------------ //
    //  Variable  —  identifier reference                                 //
    // ------------------------------------------------------------------ //

    /**
     * A reference to a named variable.  Resolved against the current
     * {@link silang.interpreter.Environment} at runtime.
     */
    public static final class Variable extends Expr {
        public final Token name;

        public Variable(Token name) { this.name = name; }

        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visitVariable(this); }
        @Override public String toString() { return name.lexeme; }
    }

    // ------------------------------------------------------------------ //
    //  Grouping  —  parenthesised expression                            //
    // ------------------------------------------------------------------ //

    /**
     * An explicitly parenthesised sub-expression.  Transparent at runtime —
     * only affects parsing precedence.
     */
    public static final class Grouping extends Expr {
        public final Expr expression;

        public Grouping(Expr expression) { this.expression = expression; }

        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visitGrouping(this); }
        @Override public String toString() { return "(group " + expression + ")"; }
    }

    // ------------------------------------------------------------------ //
    //  Call  —  function invocation                                      //
    // ------------------------------------------------------------------ //

    /**
     * A function call expression.
     *
     * <p>In v0.1–v0.2 the callee is always a {@link Variable} (a named
     * function).  Future versions will support arbitrary callee expressions.
     *
     * <pre>
     *   out("hello")
     *   compute(a, b + 1)
     * </pre>
     */
    public static final class Call extends Expr {
        /** The callee expression (always a {@link Variable} in v0.2). */
        public final Expr       callee;

        /** The {@code (} token — used for error position reporting. */
        public final Token      paren;

        /** The argument expressions, in source order. */
        public final List<Expr> arguments;

        public Call(Expr callee, Token paren, List<Expr> arguments) {
            this.callee    = callee;
            this.paren     = paren;
            this.arguments = List.copyOf(arguments);
        }

        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visitCall(this); }
        @Override public String toString() {
            return "(call " + callee + " " + arguments + ")";
        }
    }
}
