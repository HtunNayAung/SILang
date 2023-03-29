package silang.interpreter;

/**
 * Enumerates every runtime type in SIlang.
 *
 * <p>Using an enum rather than raw class checks gives us:
 * <ul>
 *   <li>Exhaustive switches — the compiler warns when a new type is added
 *       but a switch arm is missing.</li>
 *   <li>A single place to extend when new types arrive (e.g. {@code ARRAY},
 *       {@code OBJECT}, {@code FUNCTION} in later versions).</li>
 *   <li>Human-readable type names for error messages.</li>
 * </ul>
 *
 * <h2>Runtime type mapping</h2>
 * <table border="1">
 *   <tr><th>SiType</th><th>Java type</th><th>SIlang source</th></tr>
 *   <tr><td>INT</td>    <td>{@link Integer}</td><td>{@code 42}</td></tr>
 *   <tr><td>FLOAT</td>  <td>{@link Double}</td> <td>{@code 3.14}</td></tr>
 *   <tr><td>STRING</td> <td>{@link String}</td> <td>{@code "hi"}</td></tr>
 *   <tr><td>BOOLEAN</td><td>{@link Boolean}</td><td>{@code true}</td></tr>
 *   <tr><td>NULL</td>   <td>{@code null}</td>   <td>{@code null} (future)</td></tr>
 * </table>
 */
public enum SiType {

    INT     ("int"),
    FLOAT   ("float"),
    STRING  ("string"),
    BOOLEAN ("boolean"),
    NULL    ("null");

    // ------------------------------------------------------------------ //
    //  Fields                                                            //
    // ------------------------------------------------------------------ //

    /** The SIlang keyword spelling of this type, used in error messages. */
    private final String keyword;

    SiType(String keyword) { this.keyword = keyword; }

    // ------------------------------------------------------------------ //
    //  Accessors                                                         //
    // ------------------------------------------------------------------ //

    /** The SIlang keyword name, e.g. {@code "int"}, {@code "string"}. */
    public String keyword() { return keyword; }

    @Override public String toString() { return keyword; }

    // ------------------------------------------------------------------ //
    //  Classification helpers                                            //
    // ------------------------------------------------------------------ //

    /**
     * Returns the {@link SiType} corresponding to the given Java runtime
     * value, or {@link #NULL} for a {@code null} reference.
     *
     * @param value any Java object produced by the interpreter
     * @return the corresponding {@link SiType}
     * @throws IllegalArgumentException if {@code value} is of an unrecognised type
     */
    public static SiType of(Object value) {
        if (value == null)            return NULL;
        if (value instanceof Integer) return INT;
        if (value instanceof Double)  return FLOAT;
        if (value instanceof String)  return STRING;
        if (value instanceof Boolean) return BOOLEAN;
        throw new IllegalArgumentException(
            "Unrecognised SIlang runtime value type: " + value.getClass().getName());
    }

    /**
     * Returns {@code true} if this type is numeric (INT or FLOAT).
     */
    public boolean isNumeric() {
        return this == INT || this == FLOAT;
    }

    /**
     * Returns {@code true} for the given Java object if its type is numeric.
     */
    public static boolean isNumeric(Object value) {
        return value instanceof Integer || value instanceof Double;
    }
}
