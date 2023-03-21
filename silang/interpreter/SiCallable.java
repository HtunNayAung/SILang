package silang.interpreter;

import java.util.List;

/**
 * Represents any value in SIlang that can be called with {@code ()}.
 *
 * <p>In Version 0.1 the only callable is the built-in {@code out} function,
 * which is implemented as an anonymous class inside the interpreter rather
 * than a named class.  This interface is introduced now so that Version 0.3
 * user-defined functions can implement it, and the {@code Call} visitor
 * can dispatch uniformly to any callable without an instanceof chain.
 *
 * <h2>Design contract</h2>
 * <ul>
 *   <li>{@link #arity()} must return the exact expected argument count, or
 *       {@code -1} to indicate variadic (unlimited) arguments.</li>
 *   <li>{@link #call} receives already-evaluated argument values in source order.</li>
 *   <li>{@link #call} may return {@code null} for void-returning callables.</li>
 * </ul>
 *
 * <h2>Future implementations</h2>
 * <ul>
 *   <li>{@code SiFunction}  — user-defined function (v0.3)</li>
 *   <li>{@code SiClass}     — class constructor (v0.5)</li>
 *   <li>{@code SiMethod}    — bound method (v0.5)</li>
 *   <li>{@code SiNative}    — wrapper for JVM-level built-ins (v0.8)</li>
 * </ul>
 */
public interface SiCallable {

    /**
     * The number of arguments this callable expects.
     * Return {@code -1} for variadic callables (unlimited arguments).
     */
    int arity();

    /**
     * Executes the callable with the given (already-evaluated) argument list.
     *
     * @param interpreter the current interpreter, for recursive evaluation
     * @param arguments   the evaluated argument values in source order
     * @return the return value, or {@code null} for void callables
     */
    Object call(Interpreter interpreter, List<Object> arguments);

    /**
     * Returns a display name for this callable, used in error messages
     * and the future {@code toString()} output for function values.
     */
    default String displayName() { return "<callable>"; }
}
