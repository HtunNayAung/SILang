package silang.interpreter;

import silang.Token;
import silang.TokenType;
import silang.ast.Expr;
import silang.ast.Stmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree-walk interpreter for SIlang Version 0.1.
 *
 * <p>Evaluates an AST produced by {@link silang.parser.Parser} by visiting
 * each node, computing values for expressions, and executing side-effects
 * for statements.
 *
 * <h2>Architecture</h2>
 * <p>All operator type-checking and value-computation logic lives in
 * {@link TypeSystem}, not here.  The interpreter's visitor methods are
 * intentionally thin — they evaluate sub-expressions, then delegate to
 * {@code TypeSystem} for the type decision and to perform the arithmetic.
 * This means:
 * <ul>
 *   <li>Zero scattered {@code instanceof} chains in operator methods.</li>
 *   <li>Adding a new operator (v0.2+) requires one entry in
 *       {@link TypeSystem#BINARY_RULES} and one line in
 *       {@link #visitBinary}; nothing else changes.</li>
 *   <li>Type rules are testable in complete isolation from the AST walker.</li>
 * </ul>
 *
 * <h2>Runtime type mapping</h2>
 * <table border="1">
 *   <tr><th>SIlang type</th><th>Java type</th></tr>
 *   <tr><td>int</td>    <td>{@link Integer}</td></tr>
 *   <tr><td>float</td>  <td>{@link Double}</td></tr>
 *   <tr><td>string</td> <td>{@link String}</td></tr>
 *   <tr><td>boolean</td><td>{@link Boolean}</td></tr>
 *   <tr><td>null (future)</td><td>{@code null}</td></tr>
 * </table>
 *
 * <h2>Built-in functions (v0.1)</h2>
 * <ul>
 *   <li>{@code out(value)} — prints {@link Stringify#of(Object)} to stdout + newline</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Interpreter interp = new Interpreter(fileName, sourceLines);
 * interp.interpret(statements);
 * }</pre>
 */
public final class Interpreter
        implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    // ------------------------------------------------------------------ //
    //  State                                                             //
    // ------------------------------------------------------------------ //

    /** Global scope — the only scope in SIlang v0.1. */
    private final Environment globals;

    /**
     * Active scope.  In v0.1 always equal to {@link #globals}.
     * Will diverge when blocks (v0.2) and functions (v0.3) are added.
     */
    private Environment environment;

    /**
     * Registered built-in callables, keyed by name.
     * Extended by adding a single {@code put()} in {@link #registerBuiltins()}.
     */
    private final Map<String, SiCallable> builtins;

    /** Source file name for diagnostic messages. */
    private final String fileName;

    /** Raw source lines for diagnostic caret snippets. */
    private final List<String> sourceLines;

    // ------------------------------------------------------------------ //
    //  Construction                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Creates an interpreter ready to execute a SIlang v0.1 program.
     *
     * @param fileName    name of the source file (for error messages)
     * @param sourceLines raw source split on {@code '\n'} (for error snippets)
     */
    public Interpreter(String fileName, List<String> sourceLines) {
        this.fileName    = (fileName    != null) ? fileName    : "<unknown>";
        this.sourceLines = (sourceLines != null) ? sourceLines : List.of();
        this.globals     = new Environment();
        this.environment = globals;
        this.builtins    = registerBuiltins();
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Runs a complete program.  Throws {@link RuntimeError} on the first
     * semantic fault — the error is not caught here; callers (Main) format
     * and display it.
     *
     * @param statements the top-level statement list from the parser
     */
    public void interpret(List<Stmt> statements) {
        for (Stmt statement : statements) {
            execute(statement);
        }
    }

    /** Executes one statement. */
    public void execute(Stmt stmt) {
        stmt.accept(this);
    }

    /** Evaluates one expression and returns its runtime value. */
    public Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    /** Returns the global environment (for testing / REPL inspection). */
    public Environment getGlobals() {
        return globals;
    }

    // ================================================================== //
    //  Stmt.Visitor                                                      //
    // ================================================================== //

    /**
     * Declares a variable: evaluates the initializer and stores the result.
     *
     * <pre>
     *   var x = 5 + 3   →  environment.define("x", 8)
     * </pre>
     */
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = evaluate(stmt.initializer);
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    /**
     * Evaluates an expression for its side-effects; discards the value.
     * In v0.1 this is almost always an {@code out()} call.
     */
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    // ================================================================== //
    //  Expr.Visitor                                                      //
    // ================================================================== //

    /**
     * Literal — already a Java value; return as-is.
     * ({@link Integer}, {@link Double}, {@link String}, {@link Boolean}, or {@code null})
     */
    @Override
    public Object visitLiteral(Expr.Literal expr) {
        return expr.value;
    }

    /**
     * Variable — look up in the current scope chain.
     *
     * @throws RuntimeError R004 if the name has not been declared
     */
    @Override
    public Object visitVariable(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    /**
     * Grouping — transparent at runtime; just evaluate the inner expression.
     */
    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    // ------------------------------------------------------------------ //
    //  Unary                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Evaluates a unary expression.
     *
     * <h3>Type rules</h3>
     * <table border="1">
     *   <tr><th>Operator</th><th>Operand</th><th>Result</th></tr>
     *   <tr><td>{@code -}</td><td>int</td>    <td>int</td></tr>
     *   <tr><td>{@code -}</td><td>float</td>  <td>float</td></tr>
     *   <tr><td>{@code -}</td><td>string</td> <td>→ R002</td></tr>
     *   <tr><td>{@code -}</td><td>boolean</td><td>→ R002</td></tr>
     * </table>
     *
     * <p>Future: {@code !boolean} (v0.2) will add a {@code BANG} branch here.
     *
     * @throws RuntimeError R002 if unary {@code -} is applied to a non-numeric value
     */
    @Override
    public Object visitUnary(Expr.Unary expr) {
        Object operand = evaluate(expr.right);

        return switch (expr.operator.type) {
            case MINUS -> {
                // Delegate type check to TypeSystem — throws R002 if invalid
                TypeSystem.checkUnaryNegate(expr.operator, operand);
                yield TypeSystem.computeNegate(operand);
            }

            // Future v0.2: BANG (logical NOT)
            // case BANG -> {
            //     TypeSystem.checkUnaryNot(expr.operator, operand);
            //     yield !(Boolean) operand;
            // }

            default -> throw new IllegalStateException(
                "Unhandled unary operator: " + expr.operator.lexeme);
        };
    }

    // ------------------------------------------------------------------ //
    //  Binary                                                            //
    // ------------------------------------------------------------------ //

    /**
     * Evaluates a binary expression using the full operator type matrix.
     *
     * <h3>Operator rules (v0.1)</h3>
     * <p>All rules are defined in {@link TypeSystem#BINARY_RULES}.  This
     * method contains no type logic itself — it:
     * <ol>
     *   <li>Evaluates both operands.</li>
     *   <li>Calls {@link TypeSystem#checkBinary} to validate types and obtain
     *       the {@link TypeSystem.TypeCheckResult} describing what to compute.</li>
     *   <li>Calls {@link TypeSystem#compute} to perform the actual arithmetic.</li>
     * </ol>
     *
     * <p>This two-step separation means the type check and the computation
     * are never accidentally skipped or reordered.
     *
     * @throws RuntimeError R001 for division by zero
     * @throws RuntimeError R003 for incompatible operand types
     */
    @Override
    public Object visitBinary(Expr.Binary expr) {
        // Evaluate both sides fully before type-checking (left-to-right order)
        Object left  = evaluate(expr.left);
        Object right = evaluate(expr.right);

        // Validate types and obtain computation descriptor — throws R003 on mismatch
        TypeSystem.TypeCheckResult typeResult = TypeSystem.checkBinary(expr.operator, left, right);

        // Perform the computation (may throw R001 for division by zero)
        return TypeSystem.compute(expr.operator, left, right, typeResult);
    }

    // ------------------------------------------------------------------ //
    //  Call                                                              //
    // ------------------------------------------------------------------ //

    /**
     * Evaluates a function call expression.
     *
     * <h3>Call resolution order (v0.1)</h3>
     * <ol>
     *   <li>Resolve the callee to a string name (must be a {@link Expr.Variable}).</li>
     *   <li>Look up in {@link #builtins} map (handles {@code out}).</li>
     *   <li>Future v0.3: look up in environment for user-defined functions.</li>
     * </ol>
     *
     * @throws RuntimeError R006 if the callee name is not a known function
     * @throws RuntimeError R007 if the argument count does not match the arity
     */
    @Override
    public Object visitCall(Expr.Call expr) {
        String calleeName = resolveCalleeName(expr.callee, expr.paren);

        SiCallable callable = builtins.get(calleeName);
        if (callable == null) {
            // Future v0.3: check environment for SiCallable values
            throw RuntimeError.unknownFunction(calleeToken(expr.callee, expr.paren));
        }

        // Evaluate all arguments left-to-right before arity check
        List<Object> args = new ArrayList<>(expr.arguments.size());
        for (Expr arg : expr.arguments) {
            args.add(evaluate(arg));
        }

        // Arity check (variadic callables have arity() == -1)
        int expected = callable.arity();
        if (expected != -1 && args.size() != expected) {
            throw RuntimeError.wrongArity(expr.paren, calleeName, expected, args.size());
        }

        return callable.call(this, args);
    }

    // ================================================================== //
    //  Built-in function registry                                        //
    // ================================================================== //

    /**
     * Registers all Version 0.1 built-ins.
     *
     * <p>To add a new built-in in a future version, add one entry here.
     * The {@link #visitCall} dispatch and arity checking require no changes.
     */
    private Map<String, SiCallable> registerBuiltins() {
        Map<String, SiCallable> map = new HashMap<>();

        // ── out(value) ────────────────────────────────────────────────────
        //   Prints the canonical string form of its single argument to stdout,
        //   followed by a newline.  All type conversions are handled by
        //   {@link Stringify#of} — no special-casing in the built-in itself.
        map.put("out", new SiCallable() {
            @Override public int    arity()       { return 1; }
            @Override public String displayName() { return "out"; }

            @Override
            public Object call(Interpreter interpreter, List<Object> args) {
                System.out.println(Stringify.of(args.get(0)));
                return null;
            }
        });

        // ── Future built-ins ──────────────────────────────────────────────
        // map.put("outErr",   ...);  // stderr output (v0.2)
        // map.put("clock",    ...);  // milliseconds since epoch (v0.2)
        // map.put("parseInt", ...);  // string → int (v0.4)
        // map.put("str",      ...);  // any → string (v0.4)

        return map;
    }

    // ================================================================== //
    //  Call resolution helpers                                           //
    // ================================================================== //

    /**
     * Resolves the callee expression to its string name.
     *
     * <p>In v0.1 the callee must be a {@link Expr.Variable}.  Future v0.3+
     * will extend this to evaluate arbitrary callee expressions and check
     * whether the resulting value implements {@link SiCallable}.
     *
     * @throws RuntimeError R006 if the callee is not an identifier
     */
    private String resolveCalleeName(Expr callee, Token fallback) {
        if (callee instanceof Expr.Variable v) return v.name.lexeme;
        throw RuntimeError.unknownFunction(fallback);
    }

    /** Returns the identifier token from a Variable callee, or the fallback. */
    private Token calleeToken(Expr callee, Token fallback) {
        if (callee instanceof Expr.Variable v) return v.name;
        return fallback;
    }

    // ================================================================== //
    //  Error formatting (called from Main)                               //
    // ================================================================== //

    /**
     * Formats a {@link RuntimeError} diagnostic using this interpreter's
     * file name and source lines.
     *
     * @param error the error to format
     * @return the complete multi-line diagnostic string
     */
    public String formatError(RuntimeError error) {
        int    line    = error.getToken().line;
        String srcLine = (line >= 1 && line <= sourceLines.size())
            ? sourceLines.get(line - 1)
            : "";
        return error.formatDiagnostic(fileName, srcLine);
    }
}
