package silang.interpreter;

import silang.Token;

/**
 * Thrown by the SIlang interpreter when a program attempts an operation
 * that is semantically invalid at runtime.
 *
 * <p>Every {@code RuntimeError} carries the {@link Token} that triggered the
 * fault so the interpreter can produce precise file/line/column diagnostics.
 *
 * <h2>Error codes</h2>
 * <pre>
 *   R001  Division by zero
 *   R002  Unary '-' applied to a non-numeric operand (string or boolean)
 *   R003  Binary operator applied to incompatible types
 *   R004  Undefined variable read
 *   R005  Assignment to an undefined variable
 *   R006  Unknown / undefined function called
 *   R007  Wrong number of arguments to a function
 * </pre>
 *
 * <h2>Diagnostic format</h2>
 * <pre>
 * error[R001]: division by zero
 *   --> hello.si:7:15
 *    |
 *  7 | var x = 10 / 0
 *    |              ^
 *
 * error[R003]: cannot subtract string from int
 *   --> hello.si:3:5
 *    |
 *  3 | var x = "hello" - 5
 *    |                 ^
 *
 * error[R002]: cannot negate boolean — unary '-' requires int or float
 *   --> hello.si:5:9
 * </pre>
 */
// v0.3 — R009 returnOutsideFunction added
public final class RuntimeError extends RuntimeException {

    // ------------------------------------------------------------------ //
    //  Error codes                                                       //
    // ------------------------------------------------------------------ //

    public static final String ERR_DIVISION_BY_ZERO   = "R001";
    public static final String ERR_UNARY_TYPE         = "R002";
    public static final String ERR_BINARY_TYPE        = "R003";
    public static final String ERR_UNDEFINED_VARIABLE = "R004";
    public static final String ERR_UNDEFINED_ASSIGN   = "R005";
    public static final String ERR_UNKNOWN_FUNCTION   = "R006";
    public static final String ERR_WRONG_ARITY        = "R007";
    public static final String ERR_NON_BOOLEAN_COND   = "R008";
    public static final String ERR_RETURN_OUTSIDE_FN  = "R009";

    // ------------------------------------------------------------------ //
    //  Fields                                                            //
    // ------------------------------------------------------------------ //

    private final String errorCode;
    private final Token  token;

    // ------------------------------------------------------------------ //
    //  Constructor                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Constructs a {@code RuntimeError}.
     *
     * @param errorCode short code (e.g. {@code "R001"})
     * @param message   human-readable description
     * @param token     the token at which execution failed
     */
    public RuntimeError(String errorCode, String message, Token token) {
        super(message);
        this.errorCode = errorCode;
        this.token     = token;
    }

    // ------------------------------------------------------------------ //
    //  Factory methods                                                   //
    // ------------------------------------------------------------------ //

    // ── R001 ─────────────────────────────────────────────────────────────

    /** Division by zero. */
    public static RuntimeError divisionByZero(Token op) {
        return new RuntimeError(ERR_DIVISION_BY_ZERO, "division by zero", op);
    }

    // ── R002 ─────────────────────────────────────────────────────────────

    /**
     * Unary {@code -} applied to a non-numeric value.
     * Message: "cannot negate boolean — unary '-' requires int or float"
     */
    public static RuntimeError cannotNegate(Token op, Object value) {
        String t = typeName(value);
        return new RuntimeError(ERR_UNARY_TYPE,
            String.format("cannot negate %s — unary '-' requires int or float", t), op);
    }

    // ── R003 ─────────────────────────────────────────────────────────────

    /**
     * Generic binary type mismatch — used when the specific operator message
     * helpers below don't apply.
     *
     * <p>Message: "operator '+' cannot be applied to boolean and boolean"
     */
    public static RuntimeError binaryTypeMismatch(Token op, Object left, Object right) {
        return new RuntimeError(ERR_BINARY_TYPE,
            String.format("operator '%s' cannot be applied to %s and %s",
                op.lexeme, typeName(left), typeName(right)), op);
    }

    /**
     * {@code -} with a non-numeric operand.
     * Message: "cannot subtract string from int"
     */
    public static RuntimeError cannotSubtract(Token op, Object left, Object right) {
        return new RuntimeError(ERR_BINARY_TYPE,
            String.format("cannot subtract %s from %s", typeName(right), typeName(left)), op);
    }

    /**
     * {@code *} with a non-numeric operand.
     * Message: "cannot multiply boolean by float"
     */
    public static RuntimeError cannotMultiply(Token op, Object left, Object right) {
        return new RuntimeError(ERR_BINARY_TYPE,
            String.format("cannot multiply %s by %s", typeName(left), typeName(right)), op);
    }

    /**
     * {@code /} with a non-numeric operand.
     * Message: "cannot divide string by int"
     */
    public static RuntimeError cannotDivide(Token op, Object left, Object right) {
        return new RuntimeError(ERR_BINARY_TYPE,
            String.format("cannot divide %s by %s", typeName(left), typeName(right)), op);
    }

    // ── R004 ─────────────────────────────────────────────────────────────

