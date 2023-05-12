package silang.interpreter;

import silang.Token;
import silang.TokenType;

/**
 * Authoritative type-checking and type-coercion rules for SIlang Version 0.1.
 *
 * <p>This class is the <em>single source of truth</em> for all operator
 * semantics.  The interpreter delegates every type decision here, keeping
 * the interpreter itself free of scattered {@code instanceof} chains.
 *
 * <h2>Design: data-driven operator table</h2>
 * <p>Rather than nested {@code if/else} blocks, operator rules are expressed
 * as a lookup table keyed on {@code (operator, leftType, rightType)}.  Adding
 * a new operator in Version 0.2 (e.g. {@code ==}, {@code <}) requires only
 * adding rows to {@link #BINARY_RULES} — no changes elsewhere.
 *
 * <h2>Binary operator rules (v0.1)</h2>
 *
 * <h3>Addition ({@code +})</h3>
 * <table border="1">
 *   <tr><th>Left</th>   <th>Right</th>  <th>Result</th></tr>
 *   <tr><td>int</td>    <td>int</td>    <td>int</td></tr>
 *   <tr><td>float</td>  <td>float</td>  <td>float</td></tr>
 *   <tr><td>int</td>    <td>float</td>  <td>float</td></tr>
 *   <tr><td>float</td>  <td>int</td>    <td>float</td></tr>
 *   <tr><td>string</td> <td>any</td>    <td>string (concat)</td></tr>
 *   <tr><td>any</td>    <td>string</td> <td>string (concat)</td></tr>
 *   <tr><td colspan="2">all others</td> <td>→ R003 error</td></tr>
 * </table>
 *
 * <h3>Subtraction ({@code -}), Multiplication ({@code *})</h3>
 * <table border="1">
 *   <tr><th>Left</th>  <th>Right</th>  <th>Result</th></tr>
 *   <tr><td>int</td>   <td>int</td>    <td>int</td></tr>
 *   <tr><td>float</td> <td>float</td>  <td>float</td></tr>
 *   <tr><td>int</td>   <td>float</td>  <td>float</td></tr>
 *   <tr><td>float</td> <td>int</td>    <td>float</td></tr>
 *   <tr><td colspan="2">all others</td><td>→ R003 error</td></tr>
 * </table>
 *
 * <h3>Division ({@code /})</h3>
 * <p>Same type rules as subtraction, plus division-by-zero guard (R001).
 *
 * <h2>Unary rules (v0.1)</h2>
 * <table border="1">
 *   <tr><th>Operator</th><th>Operand</th><th>Result</th></tr>
 *   <tr><td>-</td><td>int</td>    <td>int</td></tr>
 *   <tr><td>-</td><td>float</td>  <td>float</td></tr>
 *   <tr><td>-</td><td>string</td> <td>→ R002</td></tr>
 *   <tr><td>-</td><td>boolean</td><td>→ R002</td></tr>
 * </table>
 *
 * <h2>Forward-compatibility</h2>
 * <p>When Version 0.2 adds comparison and logical operators, extend this
 * class by adding new {@link BinaryRule} entries in {@link #BINARY_RULES}.
 * No changes to the interpreter are required.
 */
public final class TypeSystem {

    private TypeSystem() {}  // utility class

    // ================================================================== //
    //  Result type descriptor                                            //
    // ================================================================== //

    /**
     * Describes the result of a successfully type-checked binary operation.
     *
     * <p>The {@link ResultKind} determines how the interpreter computes the
     * value; {@link #resultType} is the SIlang type of the result.
     */
    public enum ResultKind {
        /** Both operands are INT; compute in integer arithmetic. */
        INT_ARITH,
        /** At least one operand is FLOAT; promote both to double. */
        FLOAT_ARITH,
        /** The {@code +} operator with a string on one side: concatenate. */
        STRING_CONCAT,
        /** Division — same as INT_ARITH but with zero-divisor guard. */
        INT_DIVISION,
        /** Division — same as FLOAT_ARITH but with zero-divisor guard. */
        FLOAT_DIVISION,
        /** Modulo — integer remainder, with zero-divisor guard. */
        INT_MODULO,
        /** Modulo — float remainder, with zero-divisor guard. */
        FLOAT_MODULO,
        /** Future: boolean result (comparison, logical). */
        BOOL_RESULT
    }

