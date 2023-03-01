package silang.ast;

import java.util.List;

/**
 * Converts an AST back into a human-readable string for debugging.
 *
 * <p>The output format is a Lisp-style S-expression, matching the format
 * described in the SIlang compiler spec:
 *
 * <pre>
 *   (var x (+ 5 3))
 *   (call out x)
 *   (+ (* 2 3) (/ 4 2))
 * </pre>
 *
 * <p>This class implements both {@link Expr.Visitor} and {@link Stmt.Visitor},
 * serving as a reference example of the visitor pattern for future phases
 * (type-checker, interpreter, code-generator) to follow.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AstPrinter printer = new AstPrinter();
 *
 * // Print a single expression
 * String exprStr = printer.print(expr);
 *
 * // Print a full program
 * String programStr = printer.print(statements);
 * }</pre>
 */
public final class AstPrinter
        implements Expr.Visitor<String>, Stmt.Visitor<String> {

    // ------------------------------------------------------------------ //
    //  Public entry points                                               //
    // ------------------------------------------------------------------ //

    /**
     * Prints a single expression as an S-expression string.
     *
     * @param expr the expression to print; must not be {@code null}
     * @return the S-expression string
     */
    public String print(Expr expr) {
        return expr.accept(this);
    }

    /**
     * Prints a single statement as an S-expression string.
     *
     * @param stmt the statement to print; must not be {@code null}
     * @return the S-expression string
     */
    public String print(Stmt stmt) {
        return stmt.accept(this);
    }

    /**
     * Prints an entire program (list of statements), one per line.
     *
     * @param statements the list of top-level statements
     * @return a newline-separated S-expression string
     */
    public String print(List<Stmt> statements) {
        if (statements.isEmpty()) return "(program)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < statements.size(); i++) {
            sb.append(statements.get(i).accept(this));
            if (i < statements.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ //
    //  Expr.Visitor implementations                                      //
    // ------------------------------------------------------------------ //

    /**
     * {@code (op left right)} — e.g. {@code (+ 5 3)}, {@code (* x y)}
     */
    @Override
    public String visitBinary(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    /**
     * {@code (op right)} — e.g. {@code (- 5)}, {@code (- x)}
     */
    @Override
    public String visitUnary(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    /**
     * The raw literal value: {@code 42}, {@code 3.14}, {@code "hello"},
     * {@code true}, {@code false}, {@code null}.
     */
    @Override
    public String visitLiteral(Expr.Literal expr) {
        if (expr.value == null)             return "null";
        if (expr.value instanceof String s) return "\"" + s + "\"";
        return String.valueOf(expr.value);
    }

    /**
     * Just the variable name: {@code x}, {@code totalSum}.
     */
    @Override
    public String visitVariable(Expr.Variable expr) {
        return expr.name.lexeme;
    }

    /**
     * {@code (group inner)} — makes grouping visible in the printed tree.
     */
    @Override
    public String visitGrouping(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    /**
     * {@code (call callee arg1 arg2 ...)} — e.g. {@code (call out "Hello")}.
     */
    @Override
    public String visitCall(Expr.Call expr) {
        // Build args array for parenthesize
        Expr[] parts = new Expr[expr.arguments.size() + 1];
        parts[0] = expr.callee;
        for (int i = 0; i < expr.arguments.size(); i++) {
            parts[i + 1] = expr.arguments.get(i);
        }
        return parenthesize("call", parts);
    }

    // ------------------------------------------------------------------ //
    //  Stmt.Visitor implementations                                      //
    // ------------------------------------------------------------------ //

    /**
     * {@code (var name initializer)} — e.g. {@code (var x (+ 5 3))}.
     */
    @Override
    public String visitVarStmt(Stmt.Var stmt) {
        return "(var " + stmt.name.lexeme + " " + stmt.initializer.accept(this) + ")";
    }

    /**
     * Delegates directly to the inner expression's printer.
     * e.g. {@code (call out x)} rather than wrapping in an extra layer.
     */
    @Override
    public String visitExpressionStmt(Stmt.Expression stmt) {
        return stmt.expression.accept(this);
    }

    // ------------------------------------------------------------------ //
    //  Helper                                                            //
    // ------------------------------------------------------------------ //

    /**
     * Wraps a name and a sequence of sub-expressions in parentheses.
     *
     * <pre>
     *   parenthesize("+", a, b)   →  "(+ a b)"
     *   parenthesize("group", e)  →  "(group e)"
     * </pre>
     */
    private String parenthesize(String name, Expr... exprs) {
        StringBuilder sb = new StringBuilder();
        sb.append('(').append(name);
        for (Expr expr : exprs) {
            sb.append(' ');
            sb.append(expr.accept(this));
        }
        sb.append(')');
        return sb.toString();
    }
}
