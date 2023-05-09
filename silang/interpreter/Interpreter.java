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
 * Tree-walk interpreter for SIlang Version 0.2.
 *
 * <p>Evaluates the AST produced by the parser by visiting each node,
 * computing values for expressions, and executing side-effects for statements.
 *
 * <h2>New in v0.2</h2>
 * <ul>
 *   <li>{@link Stmt.Block}      — nested scope, new child {@link Environment}</li>
 *   <li>{@link Stmt.If}         — conditional, condition must be boolean</li>
 *   <li>{@link Stmt.While}      — loop, condition must be boolean</li>
 *   <li>{@link Stmt.Assign}     — re-assignment to an existing variable</li>
 *   <li>{@link Expr.Comparison} — ==, !=, <, <=, >, >= → boolean result</li>
 *   <li>{@link Expr.Logical}    — && / || with short-circuit evaluation</li>
 *   <li>{@link Expr.Unary}      — extended with ! (logical NOT)</li>
 * </ul>
 *
 * <h2>Condition type rule</h2>
 * <p>Both {@code if} and {@code while} conditions must evaluate to
 * {@code boolean}.  Any other type throws {@link RuntimeError} R008.
 *
 * <h2>Block scoping rule</h2>
 * <p>Every {@code { }} block creates a new child {@link Environment}.
 * Variables declared inside a block are not visible outside it.
 * Re-assignment ({@link Stmt.Assign}) walks the scope chain and updates
 * the variable wherever it was originally declared.
 *
 * <h2>Short-circuit evaluation</h2>
 * <ul>
 *   <li>{@code &&} — if left is {@code false}, right is not evaluated</li>
 *   <li>{@code ||} — if left is {@code true},  right is not evaluated</li>
 * </ul>
 *
 * <h2>Runtime type mapping</h2>
 * <table border="1">
 *   <tr><th>SIlang</th><th>Java</th></tr>
 *   <tr><td>int</td>    <td>{@link Integer}</td></tr>
 *   <tr><td>float</td>  <td>{@link Double}</td></tr>
 *   <tr><td>string</td> <td>{@link String}</td></tr>
 *   <tr><td>boolean</td><td>{@link Boolean}</td></tr>
 * </table>
 */