    /**
     * The outcome of a type-check call: the kind of computation to perform
     * and the SIlang type of the result value.
     */
    public record TypeCheckResult(ResultKind kind, SiType resultType) {}

    // ================================================================== //
    //  Binary operator rule table                                        //
    // ================================================================== //

    /**
     * One row in the binary operator type table.
     *
     * <p>A rule matches when the operator token type, left operand type, and
     * right operand type all equal the row's values.  {@code null} in the
     * left/right fields acts as a wildcard (matches any type).
     */
    private record BinaryRule(
            TokenType  operator,
            SiType     left,          // null = wildcard
            SiType     right,         // null = wildcard
            ResultKind resultKind,
            SiType     resultType
    ) {
        /** Returns true if this rule matches the given operand types. */
        boolean matches(TokenType op, SiType l, SiType r) {
            return operator == op
                && (left  == null || left  == l)
                && (right == null || right == r);
        }
    }

    /**
     * The complete binary operator type table for SIlang v0.1.
     *
     * <p>Rules are evaluated top-to-bottom; the first matching rule wins.
     * Wildcard rows (with {@code null} fields) must therefore come after
     * more specific rows.
     *
     * <p>To add a new operator (e.g. {@code ==} in v0.2), append new
     * {@link BinaryRule} entries here.  No other code changes required.
     */
    private static final BinaryRule[] BINARY_RULES = {

        // ── PLUS (+) ──────────────────────────────────────────────────────
        //   Specific numeric rules first, then wildcard string-concat rows.
        new BinaryRule(TokenType.PLUS, SiType.INT,    SiType.INT,    ResultKind.INT_ARITH,     SiType.INT),
        new BinaryRule(TokenType.PLUS, SiType.FLOAT,  SiType.FLOAT,  ResultKind.FLOAT_ARITH,   SiType.FLOAT),
        new BinaryRule(TokenType.PLUS, SiType.INT,    SiType.FLOAT,  ResultKind.FLOAT_ARITH,   SiType.FLOAT),
        new BinaryRule(TokenType.PLUS, SiType.FLOAT,  SiType.INT,    ResultKind.FLOAT_ARITH,   SiType.FLOAT),
        // string + anything  (wildcard right)
        new BinaryRule(TokenType.PLUS, SiType.STRING, null,          ResultKind.STRING_CONCAT,  SiType.STRING),
        // anything + string  (wildcard left)
        new BinaryRule(TokenType.PLUS, null,          SiType.STRING, ResultKind.STRING_CONCAT,  SiType.STRING),

        // ── MINUS (-) ─────────────────────────────────────────────────────
        new BinaryRule(TokenType.MINUS, SiType.INT,   SiType.INT,    ResultKind.INT_ARITH,     SiType.INT),
        new BinaryRule(TokenType.MINUS, SiType.FLOAT, SiType.FLOAT,  ResultKind.FLOAT_ARITH,   SiType.FLOAT),
        new BinaryRule(TokenType.MINUS, SiType.INT,   SiType.FLOAT,  ResultKind.FLOAT_ARITH,   SiType.FLOAT),
        new BinaryRule(TokenType.MINUS, SiType.FLOAT, SiType.INT,    ResultKind.FLOAT_ARITH,   SiType.FLOAT),

        // ── STAR (*) ──────────────────────────────────────────────────────
        new BinaryRule(TokenType.STAR, SiType.INT,    SiType.INT,    ResultKind.INT_ARITH,     SiType.INT),
        new BinaryRule(TokenType.STAR, SiType.FLOAT,  SiType.FLOAT,  ResultKind.FLOAT_ARITH,   SiType.FLOAT),
        new BinaryRule(TokenType.STAR, SiType.INT,    SiType.FLOAT,  ResultKind.FLOAT_ARITH,   SiType.FLOAT),
        new BinaryRule(TokenType.STAR, SiType.FLOAT,  SiType.INT,    ResultKind.FLOAT_ARITH,   SiType.FLOAT),

        // ── SLASH (/) ─────────────────────────────────────────────────────
        new BinaryRule(TokenType.SLASH, SiType.INT,   SiType.INT,    ResultKind.INT_DIVISION,  SiType.INT),
        new BinaryRule(TokenType.SLASH, SiType.FLOAT, SiType.FLOAT,  ResultKind.FLOAT_DIVISION,SiType.FLOAT),
        new BinaryRule(TokenType.SLASH, SiType.INT,   SiType.FLOAT,  ResultKind.FLOAT_DIVISION,SiType.FLOAT),
        new BinaryRule(TokenType.SLASH, SiType.FLOAT, SiType.INT,    ResultKind.FLOAT_DIVISION,SiType.FLOAT),

        // ── PERCENT (%) ───────────────────────────────────────────────────
        new BinaryRule(TokenType.PERCENT, SiType.INT,   SiType.INT,   ResultKind.INT_MODULO,   SiType.INT),
        new BinaryRule(TokenType.PERCENT, SiType.FLOAT, SiType.FLOAT, ResultKind.FLOAT_MODULO, SiType.FLOAT),
        new BinaryRule(TokenType.PERCENT, SiType.INT,   SiType.FLOAT, ResultKind.FLOAT_MODULO, SiType.FLOAT),
        new BinaryRule(TokenType.PERCENT, SiType.FLOAT, SiType.INT,   ResultKind.FLOAT_MODULO, SiType.FLOAT),

        // ── EQUAL_EQUAL (==) ─────────────────────────────────────────────
        //   Any two values of the same type can be equality-compared.
        //   Cross-type equality is always false (int 1 != float 1.0).
        new BinaryRule(TokenType.EQUAL_EQUAL, SiType.INT,     SiType.INT,     ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.EQUAL_EQUAL, SiType.FLOAT,   SiType.FLOAT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.EQUAL_EQUAL, SiType.INT,     SiType.FLOAT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.EQUAL_EQUAL, SiType.FLOAT,   SiType.INT,     ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.EQUAL_EQUAL, SiType.STRING,  SiType.STRING,  ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.EQUAL_EQUAL, SiType.BOOLEAN, SiType.BOOLEAN, ResultKind.BOOL_RESULT, SiType.BOOLEAN),

        // ── BANG_EQUAL (!=) ──────────────────────────────────────────────
        new BinaryRule(TokenType.BANG_EQUAL, SiType.INT,     SiType.INT,     ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.BANG_EQUAL, SiType.FLOAT,   SiType.FLOAT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.BANG_EQUAL, SiType.INT,     SiType.FLOAT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.BANG_EQUAL, SiType.FLOAT,   SiType.INT,     ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.BANG_EQUAL, SiType.STRING,  SiType.STRING,  ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.BANG_EQUAL, SiType.BOOLEAN, SiType.BOOLEAN, ResultKind.BOOL_RESULT, SiType.BOOLEAN),

        // ── LESS (<), LESS_EQUAL (<=), GREATER (>), GREATER_EQUAL (>=) ──
        //   Only numeric types are ordered (strings are not comparable in v0.2).
        new BinaryRule(TokenType.LESS,          SiType.INT,   SiType.INT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.LESS,          SiType.FLOAT, SiType.FLOAT, ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.LESS,          SiType.INT,   SiType.FLOAT, ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.LESS,          SiType.FLOAT, SiType.INT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),

        new BinaryRule(TokenType.LESS_EQUAL,    SiType.INT,   SiType.INT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.LESS_EQUAL,    SiType.FLOAT, SiType.FLOAT, ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.LESS_EQUAL,    SiType.INT,   SiType.FLOAT, ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.LESS_EQUAL,    SiType.FLOAT, SiType.INT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),

        new BinaryRule(TokenType.GREATER,       SiType.INT,   SiType.INT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.GREATER,       SiType.FLOAT, SiType.FLOAT, ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.GREATER,       SiType.INT,   SiType.FLOAT, ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.GREATER,       SiType.FLOAT, SiType.INT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),

        new BinaryRule(TokenType.GREATER_EQUAL, SiType.INT,   SiType.INT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.GREATER_EQUAL, SiType.FLOAT, SiType.FLOAT, ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.GREATER_EQUAL, SiType.INT,   SiType.FLOAT, ResultKind.BOOL_RESULT, SiType.BOOLEAN),
        new BinaryRule(TokenType.GREATER_EQUAL, SiType.FLOAT, SiType.INT,   ResultKind.BOOL_RESULT, SiType.BOOLEAN),
    };

