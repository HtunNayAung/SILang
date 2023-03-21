package silang.interpreter;

import silang.Token;
import silang.TokenType;
import silang.ast.Expr;
import silang.ast.Stmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tree-walk interpreter for SIlang Version 0.1.
 *
 * <p>Evaluates an AST produced by {@link silang.parser.Parser} by visiting
 * each node, computing values for expressions, and executing side-effects
 * for statements.  The interpreter implements both {@link Expr.Visitor} and
 * {@link Stmt.Visitor} so it can be passed directly to {@code accept()}.
 *
 * <h2>Runtime type mapping</h2>
 * <table border="1">
 *   <tr><th>SIlang type</th><th>Java type</th></tr>
 *   <tr><td>int</td><td>{@link Integer}</td></tr>
 *   <tr><td>float</td><td>{@link Double}</td></tr>
 *   <tr><td>string</td><td>{@link String}</td></tr>
 *   <tr><td>boolean</td><td>{@link Boolean}</td></tr>
 *   <tr><td>null (future)</td><td>{@code null}</td></tr>
 * </table>
 *
 * <h2>Arithmetic rules</h2>
 * <ul>
 *   <li>int OP int  → int  (integer arithmetic)</li>
 *   <li>float OP anything-numeric → float (promotion)</li>
 *   <li>string + anything → string (concatenation, converts rhs)</li>
 *   <li>anything + string → string (concatenation, converts lhs)</li>
 *   <li>All other combinations → {@link RuntimeError} R003</li>
 * </ul>
 *
 * <h2>Built-in functions (v0.1)</h2>
 * <ul>
 *   <li>{@code out(value)} — prints value to stdout, followed by newline</li>
 * </ul>
 *
 * <h2>Forward-compatibility</h2>
 * <p>The {@link #builtins} map already provides the extension point for all
 * future standard-library functions.  Adding a built-in in Version 0.3 is a
 * single map.put() call — no switch/if changes needed.  User-defined
 * functions will be registered in the same map (or a layered environment)
 * when they are introduced.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Interpreter interpreter = new Interpreter(fileName, sourceLines);
 * interpreter.interpret(statements);   // runs the program
 * }</pre>
 */