public final class Interpreter
        implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    // ------------------------------------------------------------------ //
    //  State                                                             //
    // ------------------------------------------------------------------ //

    /** Global scope — survives for the lifetime of the program. */
    private final Environment globals;

    /**
     * The currently active scope.  Changes as blocks are entered and exited.
     * In v0.1-style flat programs this is always equal to {@link #globals}.
     */
    private Environment environment;

    /** Built-in functions registered by name. */
    private final Map<String, SiCallable> builtins;

    /**
     * Current call depth — incremented on function entry, decremented on exit.
     * Used to detect top-level {@code return} (R009).
     */
    private int callDepth = 0;

    /** Source file name — for diagnostic messages. */
    private final String fileName;

    /** Raw source lines — for diagnostic caret snippets. */
    private final List<String> sourceLines;

    // ------------------------------------------------------------------ //
    //  Construction                                                      //
    // ------------------------------------------------------------------ //

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

    /** Runs a complete program. */
    public void interpret(List<Stmt> statements) {
        for (Stmt statement : statements) execute(statement);
    }

    /** Executes one statement. */
    public void execute(Stmt stmt) { stmt.accept(this); }

    /** Evaluates one expression and returns its runtime value. */
    public Object evaluate(Expr expr) { return expr.accept(this); }

    /** Returns the global environment (for testing / REPL). */
    public Environment getGlobals() { return globals; }

    // ================================================================== //
    //  Stmt.Visitor                                                      //
    // ================================================================== //

    /** Declares a variable: {@code var x = expr}. */
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = evaluate(stmt.initializer);
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    /**
     * Re-assigns an existing variable: {@code x = expr}.
     *
     * <p>Calls {@link Environment#assign} which walks up the scope chain and
     * updates the variable wherever it was first declared.  Throws R005 if
     * the variable does not exist in any enclosing scope.
     */
    @Override
    public Void visitAssignStmt(Stmt.Assign stmt) {
        Object value = evaluate(stmt.value);
        environment.assign(stmt.name, value);
        return null;
    }

    /** Expression statement — evaluate for side-effects, discard result. */
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Block                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Executes a braced block in a new child scope.
     *
     * <p>The child {@link Environment} is created on entry and the active
     * environment is restored to the parent on exit — even if an exception
     * is thrown.  This guarantees scope isolation regardless of errors.
     */
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    /**
     * Executes a list of statements inside a given scope.
     *
     * <p>This is extracted from {@link #visitBlockStmt} so that future
     * function calls can pass in a pre-built closure environment.
     *
     * @param statements the statements to execute
     * @param scope      the scope in which to execute them
     */
    public void executeBlock(List<Stmt> statements, Environment scope) {
        Environment previous = this.environment;
        try {
            this.environment = scope;
            for (Stmt stmt : statements) execute(stmt);
        } finally {
            this.environment = previous;  // always restore, even on exception
        }
    }

    // ------------------------------------------------------------------ //
    //  If                                                                //
    // ------------------------------------------------------------------ //

    /**
     * Executes an if statement.
     *
     * <p>The condition is evaluated exactly once.  Its result must be
     * {@code boolean}; any other type throws {@link RuntimeError} R008.
     * Exactly one branch is executed (or none if the condition is false
     * and there is no else-branch).
     */
    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        Object condValue = evaluate(stmt.condition);
        boolean cond = requireBoolean(stmt.keyword, condValue);

        if (cond) {
            visitBlockStmt(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            visitBlockStmt(stmt.elseBranch);
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  While                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Executes a while loop.
     *
     * <p>The condition is re-evaluated before every iteration.  It must
     * be {@code boolean}; any other type throws {@link RuntimeError} R008.
     * The loop exits when the condition is {@code false}.
     *
     * <p>Future: {@code break} and {@code continue} will be implemented
     * as control-flow exceptions ({@code BreakSignal}, {@code ContinueSignal})
     * caught here, matching the standard interpreter pattern.
     */
    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (true) {
            Object condValue = evaluate(stmt.condition);
            boolean cond = requireBoolean(stmt.keyword, condValue);
            if (!cond) break;
            visitBlockStmt(stmt.body);
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Function declaration                                               //
    // ------------------------------------------------------------------ //

    /**
     * Defines a user function in the current scope.
     *
     * <p>Wraps the AST node in a {@link SiFunction} (capturing the current
     * environment as the closure) and binds it to the function's name.
     * This makes the function available for calls that appear after the
     * declaration — and also for recursive calls within the body, since the
     * binding is in the enclosing scope before any call begins.
     */
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        SiFunction function = new SiFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Return statement                                                   //
    // ------------------------------------------------------------------ //

    /**
     * Executes a return statement.
     *
     * <p>Evaluates the optional value expression, then throws a
     * {@link ReturnSignal} to unwind the call stack back to
     * {@link SiFunction#call}.  A bare {@code return} sends {@code null}.
     *
     * <p>A {@code return} at the top level (outside any function) throws
     * {@link RuntimeError} R009 instead.
     */
    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (callDepth == 0) {
            throw RuntimeError.returnOutsideFunction(stmt.keyword);
        }
        Object value = stmt.value != null ? evaluate(stmt.value) : null;
        throw new ReturnSignal(value);
    }

    // ================================================================== //
    //  Expr.Visitor                                                      //
    // ================================================================== //

    /** Literal — already a typed Java value; return as-is. */
    @Override
    public Object visitLiteral(Expr.Literal expr) { return expr.value; }

    /** Variable — look up in the current scope chain. */
    @Override
    public Object visitVariable(Expr.Variable expr) { return environment.get(expr.name); }

    /** Grouping — transparent at runtime. */
    @Override
    public Object visitGrouping(Expr.Grouping expr) { return evaluate(expr.expression); }

    // ------------------------------------------------------------------ //
    //  Unary                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Evaluates a unary expression.
     *
     * <ul>
     *   <li>{@code -}  negation: int/float only → R002 for other types</li>
     *   <li>{@code !}  logical NOT: boolean only → R002 for other types</li>
     * </ul>
     */
    @Override
    public Object visitUnary(Expr.Unary expr) {
        Object operand = evaluate(expr.right);

        return switch (expr.operator.type) {
            case MINUS -> {
                TypeSystem.checkUnaryNegate(expr.operator, operand);
                yield TypeSystem.computeNegate(operand);
            }
            case BANG -> {
                if (!(operand instanceof Boolean)) {
                    throw RuntimeError.cannotNot(expr.operator, operand);
                }
                yield !(Boolean) operand;
            }
            default -> throw new IllegalStateException(
                "Unhandled unary operator: " + expr.operator.lexeme);
        };
    }

    // ------------------------------------------------------------------ //
    //  Binary (arithmetic)                                               //
    // ------------------------------------------------------------------ //

    /**
     * Evaluates an arithmetic binary expression.
     * All type logic delegated to {@link TypeSystem}.
     *
     * @throws RuntimeError R001 for division by zero
     * @throws RuntimeError R003 for type mismatches
     */
    @Override
    public Object visitBinary(Expr.Binary expr) {
        Object left  = evaluate(expr.left);
        Object right = evaluate(expr.right);
        TypeSystem.TypeCheckResult result = TypeSystem.checkBinary(expr.operator, left, right);
        return TypeSystem.compute(expr.operator, left, right, result);
    }

    // ------------------------------------------------------------------ //
    //  Comparison                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Evaluates a comparison expression.
     *
     * <p>Comparison operators ({@code ==}, {@code !=}, {@code <}, etc.)
     * always produce a {@code boolean}.  The type rules are defined in
     * {@link TypeSystem#BINARY_RULES}: only numeric × numeric and
     * same-type comparisons are valid.
     *
     * @throws RuntimeError R003 if the operand types are incompatible
     */
    @Override
    public Object visitComparison(Expr.Comparison expr) {
        Object left  = evaluate(expr.left);
        Object right = evaluate(expr.right);
        TypeSystem.TypeCheckResult result = TypeSystem.checkBinary(expr.operator, left, right);
        return TypeSystem.compute(expr.operator, left, right, result);
    }

    // ------------------------------------------------------------------ //
    //  Logical (short-circuit)                                           //
    // ------------------------------------------------------------------ //

    /**
     * Evaluates a short-circuit logical expression ({@code &&} / {@code ||}).
     *
     * <p>Both operands must be {@code boolean}; the left operand is checked
     * immediately.  If short-circuit applies, the right operand is never
     * evaluated (its side-effects are skipped).
     *
     * @throws RuntimeError R002 if either operand is not boolean
     */
    @Override
    public Object visitLogical(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        // Left must be boolean regardless of short-circuit
        if (!(left instanceof Boolean)) {
            throw RuntimeError.cannotNot(expr.operator, left);   // reuse R002
        }

        boolean lv = (Boolean) left;

        // Short-circuit: && → skip right if false; || → skip right if true
        if (expr.operator.type == TokenType.AND && !lv) return false;
        if (expr.operator.type == TokenType.OR  &&  lv) return true;

        // Evaluate right and type-check it
        Object right = evaluate(expr.right);
        if (!(right instanceof Boolean)) {
            throw RuntimeError.cannotNot(expr.operator, right);
        }

        return (Boolean) right;
    }

    // ------------------------------------------------------------------ //
    //  Call                                                              //
    // ------------------------------------------------------------------ //

    /**
     * Evaluates a function call.
     *
     * @throws RuntimeError R006 if the function name is unknown
     * @throws RuntimeError R007 if the argument count is wrong
     */
    @Override
    public Object visitCall(Expr.Call expr) {
        String calleeName = resolveCalleeName(expr.callee, expr.paren);

        // Look up in builtins first, then environment (user-defined functions)
        SiCallable callable = builtins.get(calleeName);
        if (callable == null) {
            Object val = null;
            try { val = environment.get(calleeToken(expr.callee, expr.paren)); }
            catch (RuntimeError ignored) {}
            if (val instanceof SiCallable sc) callable = sc;
        }
        if (callable == null) {
            throw RuntimeError.unknownFunction(calleeToken(expr.callee, expr.paren));
        }

        // Evaluate arguments
        List<Object> args = new ArrayList<>(expr.arguments.size());
        for (Expr arg : expr.arguments) args.add(evaluate(arg));

        // Arity check
        int expected = callable.arity();
        if (expected != -1 && args.size() != expected) {
            throw RuntimeError.wrongArity(expr.paren, calleeName, expected, args.size());
        }

        // Track call depth so return-outside-function can be detected
        callDepth++;
        try {
            return callable.call(this, args);
        } finally {
            callDepth--;
        }
    }

    // ================================================================== //
    //  Built-in function registry                                        //
    // ================================================================== //

    private Map<String, SiCallable> registerBuiltins() {
        Map<String, SiCallable> map = new HashMap<>();

        // out(value) — print to stdout WITHOUT a trailing newline
        map.put("out", new SiCallable() {
            @Override public int    arity()       { return 1; }
            @Override public String displayName() { return "out"; }

            @Override
            public Object call(Interpreter interpreter, List<Object> args) {
                System.out.print(Stringify.of(args.get(0)));
                return null;
            }
        });

        // outn(value) — print to stdout WITH a trailing newline
        map.put("outn", new SiCallable() {
            @Override public int    arity()       { return 1; }
            @Override public String displayName() { return "outn"; }

            @Override
            public Object call(Interpreter interpreter, List<Object> args) {
                System.out.println(Stringify.of(args.get(0)));
                return null;
            }
        });

        return map;
    }

    // ================================================================== //
    //  Condition type enforcement                                        //
    // ================================================================== //

    /**
     * Asserts that a condition value is boolean; returns it if so.
     *
     * @param keyword  the {@code if} or {@code while} token (for error location)
     * @param value    the evaluated condition
     * @return the boolean value
     * @throws RuntimeError R008 if {@code value} is not boolean
     */
    private boolean requireBoolean(Token keyword, Object value) {
        if (value instanceof Boolean b) return b;
        throw RuntimeError.nonBooleanCondition(keyword, value);
    }

    // ================================================================== //
    //  Call resolution helpers                                           //
    // ================================================================== //

    private String resolveCalleeName(Expr callee, Token fallback) {
        if (callee instanceof Expr.Variable v) return v.name.lexeme;
        throw RuntimeError.unknownFunction(fallback);
    }

    private Token calleeToken(Expr callee, Token fallback) {
        if (callee instanceof Expr.Variable v) return v.name;
        return fallback;
    }

    // ================================================================== //
    //  Error formatting                                                  //
    // ================================================================== //

    /** Formats a {@link RuntimeError} with source-line context. */
    public String formatError(RuntimeError error) {
        int    line    = error.getToken().line;
        String srcLine = (line >= 1 && line <= sourceLines.size())
            ? sourceLines.get(line - 1) : "";
        return error.formatDiagnostic(fileName, srcLine);
    }
}