    // ================================================================== //
    //  Public type-checking API                                          //
    // ================================================================== //

    /**
     * Type-checks a binary operation and returns its {@link TypeCheckResult}.
     *
     * <p>Scans {@link #BINARY_RULES} for the first matching row.  If no row
     * matches the given operand types, throws {@link RuntimeError} R003 with a
     * specific message naming the operator and the two operand types:
     * <pre>
     *   error[R003]: cannot subtract string from int
     *   error[R003]: operator '*' cannot be applied to boolean and int
     * </pre>
     *
     * @param op    the operator token (carries type and position)
     * @param left  the evaluated left operand
     * @param right the evaluated right operand
     * @return the {@link TypeCheckResult} describing what computation to perform
     * @throws RuntimeError R003 if the types are incompatible for this operator
     */
    public static TypeCheckResult checkBinary(Token op, Object left, Object right) {
        SiType lt = SiType.of(left);
        SiType rt = SiType.of(right);

        for (BinaryRule rule : BINARY_RULES) {
            if (rule.matches(op.type, lt, rt)) {
                return new TypeCheckResult(rule.resultKind(), rule.resultType());
            }
        }

        // No rule matched — build a specific error message
        throw binaryTypeError(op, lt, rt);
    }

    /**
     * Type-checks a unary negation ({@code -}) and returns the result type.
     *
     * <p>Only {@code int} and {@code float} are valid; all other types
     * throw {@link RuntimeError} R002 with a specific message:
     * <pre>
     *   error[R002]: cannot negate boolean — unary '-' requires int or float
     *   error[R002]: cannot negate string — unary '-' requires int or float
     * </pre>
     *
     * @param op    the unary {@code -} operator token
     * @param operand the evaluated operand
     * @return {@link SiType#INT} or {@link SiType#FLOAT}
     * @throws RuntimeError R002 if the operand is not numeric
     */
    public static SiType checkUnaryNegate(Token op, Object operand) {
        SiType t = SiType.of(operand);
        return switch (t) {
            case INT   -> SiType.INT;
            case FLOAT -> SiType.FLOAT;
            case STRING, BOOLEAN, NULL -> throw new RuntimeError(
                RuntimeError.ERR_UNARY_TYPE,
                String.format("cannot negate %s — unary '-' requires int or float", t.keyword()),
                op);
        };
    }

