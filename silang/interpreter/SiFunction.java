package silang.interpreter;

import silang.ast.Stmt;

import java.util.List;

/**
 * Runtime representation of a user-defined SILang function.
 *
 * <p>
 * Created when the interpreter executes a {@link Stmt.Function} declaration.
 * Stored as a value in the {@link Environment} under the function's name,
 * so it can be retrieved and called like any other {@link SiCallable}.
 *
 * <h2>Call semantics</h2>
 * <ol>
 * <li>A new child {@link Environment} is created, with {@link #closure}
 * as its parent. This child is the function's local scope.</li>
 * <li>Each parameter is bound in the child scope using the matching
 * argument value.</li>
 * <li>The body {@link Stmt.Block} is executed inside that child scope
 * via {@link Interpreter#executeBlock}.</li>
 * <li>If a {@link ReturnSignal} is thrown, it is caught here and its
 * {@link ReturnSignal#value} is returned to the caller.</li>
 * <li>If execution reaches the end of the body without a {@code return},
 * the function returns {@code null}.</li>
 * </ol>
 *
 * <h2>Closure</h2>
 * <p>
 * The {@link #closure} field captures the {@link Environment} that was
 * active at the point of the {@code fn} declaration — not the call site.
 * This gives SILang lexical (not dynamic) scoping, which is the expected
 * behaviour for nested functions and future lambda expressions.
 */
public final class SiFunction implements SiCallable {

    /** The AST node this function was created from. */
    private final Stmt.Function declaration;

    /**
     * The scope captured at declaration time.
     * All free variables in the body are resolved against this environment.
     */
    private final Environment closure;

    /**
     * Constructs a new {@code SiFunction}.
     *
     * @param declaration the parsed function declaration
     * @param closure     the environment active when the declaration was executed
     */
    public SiFunction(Stmt.Function declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    // ------------------------------------------------------------------ //
    // SiCallable //
    // ------------------------------------------------------------------ //

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public String displayName() {
        return declaration.name.lexeme;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // Create a fresh local scope rooted at the closure
        Environment localScope = new Environment(closure);

        // Bind each parameter to its argument
        for (int i = 0; i < declaration.params.size(); i++) {
            localScope.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        // Execute the body; catch any return signal
        try {
            interpreter.executeBlock(declaration.body.statements, localScope);
        } catch (ReturnSignal signal) {
            return signal.value;
        }

        // Implicit return null if body completes without a return statement
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
