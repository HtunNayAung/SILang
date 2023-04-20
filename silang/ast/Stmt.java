package silang.ast;

import silang.Token;

import java.util.List;

/**
 * Base class for every statement node in the SIlang AST.
 *
 * <h2>Statement hierarchy (SIlang v0.2)</h2>
 * <ul>
 * <li>{@link Var} — {@code var x = expr}</li>
 * <li>{@link Assign} — {@code x = expr} (re-assignment)</li>
 * <li>{@link Expression} — {@code expr} (side-effect only)</li>
 * <li>{@link Block} — {@code { stmt* }}</li>
 * <li>{@link If} — {@code if (cond) block (else block)?}</li>
 * <li>{@link While} — {@code while (cond) block}</li>
 * </ul>
 *
 * <h2>Grammar (SIlang v0.2)</h2>
 * 
 * <pre>
 *   program      →  declaration* EOF
 *   declaration  →  variableDecl | statement
 *   variableDecl →  "var" IDENTIFIER "=" expression
 *   statement    →  assignStmt | exprStatement | ifStmt | whileStmt | block
 *   assignStmt   →  IDENTIFIER "=" expression
 *   exprStatement→  expression
 *   ifStmt       →  "if" "(" expression ")" block ( "else" block )?
 *   whileStmt    →  "while" "(" expression ")" block
 *   block        →  "{" declaration* "}"
 * </pre>
 *
 * <h2>Forward-compatibility</h2>
 * <ul>
 * <li>{@code Return} — {@code return expr} (v0.3)</li>
 * <li>{@code Function} — {@code fun name(p*) block} (v0.3)</li>
 * <li>{@code For} — {@code for (init;cond;step)} (v0.4)</li>
 * <li>{@code Class} — {@code class Name { … }} (v0.5)</li>
 * </ul>
 */