    // ================================================================== //
    //  Value computation helpers                                         //
    // ================================================================== //

    /**
     * Computes the result of a binary operation given a validated
     * {@link TypeCheckResult}.
     *
     * <p>The caller must have already called {@link #checkBinary} to
     * obtain the {@code result} descriptor before calling this method.
     * This separation ensures the type check and the arithmetic are
     * never accidentally skipped.
     *
     * @param op     the operator token (for the division-by-zero token position)
     * @param left   the evaluated left operand
     * @param right  the evaluated right operand
     * @param result the {@link TypeCheckResult} from {@link #checkBinary}
     * @return the computed value
     * @throws RuntimeError R001 for division by zero
     */
    public static Object compute(Token op, Object left, Object right, TypeCheckResult result) {
        return switch (result.kind()) {

            case INT_ARITH -> {
                int l = (Integer) left;
                int r = (Integer) right;
                yield switch (op.type) {
                    case PLUS  -> l + r;
                    case MINUS -> l - r;
                    case STAR  -> l * r;
                    default    -> throw new IllegalStateException("Unexpected op: " + op.lexeme);
                };
            }

            case FLOAT_ARITH -> {
                double l = toDouble(left);
                double r = toDouble(right);
                yield switch (op.type) {
                    case PLUS  -> l + r;
                    case MINUS -> l - r;
                    case STAR  -> l * r;
                    default    -> throw new IllegalStateException("Unexpected op: " + op.lexeme);
                };
            }

            case INT_DIVISION -> {
                int divisor = (Integer) right;
                if (divisor == 0) throw RuntimeError.divisionByZero(op);
                yield (Integer) left / divisor;  // truncated toward zero
            }

            case FLOAT_DIVISION -> {
                double divisor = toDouble(right);
                if (divisor == 0.0) throw RuntimeError.divisionByZero(op);
                yield toDouble(left) / divisor;
            }

            case INT_MODULO -> {
                int divisor = (Integer) right;
                if (divisor == 0) throw RuntimeError.divisionByZero(op);
                yield (Integer) left % divisor;
            }

            case FLOAT_MODULO -> {
                double divisor = toDouble(right);
                if (divisor == 0.0) throw RuntimeError.divisionByZero(op);
                yield toDouble(left) % divisor;
            }

            case STRING_CONCAT ->
                // At least one side is already a string; stringify the other
                Stringify.of(left) + Stringify.of(right);

            case BOOL_RESULT -> {
                // Comparison operators — both operands already type-checked.
                // For numeric comparisons, promote int to double if mixed.
                double l = (left instanceof Double || right instanceof Double)
                    ? toDouble(left)
                    : (left instanceof Integer ? (double)(int)(Integer)left : 0);
                double r = (left instanceof Double || right instanceof Double)
                    ? toDouble(right)
                    : (right instanceof Integer ? (double)(int)(Integer)right : 0);

                yield switch (op.type) {
                    // Numeric ordered comparisons
                    case LESS          -> {
                        if (isNumericValue(left)) yield l < r;
                        yield false; // unreachable after type check
                    }
                    case LESS_EQUAL    -> { if (isNumericValue(left)) yield l <= r; yield false; }
                    case GREATER       -> { if (isNumericValue(left)) yield l >  r; yield false; }
                    case GREATER_EQUAL -> { if (isNumericValue(left)) yield l >= r; yield false; }

                    // Equality — works for all same-type comparisons
                    case EQUAL_EQUAL -> {
                        if (isNumericValue(left) && isNumericValue(right)) yield l == r;
                        yield left.equals(right);
                    }
                    case BANG_EQUAL -> {
                        if (isNumericValue(left) && isNumericValue(right)) yield l != r;
                        yield !left.equals(right);
                    }

                    default -> throw new IllegalStateException("Unexpected comparison op: " + op.lexeme);
                };
            }
        };
    }

