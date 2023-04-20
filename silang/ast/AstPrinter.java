package silang.ast;

import java.util.List;

/**
 * Produces a parenthesised S-expression representation of a SIlang AST
 * for debugging and the {@code --ast} CLI flag.
 *
 * <h2>Output examples</h2>
 * <pre>
 *   var x = 5 + 3          →  (var x (+ 5 3))
 *   out(x * 10)            →  (call out (* x 10))
 *   if (x > 0) { out(x) } →  (if (> x 0) (block (call out x)))
 *   while (n > 0) { ... } →  (while (> n 0) (block ...))
 *   x = x - 1             →  (assign x (- x 1))
 *   x > 0 && x < 10       →  (&& (> x 0) (< x 10))
 * </pre>
 */
// v0.2 — visitComparison, visitLogical, visitAssignStmt, visitBlockStmt, visitIfStmt, visitWhileStmt added
public final class AstPrinter
        implements Expr.Visitor<String>, Stmt.Visitor<String> {

    // ------------------------------------------------------------------ //
    //  Public API                                                        //
    // ------------------------------------------------------------------ //

    /** Formats a statement as an S-expression string. */
    public String print(Stmt stmt)  { return stmt.accept(this); }

    /** Formats an expression as an S-expression string. */
    public String print(Expr expr)  { return expr.accept(this); }

    // ================================================================== //
    //  Stmt.Visitor                                                      //
    // ================================================================== //

    @Override
    public String visitVarStmt(Stmt.Var stmt) {
        return "(var " + stmt.name.lexeme + " " + stmt.initializer.accept(this) + ")";
    }

    @Override
    public String visitAssignStmt(Stmt.Assign stmt) {
        return "(assign " + stmt.name.lexeme + " " + stmt.value.accept(this) + ")";
    }

    @Override
    public String visitExpressionStmt(Stmt.Expression stmt) {
        return stmt.expression.accept(this);
    }

    @Override
    public String visitBlockStmt(Stmt.Block stmt) {
        StringBuilder sb = new StringBuilder("(block");
        for (Stmt s : stmt.statements) {
            sb.append(" ").append(s.accept(this));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitIfStmt(Stmt.If stmt) {
        StringBuilder sb = new StringBuilder("(if ");
        sb.append(stmt.condition.accept(this));
        sb.append(" ").append(stmt.thenBranch.accept(this));
        if (stmt.elseBranch != null) {
            sb.append(" ").append(stmt.elseBranch.accept(this));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitWhileStmt(Stmt.While stmt) {
        return "(while " + stmt.condition.accept(this)
             + " " + stmt.body.accept(this) + ")";
    }

    // ================================================================== //
    //  Expr.Visitor                                                      //
    // ================================================================== //

    @Override
    public String visitBinary(Expr.Binary expr) {
        return "(" + expr.operator.lexeme
             + " " + expr.left.accept(this)
             + " " + expr.right.accept(this) + ")";
    }

    @Override
    public String visitComparison(Expr.Comparison expr) {
        return "(" + expr.operator.lexeme
             + " " + expr.left.accept(this)
             + " " + expr.right.accept(this) + ")";
    }

    @Override
    public String visitLogical(Expr.Logical expr) {
        return "(" + expr.operator.lexeme
             + " " + expr.left.accept(this)
             + " " + expr.right.accept(this) + ")";
    }

    @Override
    public String visitUnary(Expr.Unary expr) {
        return "(" + expr.operator.lexeme + " " + expr.right.accept(this) + ")";
    }

    @Override
    public String visitLiteral(Expr.Literal expr) {
        if (expr.value == null)              return "null";
        if (expr.value instanceof String s)  return "\"" + s + "\"";
        return expr.value.toString();
    }

    @Override
    public String visitVariable(Expr.Variable expr) {
        return expr.name.lexeme;
    }

    @Override
    public String visitGrouping(Expr.Grouping expr) {
        return "(group " + expr.expression.accept(this) + ")";
    }

    @Override
    public String visitCall(Expr.Call expr) {
        StringBuilder sb = new StringBuilder("(call ");
        sb.append(expr.callee.accept(this));
        for (Expr arg : expr.arguments) {
            sb.append(" ").append(arg.accept(this));
        }
        sb.append(")");
        return sb.toString();
    }
}