public sealed abstract class Stmt
        permits Stmt.Var,
        Stmt.Assign,
        Stmt.Expression,
        Stmt.Block,
        Stmt.If,
        Stmt.While {

    // ------------------------------------------------------------------ //
    // Visitor interface //
    // ------------------------------------------------------------------ //

    /**
     * Visitor over all statement types.
     *
     * @param <R> return type of each visit method
     */
    public interface Visitor<R> {
        R visitVarStmt(Var stmt);

        R visitAssignStmt(Assign stmt);

        R visitExpressionStmt(Expression stmt);

        R visitBlockStmt(Block stmt);

        R visitIfStmt(If stmt);

        R visitWhileStmt(While stmt);
    }

    /** Accepts a visitor and dispatches to the matching visit method. */
    public abstract <R> R accept(Visitor<R> visitor);

    // ================================================================== //
    // Concrete node types //
    // ================================================================== //

    // ------------------------------------------------------------------ //
    // Var — variable declaration (var x = expr) //
    // ------------------------------------------------------------------ //

    /**
     * Declares a new variable in the current scope and binds an initial value.
     *
     * <pre>
     *   var x = 5 + 3
     *   var name = "Alice"
     * </pre>
     */
    public static final class Var extends Stmt {

        /** The identifier token naming this variable. */
        public final Token name;

        /** The expression evaluated to produce the initial value. Never null. */
        public final Expr initializer;

        public Var(Token name, Expr initializer) {
            this.name = name;
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
    // Assign — re-assignment (x = expr) //
    // ------------------------------------------------------------------ //

    /**
     * Re-assigns a value to a previously declared variable.
     *
     * <p>
     * Distinct from {@link Var}: does not introduce a new binding; the
     * variable must already exist in the scope chain (throws R005 otherwise).
     *
     * <pre>
     *   x = x - 1
     *   total = price * qty
     * </pre>
     *
     * <h3>Why a statement, not an expression?</h3>
     * <p>
     * SIlang v0.2 makes assignment a <em>statement</em> rather than an
     * expression to avoid assignment-in-condition bugs like {@code if (x = 0)}.
     * Future versions may relax this.
     */
    public static final class Assign extends Stmt {

        /** The identifier token naming the variable to update. */
        public final Token name;

        /** The expression evaluated to produce the new value. */
        public final Expr value;

        public Assign(Token name, Expr value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitAssignStmt(this);
        }

        @Override
        public String toString() {
            return "Assign(" + name.lexeme + " = " + value + ")";
        }
    }

    // ------------------------------------------------------------------ //
    // Expression — expr evaluated for side-effects //
    // ------------------------------------------------------------------ //

    /**
     * An expression used as a statement; its value is discarded.
     * Primarily used for function calls: {@code out("hello")}.
     */
    public static final class Expression extends Stmt {

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
            return "Expr(" + expression + ")";
        }
    }

    // ------------------------------------------------------------------ //
    // Block — { declaration* } //
    // ------------------------------------------------------------------ //

    /**
     * A braced list of declarations forming a new nested scope.
     *
     * <p>
     * Blocks are used as the body of {@code if}, {@code else}, and
     * {@code while}. Each block creates a child
     * {@link silang.interpreter.Environment}
     * so variables declared inside don't leak out:
     *
     * <pre>
     *   {
     *       var temp = x
     *       x = y
     *       y = temp
     *   }
     * </pre>
     *
     * <p>
     * Future: standalone blocks will allow scoped temporaries anywhere.
     */
    public static final class Block extends Stmt {

        /** The statements inside the braces. May be empty. */
        public final List<Stmt> statements;

        public Block(List<Stmt> statements) {
            this.statements = List.copyOf(statements);
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }

        @Override
        public String toString() {
            return "Block(" + statements.size() + " stmts)";
        }
    }

    // ------------------------------------------------------------------ //
    // If — if (cond) block (else block)? //
    // ------------------------------------------------------------------ //

    /**
     * A conditional statement with an optional else branch.
     *
     * <p>
     * The condition must evaluate to a {@code boolean}; any other type
     * throws {@link silang.interpreter.RuntimeError} R008 at runtime.
     *
     * <pre>
     *   if (x > 0) {
     *       out("positive")
     *   } else {
     *       out("non-positive")
     *   }
     * </pre>
     *
     * <p>
     * Dangling-else is resolved by the parser: an {@code else} always
     * belongs to the nearest enclosing {@code if} (standard rule).
     */
    public static final class If extends Stmt {

        /** The {@code if} keyword token — used for error position reporting. */
        public final Token keyword;

        /** The condition expression. Must evaluate to boolean at runtime. */
        public final Expr condition;

        /** The then-branch block. Always non-null. */
        public final Block thenBranch;

        /**
         * The else-branch block, or {@code null} if there is no {@code else}.
         */
        public final Block elseBranch; // nullable

        public If(Token keyword, Expr condition, Block thenBranch, Block elseBranch) {
            this.keyword = keyword;
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }

        @Override
        public String toString() {
            return "If(" + condition + ", then=" + thenBranch
                    + (elseBranch != null ? ", else=" + elseBranch : "") + ")";
        }
    }

    // ------------------------------------------------------------------ //
    // While — while (cond) block //
    // ------------------------------------------------------------------ //

    /**
     * A while loop: repeatedly executes {@link #body} as long as
     * {@link #condition} evaluates to {@code true}.
     *
     * <p>
     * The condition must evaluate to a {@code boolean}; any other type
     * throws {@link silang.interpreter.RuntimeError} R008 at runtime.
     *
     * <pre>
     *   var n = 5
     *   while (n > 0) {
     *       out(n)
     *       n = n - 1
     *   }
     * </pre>
     *
     * <p>
     * Future: {@code break} and {@code continue} will be added as
     * {@link silang.interpreter.BreakSignal} /
     * {@link silang.interpreter.ContinueSignal}
     * exceptions that unwind the call stack to the nearest enclosing loop.
     */
    public static final class While extends Stmt {

        /** The {@code while} keyword token — used for error position reporting. */
        public final Token keyword;

        /** The loop condition. Must evaluate to boolean at runtime. */
        public final Expr condition;

        /** The loop body block. Executed once per iteration. */
        public final Block body;

        public While(Token keyword, Expr condition, Block body) {
            this.keyword = keyword;
            this.condition = condition;
            this.body = body;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitWhileStmt(this);
        }

        @Override
        public String toString() {
            return "While(" + condition + ", " + body + ")";
        }
    }
}