    /**
     * Computes unary negation after the operand type has been validated.
     *
     * @param operand the numeric operand
     * @return negated {@link Integer} or {@link Double}
     */
    public static Object computeNegate(Object operand) {
        if (operand instanceof Integer i) return -i;
        if (operand instanceof Double  d) return -d;
        // unreachable after checkUnaryNegate
        throw new IllegalStateException("computeNegate called on non-numeric: " + operand);
    }

    // ================================================================== //
    //  Error message builders                                            //
    // ================================================================== //

    /**
     * Builds a specific R003 error message for the given operator and types.
     *
     * <p>The message names the specific illegal combination in plain English:
     * <pre>
     *   cannot subtract string from int
     *   cannot multiply boolean by float
     *   operator '+' cannot be applied to boolean and boolean
     * </pre>
     */
    private static RuntimeError binaryTypeError(Token op, SiType left, SiType right) {
        String msg = switch (op.type) {
            case MINUS ->
                String.format("cannot subtract %s from %s", right.keyword(), left.keyword());
            case STAR ->
                String.format("cannot multiply %s by %s", left.keyword(), right.keyword());
            case SLASH ->
                String.format("cannot divide %s by %s", left.keyword(), right.keyword());
            default ->  // PLUS with non-string non-numeric, or unknown op
                String.format("operator '%s' cannot be applied to %s and %s",
                    op.lexeme, left.keyword(), right.keyword());
        };
        return new RuntimeError(RuntimeError.ERR_BINARY_TYPE, msg, op);
    }

    // ================================================================== //
    //  Private helpers                                                   //
    // ================================================================== //

    /** Widens an {@link Integer} to {@code double}; passes {@link Double} through. */
    static double toDouble(Object value) {
        if (value instanceof Double  d) return d;
        if (value instanceof Integer i) return i.doubleValue();
        throw new IllegalStateException("toDouble called on: " + value);
    }

    /** Returns true if value is a numeric (non-boolean) Java number. */
    private static boolean isNumericValue(Object value) {
        return value instanceof Integer || value instanceof Double;
    }
}
