package silang.interpreter;

/**
 * Converts SIlang runtime values to their canonical string representation.
 *
 * <p>This logic is extracted from the interpreter into its own class because
 * it is used by multiple components:
 * <ul>
 *   <li>{@link Interpreter} — for the {@code out()} built-in</li>
 *   <li>{@link TypeSystem}  — for string concatenation</li>
 *   <li>Future REPL        — for printing expression results</li>
 *   <li>Future debugger    — for variable inspection</li>
 * </ul>
 *
 * <h2>Conversion rules</h2>
 * <table border="1">
 *   <tr><th>SIlang type</th><th>Java value</th><th>String output</th></tr>
 *   <tr><td>int</td>    <td>{@link Integer}</td><td>decimal digits, e.g. {@code 42}</td></tr>
 *   <tr><td>float</td>  <td>{@link Double}</td> <td>decimal notation; trailing {@code .0}
 *       stripped for whole numbers, e.g. {@code 5.0 → "5"}, {@code 3.14 → "3.14"}</td></tr>
 *   <tr><td>string</td> <td>{@link String}</td> <td>the string itself (no quotes)</td></tr>
 *   <tr><td>boolean</td><td>{@link Boolean}</td><td>{@code "true"} or {@code "false"}</td></tr>
 *   <tr><td>null</td>   <td>{@code null}</td>   <td>{@code "null"}</td></tr>
 * </table>
 *
 * <h2>Float formatting detail</h2>
 * <p>Java's {@link Double#toString} produces {@code "5.0"} for {@code 5.0},
 * which is misleading when the SIlang program wrote {@code var x = 10.0 / 2.0}.
 * SIlang strips the {@code .0} suffix so output reads {@code "5"} rather than
 * {@code "5.0"}.  Non-whole floats like {@code 3.14} are printed as-is.
 *
 * <p>This matches the SIlang Version 0.1 specification §Expressions — Division.
 */
public final class Stringify {

    private Stringify() {}  // utility class

    /**
     * Converts {@code value} to its SIlang string representation.
     *
     * @param value any SIlang runtime value (may be {@code null})
     * @return the canonical string representation; never {@code null}
     */
    public static String of(Object value) {
        if (value == null)              return "null";
        if (value instanceof Boolean b) return b ? "true" : "false";
        if (value instanceof Double  d) return formatDouble(d);
        return value.toString();        // Integer, String — toString is already correct
    }

    /**
     * Formats a {@link Double} according to SIlang display rules:
     * strips the {@code .0} suffix for whole numbers.
     *
     * <p>Examples:
     * <pre>
     *   5.0      → "5"
     *   3.14     → "3.14"
     *   -2.5     → "-2.5"
     *   100.0    → "100"
     *   0.5      → "0.5"
     * </pre>
     */
    private static String formatDouble(double d) {
        String s = Double.toString(d);
        // Java produces "5.0", "100.0" etc. for whole-number doubles.
        // Strip the trailing ".0" to match SIlang output spec.
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }
}
