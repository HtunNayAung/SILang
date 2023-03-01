package silang.ast;

import silang.Token;

/**
 * Base class for every statement node in the SIlang AST.
 *
 * <p>In SIlang v0.1 there are only two kinds of statement:
 * <ol>
 *   <li>{@link Var}        — a variable declaration  ({@code var x = 5})</li>
 *   <li>{@link Expression} — an expression evaluated for side-effects
 *                            ({@code out("hello")})</li>
 * </ol>
 *
 * <p>The hierarchy is {@code sealed} for the same reasons as {@link Expr}:
 * exhaustive pattern-matching and compile-time safety when new statement
 * types are added in later versions.
 *
 * <h2>Statement grammar (SIlang v0.1)</h2>
 * <pre>
 *   program     →  statement* EOF
 *   statement   →  variableDecl | exprStatement
 *   variableDecl→  "var" IDENTIFIER "=" expression
 *   exprStatement→  expression
 * </pre>
 *
 * <h2>Visitor pattern</h2>
 * <p>Just like {@link Expr}, statements expose an {@link Visitor} interface
 * so downstream phases can traverse them without touching this file.
 *
 * <h2>Forward-compatibility</h2>
 * <p>Future statement types to be added here:
 * <ul>
 *   <li>{@code If}       — {@code if (cond) { … } else { … }}  (v0.2)</li>
 *   <li>{@code While}    — {@code while (cond) { … }}          (v0.2)</li>
 *   <li>{@code Block}    — {@code { stmt* }}                   (v0.2)</li>
 *   <li>{@code Return}   — {@code return expr}                 (v0.3)</li>
 *   <li>{@code Function} — {@code fun name(params) { … }}      (v0.3)</li>
 *   <li>{@code Class}    — {@code class Name { … }}            (v0.5)</li>
 * </ul>
 */
public sealed abstract class Stmt
    permits Stmt.Var,
            Stmt.Expression {

    // ------------------------------------------------------------------ //
    //  Visitor interface                                                  //
    // ------------------------------------------------------------------ //

    /**
     * Visitor over the statement hierarchy.
     *
     * @param <R> the return type of each {@code visit} method
     */
    public interface Visitor<R> {
        R visitVarStmt(Var stmt);
        R visitExpressionStmt(Expression stmt);
    }

    /**
     * Accepts a visitor and dispatches to the correct {@code visit} method.
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
    //  Var  —  variable declaration                                      //
    // ------------------------------------------------------------------ //

    /**
     * A variable declaration statement: {@code var <name> = <initializer>}.
     *
     * <p>In SIlang v0.1 every variable declaration <em>must</em> include an
     * initializer.  The {@code initializer} field is therefore never
     * {@code null} for parsed programs.
     *
     * <p>Future versions will add type annotations:
     * <pre>
     *   int x = 5
     *   string name = "Alice"
     * </pre>
     * When that feature arrives a {@code typeAnnotation} field (nullable,
     * representing the optional explicit type) can be added here without
     * breaking the v0.1 constructor.
     *
     * <pre>Examples:
     *   var x = 5
     *   var name = "Alice"
     *   var result = (5 + 3) * 10
     * </pre>
     */
    public static final class Var extends Stmt {

        /**
         * The identifier token that names this variable.
         * {@code name.lexeme} is the variable's string name.
         * {@code name.line} / {@code name.column} locate it in source.
         */
        public final Token name;

        /**
         * The expression whose value will be bound to {@link #name}.
         * Never {@code null} in SIlang v0.1.
         */
        public final Expr initializer;

        public Var(Token name, Expr initializer) {
            this.name        = name;
            this.initializer = initializer;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVarStmt(this);
        }

        @Override
        public String toString() {
            return "Var(" + name.lexeme + " = " + initializer + ")";
        }
    }

    // ------------------------------------------------------------------ //
    //  Expression  —  expression evaluated for side-effects              //
    // ------------------------------------------------------------------ //

    /**
     * An expression used as a statement — evaluated purely for its
     * side-effects and whose value is discarded.
     *
     * <p>In SIlang v0.1 the only useful expression-statements are function
     * calls such as {@code out("Hello")}.  The grammar allows any expression
     * to be a statement; later versions will add an optional trailing
     * semicolon without changing this node.
     *
     * <pre>Examples:
     *   out("Hello")
     *   out(x + y)
     *   compute(a, b)   — (once user-defined functions exist)
     * </pre>
     */
    public static final class Expression extends Stmt {

        /**
         * The expression to evaluate.
         * For v0.1 programs this is almost always a {@link Expr.Call}.
         */
        public final Expr expression;

        public Expression(Expr expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }

        @Override
        public String toString() {
            return "ExprStmt(" + expression + ")";
        }
    }
}
