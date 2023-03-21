package silang.interpreter;

import silang.Token;

/**
 * Thrown by the SIlang interpreter when a program attempts an operation
 * that is semantically invalid at runtime.
 *
 * <p>Every {@code RuntimeError} carries the {@link Token} that triggered the
 * fault, giving the interpreter precise file/line/column information for
 * user-facing diagnostics.
 *
 * <h2>Error codes</h2>
 * <pre>
 *   R001  Division by zero
 *   R002  Unary '-' applied to a non-numeric operand
 *   R003  Binary operator applied to incompatible types
 *   R004  Undefined variable referenced
 *   R005  Assignment to an undefined variable
 *   R006  Unknown function called
 *   R007  Wrong number of arguments to built-in function
 *   R008  String concatenation with an unconvertible value (future)
 * </pre>
 *
 * <h2>Sample diagnostic</h2>
 * <pre>
 * error[R001]: division by zero
 *   --> hello.si:7:15
 *    |
 *  7 | var x = 10 / 0
 *    |              ^
 * </pre>
 */
public final class RuntimeError extends RuntimeException {

    // ------------------------------------------------------------------ //
    //  Error codes                                                       //
    // ------------------------------------------------------------------ //

    public static final String ERR_DIVISION_BY_ZERO    = "R001";
    public static final String ERR_UNARY_TYPE          = "R002";
    public static final String ERR_BINARY_TYPE         = "R003";
    public static final String ERR_UNDEFINED_VARIABLE  = "R004";
    public static final String ERR_UNDEFINED_ASSIGN    = "R005";
    public static final String ERR_UNKNOWN_FUNCTION    = "R006";
    public static final String ERR_WRONG_ARITY         = "R007";

    // ------------------------------------------------------------------ //
    //  Fields                                                            //
    // ------------------------------------------------------------------ //

    /** Short diagnostic code, e.g. {@code "R001"}. */
    private final String errorCode;

    /**
     * The token that was being evaluated when the error occurred.
     * Used to report the source location.
     */
    private final Token token;

    // ------------------------------------------------------------------ //
    //  Constructor                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Constructs a new {@code RuntimeError}.
     *
     * @param errorCode  short diagnostic code
     * @param message    human-readable description
     * @param token      the token at which execution failed
     */
    public RuntimeError(String errorCode, String message, Token token) {
        super(message);
        this.errorCode = errorCode;
        this.token     = token;
    }

    // ------------------------------------------------------------------ //
    //  Factory methods                                                   //
    // ------------------------------------------------------------------ //

    /** R001 — division by zero. */
    public static RuntimeError divisionByZero(Token op) {
        return new RuntimeError(ERR_DIVISION_BY_ZERO,
            "division by zero", op);
    }

    /** R002 — unary '-' on a non-numeric value. */
    public static RuntimeError unaryTypeMismatch(Token op, Object value) {
        String typeName = typeName(value);
        return new RuntimeError(ERR_UNARY_TYPE,
            String.format("unary '-' requires a number, got %s", typeName), op);
    }

    /** R003 — binary operator applied to incompatible operand types. */
    public static RuntimeError binaryTypeMismatch(Token op, Object left, Object right) {
        return new RuntimeError(ERR_BINARY_TYPE,
            String.format("operator '%s' cannot be applied to %s and %s",
                op.lexeme, typeName(left), typeName(right)), op);
    }

    /** R004 — reading a variable that has not been declared. */
    public static RuntimeError undefinedVariable(Token name) {
        return new RuntimeError(ERR_UNDEFINED_VARIABLE,
            String.format("undefined variable '%s'", name.lexeme), name);
    }

    /** R005 — assigning to a variable that has not been declared. */
    public static RuntimeError undefinedAssign(Token name) {
        return new RuntimeError(ERR_UNDEFINED_ASSIGN,
            String.format("assignment to undefined variable '%s'", name.lexeme), name);
    }

    /** R006 — calling a name that is not a known function. */
    public static RuntimeError unknownFunction(Token name) {
        return new RuntimeError(ERR_UNKNOWN_FUNCTION,
            String.format("undefined function '%s'", name.lexeme), name);
    }

    /** R007 — wrong number of arguments to a built-in function. */
    public static RuntimeError wrongArity(Token paren, String funcName, int expected, int got) {
        return new RuntimeError(ERR_WRONG_ARITY,
            String.format("'%s' expects %d argument(s) but got %d",
                funcName, expected, got), paren);
    }

    // ------------------------------------------------------------------ //
    //  Accessors                                                         //
    // ------------------------------------------------------------------ //

    public String getErrorCode() { return errorCode; }
    public Token  getToken()     { return token;      }

    // ------------------------------------------------------------------ //
    //  Diagnostics                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Formats a compiler-style diagnostic string matching the SIlang
     * specification output format.
     *
     * @param fileName   the source file name
     * @param sourceLine the raw text of the line on which the error occurred
     * @return the formatted diagnostic string
     */
    public String formatDiagnostic(String fileName, String sourceLine) {
        int    line      = token.line;
        int    col       = token.column;
        String lineLabel = String.valueOf(line);
        String pad       = " ".repeat(lineLabel.length());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("error[%s]: %s%n", errorCode, getMessage()));
        sb.append(String.format("  --> %s:%d:%d%n", fileName, line, col));
        if (sourceLine != null && !sourceLine.isEmpty()) {
            sb.append(String.format("   %s |%n", pad));
            sb.append(String.format(" %s  | %s%n", lineLabel, sourceLine));
            sb.append(String.format("   %s | %s^%n", pad, " ".repeat(Math.max(0, col - 1))));
        }
        return sb.toString().stripTrailing();
    }

    @Override
    public String toString() {
        return String.format("RuntimeError(%s) at %d:%d — %s",
            errorCode, token.line, token.column, getMessage());
    }

    // ------------------------------------------------------------------ //
    //  Utility                                                           //
    // ------------------------------------------------------------------ //

    /**
     * Returns a readable SIlang type name for a Java runtime value.
     *
     * <ul>
     *   <li>{@link Integer}  → {@code "int"}</li>
     *   <li>{@link Double}   → {@code "float"}</li>
     *   <li>{@link String}   → {@code "string"}</li>
     *   <li>{@link Boolean}  → {@code "boolean"}</li>
     *   <li>{@code null}     → {@code "null"}</li>
     * </ul>
     */
    public static String typeName(Object value) {
        if (value == null)                return "null";
        if (value instanceof Integer)     return "int";
        if (value instanceof Double)      return "float";
        if (value instanceof String)      return "string";
        if (value instanceof Boolean)     return "boolean";
        return value.getClass().getSimpleName();
    }
}