    /** Read of an undeclared variable. */
    public static RuntimeError undefinedVariable(Token name) {
        return new RuntimeError(ERR_UNDEFINED_VARIABLE,
            String.format("undefined variable '%s'", name.lexeme), name);
    }

    // ── R005 ─────────────────────────────────────────────────────────────

    /** Assignment to an undeclared variable. */
    public static RuntimeError undefinedAssign(Token name) {
        return new RuntimeError(ERR_UNDEFINED_ASSIGN,
            String.format("assignment to undefined variable '%s'", name.lexeme), name);
    }

    // ── R006 ─────────────────────────────────────────────────────────────

    /** Call to a function that has not been defined. */
    public static RuntimeError unknownFunction(Token name) {
        return new RuntimeError(ERR_UNKNOWN_FUNCTION,
            String.format("undefined function '%s'", name.lexeme), name);
    }

    // ── R007 ─────────────────────────────────────────────────────────────

    /** Wrong argument count for a built-in or user-defined function. */
    public static RuntimeError wrongArity(Token paren, String funcName, int expected, int got) {
        String argWord = (expected == 1) ? "argument" : "arguments";
        return new RuntimeError(ERR_WRONG_ARITY,
            String.format("'%s' expects %d %s but received %d",
                funcName, expected, argWord, got), paren);
    }

    // ── R008 ─────────────────────────────────────────────────────────────

    /**
     * Condition of {@code if} or {@code while} is not boolean.
     * Message: "if condition must be boolean but got int"
     */
    public static RuntimeError nonBooleanCondition(Token keyword, Object value) {
        return new RuntimeError(ERR_NON_BOOLEAN_COND,
            String.format("'%s' condition must be boolean but got %s",
                keyword.lexeme, typeName(value)), keyword);
    }

    /**
     * Logical NOT ({@code !}) applied to a non-boolean value (R002).
     * Message: "cannot apply '!' to int — '!' requires boolean"
     */
    public static RuntimeError cannotNot(Token op, Object value) {
        return new RuntimeError(ERR_UNARY_TYPE,
            String.format("cannot apply '!' to %s — '!' requires boolean", typeName(value)), op);
    }

    // ── R009 ─────────────────────────────────────────────────────────────

    /**
     * {@code return} used outside any function body.
     * Message: "cannot use 'return' outside a function"
     */
    public static RuntimeError returnOutsideFunction(Token keyword) {
        return new RuntimeError(ERR_RETURN_OUTSIDE_FN,
            "cannot use 'return' outside a function", keyword);
    }

    // ------------------------------------------------------------------ //
    //  Accessors                                                         //
    // ------------------------------------------------------------------ //

    public String getErrorCode() { return errorCode; }
    public Token  getToken()     { return token;      }

    // ------------------------------------------------------------------ //
    //  Formatted diagnostic                                              //
    // ------------------------------------------------------------------ //

    /**
     * Returns a compiler-style multi-line diagnostic string:
     *
     * <pre>
     * error[R003]: cannot subtract string from int
     *   --> hello.si:3:5
     *    |
     *  3 | var x = "hello" - 5
     *    |                 ^
     * </pre>
     *
     * @param fileName   source file name
     * @param sourceLine the raw text of the offending line
     */
    public String formatDiagnostic(String fileName, String sourceLine) {
        int    line  = token.line;
        int    col   = token.column;
        String label = String.valueOf(line);
        String pad   = " ".repeat(label.length());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("error[%s]: %s%n", errorCode, getMessage()));
        sb.append(String.format("  --> %s:%d:%d%n", fileName, line, col));
        if (sourceLine != null && !sourceLine.isEmpty()) {
            sb.append(String.format("   %s |%n",          pad));
            sb.append(String.format(" %s  | %s%n",        label, sourceLine));
            sb.append(String.format("   %s | %s^%n",      pad, " ".repeat(Math.max(0, col - 1))));
        }
        return sb.toString().stripTrailing();
    }

    @Override
    public String toString() {
        return String.format("RuntimeError(%s) at %d:%d — %s",
            errorCode, token.line, token.column, getMessage());
    }

    // ------------------------------------------------------------------ //
    //  Type name utility                                                 //
    // ------------------------------------------------------------------ //

    /**
     * Returns the SIlang type name for a Java runtime value.
     *
     * <table border="1">
     *   <tr><td>{@link Integer}</td> <td>{@code "int"}</td></tr>
     *   <tr><td>{@link Double}</td>  <td>{@code "float"}</td></tr>
     *   <tr><td>{@link String}</td>  <td>{@code "string"}</td></tr>
     *   <tr><td>{@link Boolean}</td> <td>{@code "boolean"}</td></tr>
     *   <tr><td>{@code null}</td>    <td>{@code "null"}</td></tr>
     * </table>
     */
    public static String typeName(Object value) {
        if (value == null)            return "null";
        if (value instanceof Integer) return "int";
        if (value instanceof Double)  return "float";
        if (value instanceof String)  return "string";
        if (value instanceof Boolean) return "boolean";
        return value.getClass().getSimpleName();
    }
}
