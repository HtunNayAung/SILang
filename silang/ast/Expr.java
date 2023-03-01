package silang.ast;

import silang.Token;
import java.util.List;

/**
 * Base class for every expression node in the SIlang AST.
 *
 * <p>The hierarchy is {@code sealed} so the compiler enforces exhaustiveness
 * in pattern-matching switches.  Every concrete node type is a {@code record},
 * giving automatic constructors, accessors, {@code equals}, {@code hashCode},
 * and a readable {@code toString} — all without boilerplate.
 *
 * <h2>Expression grammar (SIlang v0.1)</h2>
 * <pre>
 *   expression  →  term  ( ( "+" | "-" )  term  )*
 *   term        →  unary ( ( "*" | "/" )  unary )*
 *   unary       →  "-" unary | primary
 *   primary     →  INTEGER | FLOAT | STRING | BOOLEAN
 *               |  IDENTIFIER
 *               |  functionCall
 *               |  "(" expression ")"
 * </pre>
 *
 * <h2>Visitor pattern</h2>
 * <p>The {@link Visitor} interface lets downstream phases (pretty-printer,
 * type-checker, interpreter, code-generator) traverse the tree without
 * modifying this file.  Adding a new phase = implementing {@code Visitor};
 * adding a new node type = adding a case to the sealed hierarchy and to
 * each existing {@code Visitor} implementation.
 *
 * <h2>Forward-compatibility</h2>
 * <p>The sealed hierarchy makes it trivial to add future nodes:
 * <ul>
 *   <li>{@code Assign}   — {@code x = value}        (v0.2)</li>
 *   <li>{@code Logical}  — {@code a && b}, {@code a || b} (v0.2)</li>
 *   <li>{@code Get}      — {@code obj.field}         (v0.5)</li>
 *   <li>{@code Set}      — {@code obj.field = val}   (v0.5)</li>
 *   <li>{@code This}     — {@code this}              (v0.5)</li>
 *   <li>{@code Super}    — {@code super.method()}    (v0.6)</li>
 * </ul>
 */
