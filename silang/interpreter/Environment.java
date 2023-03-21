package silang.interpreter;

import silang.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * A runtime variable scope for SIlang programs.
 *
 * <p>In Version 0.1 there is a single, flat global scope — every variable
 * declaration sits at the top level and is reachable from anywhere in the
 * program.
 *
 * <p>The class is designed with a parent-chain architecture so that nested
 * scopes (function bodies, blocks, method scopes) can be introduced in later
 * versions without any structural changes:
 *
 * <pre>{@code
 * // v0.1 — single global scope
 * Environment global = new Environment();
 *
 * // v0.3 — function call creates a child scope that shadows the parent
 * Environment funcScope = new Environment(global);
 * }</pre>
 *
 * <h2>Variable lifetime rules (v0.1)</h2>
 * <ul>
 *   <li>Variables are created by {@link #define} (from a {@code var} statement).</li>
 *   <li>Reading an undeclared name throws {@link RuntimeError} R004.</li>
 *   <li>Assigning to an undeclared name throws {@link RuntimeError} R005.</li>
 *   <li>Re-declaring an existing name with {@code var} <em>re-defines</em> (shadows)
 *       it in the current scope — no error.</li>
 * </ul>
 *
 * <h2>Forward-compatibility</h2>
 * <ul>
 *   <li>v0.2 (blocks) — create child {@code Environment} per {@code { }}</li>
 *   <li>v0.3 (functions) — create child per call frame, close over parent</li>
 *   <li>v0.5 (objects) — {@code Environment} becomes the object's field table</li>
 * </ul>
 */
public final class Environment {

    // ------------------------------------------------------------------ //
    //  Fields                                                            //
    // ------------------------------------------------------------------ //

    /**
     * The enclosing scope, or {@code null} for the global scope.
     *
     * <p>Variable lookup walks this chain until the name is found or the
     * chain is exhausted.
     */
    private final Environment enclosing;

    /**
     * Variables defined in <em>this</em> scope.
     * Keys are variable names (case-sensitive).
     * Values are Java objects: {@link Integer}, {@link Double},
     * {@link String}, {@link Boolean}, or {@code null}.
     */
    private final Map<String, Object> values = new HashMap<>();

    // ------------------------------------------------------------------ //
    //  Constructors                                                      //
    // ------------------------------------------------------------------ //

    /** Creates the global (top-level) scope with no enclosing parent. */
    public Environment() {
        this.enclosing = null;
    }

    /**
     * Creates a child scope that delegates unknown lookups to {@code enclosing}.
     *
     * @param enclosing the parent scope; must not be {@code null}
     */
    public Environment(Environment enclosing) {
        if (enclosing == null) throw new IllegalArgumentException("enclosing must not be null");
        this.enclosing = enclosing;
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Defines (creates or re-defines) a variable in <em>this</em> scope.
     *
     * <p>If a variable with {@code name} already exists in this scope it is
     * silently overwritten.  This supports re-declaration:
     * <pre>
     *   var x = 5
     *   var x = 10   // re-declares x — legal in v0.1
     * </pre>
     *
     * @param name  the variable name (must not be {@code null})
     * @param value the initial value; may be {@code null} (future: uninitialized)
     */
    public void define(String name, Object value) {
        values.put(name, value);
    }

    /**
     * Retrieves the value of a variable, walking up the scope chain if needed.
     *
     * @param name the identifier token whose {@code lexeme} is the variable name
     * @return the stored value (may be {@code null} for future nullable vars)
     * @throws RuntimeError R004 if the variable is not found in any scope
     */
    public Object get(Token name) {
        // Check current scope first (inner scope shadows outer)
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        // Walk up the scope chain
        if (enclosing != null) {
            return enclosing.get(name);
        }

        // Not found anywhere
        throw RuntimeError.undefinedVariable(name);
    }

    /**
     * Updates an existing variable anywhere in the scope chain.
     *
     * <p>Assignment is distinct from declaration: it locates the <em>nearest</em>
     * scope that already contains {@code name} and updates it there.  This
     * ensures that inner scopes can mutate outer-scope variables (closures).
     *
     * @param name  the identifier token naming the variable
     * @param value the new value
     * @throws RuntimeError R005 if the variable is not found in any scope
     */
    public void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw RuntimeError.undefinedAssign(name);
    }

    // ------------------------------------------------------------------ //
    //  Introspection (for debugging / REPL)                             //
    // ------------------------------------------------------------------ //

    /**
     * Returns a read-only snapshot of all variables in this scope (not the chain).
     * Useful for REPL inspection and test assertions.
     */
    public Map<String, Object> snapshot() {
        return Map.copyOf(values);
    }

    /**
     * Returns the number of variables defined in this scope (not the chain).
     */
    public int size() {
        return values.size();
    }

    /**
     * Returns {@code true} if {@code name} is defined in this scope (not the chain).
     */
    public boolean containsLocal(String name) {
        return values.containsKey(name);
    }

    /** Returns the enclosing parent scope, or {@code null} for the global scope. */
    public Environment getEnclosing() {
        return enclosing;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Environment{");
        values.forEach((k, v) -> sb.append(k).append("=").append(v).append(", "));
        if (!values.isEmpty()) sb.setLength(sb.length() - 2);
        sb.append("}");
        if (enclosing != null) sb.append(" -> ").append(enclosing);
        return sb.toString();
    }
}