public final class Interpreter
        implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    // ------------------------------------------------------------------ //
    //  State                                                             //
    // ------------------------------------------------------------------ //

    /** Global variable environment — the single scope for SIlang v0.1. */
    private final Environment globals;

    /**
     * The currently active scope.  In v0.1 this is always {@link #globals}.
     * When function calls and blocks are introduced (v0.2/v0.3), this field
     * will be replaced with a new child {@link Environment} on entry and
     * restored on exit.
     */
    private Environment environment;

    /**
     * Built-in functions registered by name.
     * Looked up in {@link #visitCall} before checking the variable environment.
     */
    private final Map<String, SiCallable> builtins;

    /** Source file name — used in runtime error diagnostics. */
    private final String fileName;

    /** Source lines split on {@code '\n'} — used for error snippets. */
    private final List<String> sourceLines;

    // ------------------------------------------------------------------ //
    //  Construction                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Creates a new interpreter.
     *
     * @param fileName    the source file name (for error messages)
     * @param sourceLines the raw source lines (for error snippets)
     */
    public Interpreter(String fileName, List<String> sourceLines) {
        this.fileName    = (fileName != null) ? fileName : "<unknown>";
        this.sourceLines = (sourceLines != null) ? sourceLines : List.of();
        this.globals     = new Environment();
        this.environment = globals;
        this.builtins    = registerBuiltins();
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Executes a full program represented as a list of top-level statements.
     *
     * <p>Execution stops immediately on the first {@link RuntimeError};
     * the error is formatted and re-thrown so {@code Main} can print it.
     *
     * @param statements the parsed program statements
     * @throws RuntimeError if execution encounters a runtime fault
     */
    public void interpret(List<Stmt> statements) {
        for (Stmt statement : statements) {
            execute(statement);
        }
    }

    /**
     * Executes a single statement.
     *
     * @param stmt the statement to execute
     */
    public void execute(Stmt stmt) {
        stmt.accept(this);
    }

    /**
     * Evaluates a single expression and returns its runtime value.
     *
     * @param expr the expression to evaluate
     * @return the Java object representing the SIlang value
     */
    public Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    /**
     * Returns the current global environment (for REPL / test inspection).
     */
    public Environment getGlobals() {
        return globals;
    }

    // ================================================================== //
    //  Stmt.Visitor implementations                                      //
    // ================================================================== //

    /**
     * Executes a variable declaration: evaluates the initializer and binds
     * the result to the variable name in the current scope.
     *
     * <pre>
     *   var x = 5 + 3
     *   → environment.define("x", 8)
     * </pre>
     */
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = evaluate(stmt.initializer);
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    /**
     * Executes an expression statement by evaluating the expression and
     * discarding the result (side-effects only — e.g. {@code out("hi")}).
     */
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    // ================================================================== //
    //  Expr.Visitor implementations                                      //
    // ================================================================== //

    /**
     * Returns the literal value directly — it was already parsed by the lexer
     * into the correct Java type ({@link Integer}, {@link Double},
     * {@link String}, {@link Boolean}, or {@code null}).
     */
    @Override
    public Object visitLiteral(Expr.Literal expr) {
        return expr.value;
    }

    /**
     * Looks up the variable name in the current environment chain and returns
     * its stored value.
     *
     * @throws RuntimeError R004 if the variable has not been declared
     */
    @Override
    public Object visitVariable(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    /**
     * Evaluates the inner expression — the grouping node exists only to
     * express explicit parenthesisation; at runtime it is transparent.
     */
    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    /**
     * Evaluates a unary expression.
     *
     * <p>In SIlang v0.1 only {@code -} is supported:
     * <ul>
     *   <li>{@code -int}   → {@link Integer} negation</li>
     *   <li>{@code -float} → {@link Double} negation</li>
     *   <li>anything else  → {@link RuntimeError} R002</li>
     * </ul>
     *
     * <p>Future versions will add {@code !boolean} (logical NOT).
     */
    @Override
    public Object visitUnary(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        if (expr.operator.type == TokenType.MINUS) {
            if (right instanceof Integer i) return -i;
            if (right instanceof Double  d) return -d;
            throw RuntimeError.unaryTypeMismatch(expr.operator, right);
        }

        // Future: BANG (logical NOT) — unreachable in v0.1
        throw new IllegalStateException("Unhandled unary operator: " + expr.operator.lexeme);
    }

    /**
     * Evaluates a binary expression with full type rules.
     *
     * <h3>Arithmetic ({@code +}, {@code -}, {@code *}, {@code /})</h3>
     * <table border="1">
     *   <tr><th>Left</th><th>Op</th><th>Right</th><th>Result</th></tr>
     *   <tr><td>int</td>   <td>any</td><td>int</td>   <td>int</td></tr>
     *   <tr><td>float</td> <td>any</td><td>numeric</td><td>float</td></tr>
     *   <tr><td>numeric</td><td>any</td><td>float</td><td>float</td></tr>
     *   <tr><td>string</td><td>+</td>  <td>any</td>   <td>string (concat)</td></tr>
     *   <tr><td>any</td>   <td>+</td>  <td>string</td><td>string (concat)</td></tr>
     * </table>
     *
     * <h3>Division</h3>
     * <ul>
     *   <li>int / int → int (truncated toward zero)</li>
     *   <li>int / 0   → {@link RuntimeError} R001</li>
     *   <li>float / 0.0 → runtime error (not IEEE infinity)</li>
     * </ul>
     */
    @Override
    public Object visitBinary(Expr.Binary expr) {
        Object left  = evaluate(expr.left);
        Object right = evaluate(expr.right);
        Token  op    = expr.operator;

        return switch (op.type) {

            // ── Addition / string concatenation ──────────────────────────
            case PLUS -> evalPlus(op, left, right);

            // ── Subtraction ──────────────────────────────────────────────
            case MINUS -> {
                Number[] nums = requireNumeric(op, left, right);
                yield (nums[0] instanceof Double || nums[1] instanceof Double)
                    ? toDouble(nums[0]) - toDouble(nums[1])
                    : (Integer) nums[0] - (Integer) nums[1];
            }

            // ── Multiplication ───────────────────────────────────────────
            case STAR -> {
                Number[] nums = requireNumeric(op, left, right);
                yield (nums[0] instanceof Double || nums[1] instanceof Double)
                    ? toDouble(nums[0]) * toDouble(nums[1])
                    : (Integer) nums[0] * (Integer) nums[1];
            }

            // ── Division ─────────────────────────────────────────────────
            case SLASH -> evalDivision(op, left, right);

            default -> throw new IllegalStateException(
                "Unhandled binary operator: " + op.lexeme);
        };
    }

    /**
     * Evaluates a function call expression.
     *
     * <p>Call resolution order:
     * <ol>
     *   <li>Check {@link #builtins} map by callee name (handles {@code out}).</li>
     *   <li>Future v0.3: look up user-defined functions in the environment.</li>
     * </ol>
     *
     * <p>The callee is expected to be a {@link Expr.Variable} in v0.1.
     * Future versions will support arbitrary callee expressions
     * (method calls, first-class functions).
     */
    @Override
    public Object visitCall(Expr.Call expr) {
        // Resolve the callee to a name (v0.1: always a Variable)
        String calleeName = resolveCalleeName(expr.callee, expr.paren);

        // Look up in built-ins first
        SiCallable callable = builtins.get(calleeName);
        if (callable == null) {
            // Future v0.3: check environment for user-defined functions
            throw RuntimeError.unknownFunction(
                calleeName(expr.callee, expr.paren));
        }

        // Evaluate all arguments left-to-right
        List<Object> arguments = new ArrayList<>();
        for (Expr arg : expr.arguments) {
            arguments.add(evaluate(arg));
        }

        // Arity check (skip for variadic callables with arity == -1)
        if (callable.arity() != -1 && arguments.size() != callable.arity()) {
            throw RuntimeError.wrongArity(
                expr.paren, calleeName, callable.arity(), arguments.size());
        }

        return callable.call(this, arguments);
    }

    // ================================================================== //
    //  Built-in function registry                                        //
    // ================================================================== //

    /**
     * Registers all Version 0.1 built-in functions and returns the map.
     *
     * <p>To add a new built-in in a future version, add a single
     * {@code result.put(...)} entry here — nothing else changes.
     */
    private Map<String, SiCallable> registerBuiltins() {
        return Map.of(

            // ── out(value) ───────────────────────────────────────────────
            //
            //   Prints the string representation of its single argument to
            //   stdout followed by a newline.
            //
            //   Supported conversions:
            //     int     → decimal digits ("42")
            //     float   → decimal notation ("3.14")
            //     boolean → "true" or "false"
            //     string  → printed as-is (no surrounding quotes)
            //     null    → "null"
            "out", new SiCallable() {
                @Override public int arity() { return 1; }

                @Override
                public Object call(Interpreter interpreter, List<Object> args) {
                    System.out.println(stringify(args.get(0)));
                    return null;
                }

                @Override public String displayName() { return "out"; }
            }

            // Future built-ins are added here:
            // "outErr",  ...   — stderr output (v0.2)
            // "clock",   ...   — wall-clock time (v0.2)
            // "parseInt", ...  — string → int conversion (v0.4)
        );
    }

    // ================================================================== //
    //  Arithmetic helpers                                                //
    // ================================================================== //

    /**
     * Evaluates the {@code +} operator:
     * <ul>
     *   <li>string + anything → concatenation (rhs converted to string)</li>
     *   <li>anything + string → concatenation (lhs converted to string)</li>
     *   <li>int + int → int addition</li>
     *   <li>numeric + numeric (at least one float) → float addition</li>
     *   <li>otherwise → {@link RuntimeError} R003</li>
     * </ul>
     */
    private Object evalPlus(Token op, Object left, Object right) {
        // String concatenation — either side being a string triggers it
        if (left instanceof String s) {
            return s + stringify(right);
        }
        if (right instanceof String s) {
            return stringify(left) + s;
        }

        // Numeric addition
        if (isNumeric(left) && isNumeric(right)) {
            if (left instanceof Double || right instanceof Double) {
                return toDouble(left) + toDouble(right);
            }
            return (Integer) left + (Integer) right;
        }

        throw RuntimeError.binaryTypeMismatch(op, left, right);
    }

    /**
     * Evaluates the {@code /} division operator with zero-division guard.
     */
    private Object evalDivision(Token op, Object left, Object right) {
        Number[] nums = requireNumeric(op, left, right);

        boolean floatResult = nums[0] instanceof Double || nums[1] instanceof Double;

        if (floatResult) {
            double divisor = toDouble(nums[1]);
            if (divisor == 0.0) throw RuntimeError.divisionByZero(op);
            return toDouble(nums[0]) / divisor;
        } else {
            int divisor = (Integer) nums[1];
            if (divisor == 0) throw RuntimeError.divisionByZero(op);
            return (Integer) nums[0] / divisor;  // truncated integer division
        }
    }

    /**
     * Validates that both operands are numeric ({@link Integer} or
     * {@link Double}), returning them as a two-element array.
     *
     * @throws RuntimeError R003 if either operand is not numeric
     */
    private Number[] requireNumeric(Token op, Object left, Object right) {
        if (isNumeric(left) && isNumeric(right)) {
            return new Number[]{ (Number) left, (Number) right };
        }
        throw RuntimeError.binaryTypeMismatch(op, left, right);
    }

    // ================================================================== //
    //  Value helpers                                                     //
    // ================================================================== //

    /**
     * Converts a SIlang runtime value to its canonical string representation.
     *
     * <ul>
     *   <li>{@code null}      → {@code "null"}</li>
     *   <li>{@link Boolean}   → {@code "true"} or {@code "false"}</li>
     *   <li>{@link Integer}   → decimal digits</li>
     *   <li>{@link Double}    → decimal notation, trailing {@code ".0} stripped
     *                           when the value is a whole number</li>
     *   <li>{@link String}    → the string itself (no surrounding quotes)</li>
     * </ul>
     */
    static String stringify(Object value) {
        if (value == null)           return "null";
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof Double d) {
            String text = d.toString();
            // Remove redundant ".0" suffix for whole-number floats
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        return value.toString();
    }

    /**
     * Returns {@code true} if {@code value} is an {@link Integer} or
     * {@link Double}.
     */
    private static boolean isNumeric(Object value) {
        return value instanceof Integer || value instanceof Double;
    }

    /**
     * Converts a numeric value to {@link Double}.
     * Precondition: {@code value} is already known to be numeric.
     */
    private static double toDouble(Object value) {
        if (value instanceof Double d) return d;
        return ((Integer) value).doubleValue();
    }

    // ================================================================== //
    //  Call resolution helpers                                           //
    // ================================================================== //

    /**
     * Resolves a callee expression to a string name for built-in lookup.
     *
     * <p>In v0.1 the callee must be a {@link Expr.Variable}; any other
     * callee expression is a runtime error.  Future versions (v0.3+) will
     * allow arbitrary callees by evaluating the expression and checking
     * if the resulting value implements {@link SiCallable}.
     *
     * @throws RuntimeError R006 if the callee is not a variable name
     */
    private String resolveCalleeName(Expr callee, Token paren) {
        if (callee instanceof Expr.Variable v) {
            return v.name.lexeme;
        }
        // Future: evaluate callee and check instanceof SiCallable
        throw RuntimeError.unknownFunction(paren);
    }

    /**
     * Returns the name token from a callee expression (for error messages).
     */
    private Token calleeName(Expr callee, Token fallback) {
        if (callee instanceof Expr.Variable v) return v.name;
        return fallback;
    }

    // ================================================================== //
    //  Error formatting (called from Main)                               //
    // ================================================================== //

    /**
     * Formats a {@link RuntimeError}'s diagnostic using this interpreter's
     * file name and source lines.
     *
     * @param error the runtime error to format
     * @return the formatted multi-line diagnostic string
     */
    public String formatError(RuntimeError error) {
        int    line      = error.getToken().line;
        String srcLine   = (line >= 1 && line <= sourceLines.size())
            ? sourceLines.get(line - 1) : "";
        return error.formatDiagnostic(fileName, srcLine);
    }
}
