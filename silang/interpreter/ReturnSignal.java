package silang.interpreter;

/**
 * Control-flow exception used to implement {@code return} in SILang.
 *
 * <p>When the interpreter executes a {@code return} statement it throws a
 * {@code ReturnSignal} carrying the return value.  The signal unwinds the
 * Java call stack until it reaches {@link SiFunction#call}, which catches
 * it and extracts the value.
 *
 * <p>Using an exception for this is the standard tree-walk interpreter
 * pattern.  It is simpler and more correct than trying to thread a return
 * value back through every {@code execute()} call with a nullable result.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>Extends {@link RuntimeException} so it does not need to be declared
 *       in {@code throws} clauses.</li>
 *   <li>{@code fillInStackTrace()} is suppressed — we do not need a Java
 *       stack trace for a normal control-flow event, and suppressing it
 *       makes the throw/catch cheap.</li>
 *   <li>Future: {@code BreakSignal} and {@code ContinueSignal} will follow
 *       the same pattern for loop control flow.</li>
 * </ul>
 */
public final class ReturnSignal extends RuntimeException {

    /** The value being returned. {@code null} for a bare {@code return}. */
    public final Object value;

    /**
     * Constructs a return signal carrying {@code value}.
     *
     * @param value the return value; {@code null} for {@code return} with no expression
     */
    public ReturnSignal(Object value) {
        super(null, null, true, false);   // suppress stack trace fill-in
        this.value = value;
    }
}