public sealed abstract class Expr
    permits Expr.Binary,
            Expr.Unary,
            Expr.Literal,
            Expr.Variable,
            Expr.Grouping,
            Expr.Call {

    // ------------------------------------------------------------------ //
    //  Visitor interface                                                  //
    // ------------------------------------------------------------------ //

    /**
     * Visitor over the expression hierarchy.
     *
     * <p>Implement this interface in every phase that needs to walk the AST
     * (pretty-printer, type-checker, interpreter, etc.).
     *
     * @param <R> the return type of each {@code visit} method
     */
    public interface Visitor<R> {
        R visitBinary(Binary expr);
        R visitUnary(Unary expr);
        R visitLiteral(Literal expr);
        R visitVariable(Variable expr);
        R visitGrouping(Grouping expr);
        R visitCall(Call expr);
    }

    /**
     * Accepts a visitor and dispatches to the appropriate {@code visit} method.
     *
     * @param <R>     the visitor's return type
     * @param visitor the visitor to dispatch to
     * @return the value returned by the visitor
     */
    public abstract <R> R accept(Visitor<R> visitor);

    // ================================================================== //
    //  Concrete node types                                                //
    // ================================================================== //

    // ------------------------------------------------------------------ //
    //  Binary  —  left OP right                                          //
    // ------------------------------------------------------------------ //

    /**
     * A binary infix expression: {@code left operator right}.
     *
     * <p>In SIlang v0.1 the operator token is one of:
     * {@code +}, {@code -}, {@code *}, {@code /}.
     *
     * <p>The {@code +} operator is overloaded: when either operand evaluates
     * to a {@link String} it performs concatenation; otherwise arithmetic.
     *
     * <pre>Examples:
     *   5 + 3
     *   price * tax
     *   "Hello " + name
     * </pre>
     */
    public static final class Binary extends Expr {
        /** The left-hand operand. */
        public final Expr  left;
        /** The operator token (carries line/column for error messages). */
        public final Token operator;
        /** The right-hand operand. */
        public final Expr  right;

        public Binary(Expr left, Token operator, Expr right) {
            this.left     = left;
            this.operator = operator;
            this.right    = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinary(this);
        }

        @Override
        public String toString() {
            return "Binary(" + left + " " + operator.lexeme + " " + right + ")";
        }
    }

    // ------------------------------------------------------------------ //
    //  Unary  —  OP right                                                //
    // ------------------------------------------------------------------ //

    /**
     * A unary prefix expression: {@code operator right}.
     *
     * <p>In SIlang v0.1 only unary {@code -} (negation) is supported.
     * Future versions will add {@code !} (logical NOT).
     *
     * <pre>Examples:
     *   -5
     *   -(x + y)
     * </pre>
     */
    public static final class Unary extends Expr {
        /** The prefix operator token. */
        public final Token operator;
        /** The operand expression. */
        public final Expr  right;

        public Unary(Token operator, Expr right) {
            this.operator = operator;
            this.right    = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnary(this);
        }

        @Override
        public String toString() {
            return "Unary(" + operator.lexeme + right + ")";
        }
    }

    // ------------------------------------------------------------------ //
    //  Literal  —  a compile-time constant value                         //
    // ------------------------------------------------------------------ //

    /**
     * A literal (constant) value.
     *
     * <p>The {@code value} field holds the already-parsed Java object:
     * <ul>
     *   <li>{@link Integer}  for integer literals  ({@code 42})</li>
     *   <li>{@link Double}   for float literals    ({@code 3.14})</li>
     *   <li>{@link String}   for string literals   ({@code "hello"})</li>
     *   <li>{@link Boolean}  for boolean literals  ({@code true}, {@code false})</li>
     *   <li>{@code null}     for the future {@code null} keyword</li>
     * </ul>
     *
     * <pre>Examples:
     *   42
     *   3.14
     *   "Alice"
     *   true
     * </pre>
     */
    public static final class Literal extends Expr {
        /**
         * The compile-time value.  One of {@link Integer}, {@link Double},
         * {@link String}, {@link Boolean}, or {@code null}.
         */
        public final Object value;

        public Literal(Object value) {
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteral(this);
        }

        @Override
        public String toString() {
            if (value == null)            return "null";
            if (value instanceof String s) return "\"" + s + "\"";
            return String.valueOf(value);
        }
    }

    // ------------------------------------------------------------------ //
    //  Variable  —  a name reference                                     //
    // ------------------------------------------------------------------ //

    /**
     * A reference to a named variable (or function, in a call position).
     *
     * <p>The {@code name} token carries the identifier text and the source
     * position, both of which are needed for error reporting at runtime.
     *
     * <pre>Examples:
     *   x
     *   totalSum
     *   out   (as the callee of a call expression)
     * </pre>
     */
    public static final class Variable extends Expr {
        /** The identifier token — {@code name.lexeme} gives the variable name. */
        public final Token name;

        public Variable(Token name) {
            this.name = name;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVariable(this);
        }

        @Override
        public String toString() {
            return name.lexeme;
        }
    }

    // ------------------------------------------------------------------ //
    //  Grouping  —  parenthesised sub-expression                         //
    // ------------------------------------------------------------------ //

    /**
     * A parenthesised expression that explicitly overrides operator
     * precedence.  The parentheses themselves are not represented in the
     * AST beyond this wrapper node — they affect only the tree structure.
     *
     * <pre>Examples:
     *   (5 + 3)
     *   (a * b) + c
     * </pre>
     */
    public static final class Grouping extends Expr {
        /** The expression inside the parentheses. */
        public final Expr expression;

        public Grouping(Expr expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGrouping(this);
        }

        @Override
        public String toString() {
            return "(group " + expression + ")";
        }
    }

    // ------------------------------------------------------------------ //
    //  Call  —  function / method invocation                             //
    // ------------------------------------------------------------------ //

    /**
     * A function call expression: {@code callee(arg1, arg2, ...)}.
     *
     * <p>The {@code callee} is any expression that evaluates to something
     * callable.  In SIlang v0.1 callees are always {@link Variable} nodes
     * (simple name references), but the grammar is designed so future
     * versions can support method calls ({@code obj.method(...)}) by making
     * {@code callee} a {@link Get} expression.
     *
     * <p>The {@code paren} token (the opening {@code (}) is retained to give
     * precise source-position information in arity-mismatch error messages
     * at runtime.
     *
     * <pre>Examples:
     *   out("Hello")
     *   add(x, y)
     *   compute(a + b, 10)
     * </pre>
     */
    public static final class Call extends Expr {
        /**
         * The expression that resolves to the function being called.
         * In v0.1 this is always a {@link Variable}.
         */
        public final Expr        callee;

        /**
         * The opening {@code (} token — used for error-message positioning.
         */
        public final Token       paren;

        /**
         * The list of argument expressions, in source order.
         * May be empty for zero-argument calls.
         */
        public final List<Expr>  arguments;

        public Call(Expr callee, Token paren, List<Expr> arguments) {
            this.callee    = callee;
            this.paren     = paren;
            this.arguments = List.copyOf(arguments); // defensive immutable copy
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitCall(this);
        }

        @Override
        public String toString() {
            return "Call(" + callee + ", args=" + arguments + ")";
        }
    }
}
