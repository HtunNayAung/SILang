package silang.parser;

import silang.Token;
import silang.TokenType;

/**
 * Thrown by the SIlang parser when the token stream does not conform to
 * the SIlang grammar.
 *
 * <p>{@code ParseError} is a <em>checked-style</em> exception that is
 * caught inside the parser itself to implement error recovery (panic-mode
 * synchronization).  It is also exposed to callers so they can format
 * and report diagnostics.
 *
 * <h2>Error codes</h2>
 * <pre>
 *   P001  unexpected token — expected something else
 *   P002  expected expression — token cannot begin an expression
 *   P003  missing closing paren in grouping
 *   P004  missing closing paren in function call
 *   P005  too many arguments in function call (> MAX_ARGS)
 *   P006  missing '=' in variable declaration
 *   P007  missing '(' after 'if' or 'while'
 *   P008  missing ')' after condition in 'if' or 'while'
 *   P009  missing '{' to begin block
 *   P010  missing '}' to close block
 *   P011  missing '=' in assignment statement
 * </pre>
 *
 * <h2>Sample diagnostics</h2>
 * <pre>
 * error[P001]: expected '=' after variable name
 *   --> hello.si:5:9
 *    |
 *  5 | var price 3.14
 *    |           ^ expected '='
 *
 * error[P002]: expected expression
 *   --> hello.si:7:1
 *    |
 *  7 | @bad
 *    | ^ unexpected token '@bad'
 * </pre>
 */
// v0.2 — P007-P011 added for control flow
public final class ParseError extends RuntimeException {

    // ------------------------------------------------------------------ //
    //  Error codes                                                       //
    // ------------------------------------------------------------------ //

    public static final String ERR_UNEXPECTED_TOKEN    = "P001";
    public static final String ERR_EXPECTED_EXPRESSION = "P002";
    public static final String ERR_MISSING_RPAREN_GROUP= "P003";
    public static final String ERR_MISSING_RPAREN_CALL = "P004";
    public static final String ERR_TOO_MANY_ARGS       = "P005";
    public static final String ERR_MISSING_EQUAL       = "P006";
    public static final String ERR_MISSING_LPAREN_COND = "P007";
    public static final String ERR_MISSING_RPAREN_COND = "P008";
    public static final String ERR_MISSING_LBRACE      = "P009";
    public static final String ERR_MISSING_RBRACE      = "P010";
    public static final String ERR_MISSING_EQUAL_ASSIGN= "P011";

    /** Maximum number of arguments a call expression may have (spec §Built-in Functions). */
    public static final int MAX_ARGS = 255;

    // ------------------------------------------------------------------ //
    //  Fields                                                            //
    // ------------------------------------------------------------------ //

    /** Short error code, e.g. {@code "P001"}. */
    private final String    errorCode;

    /** The offending token that triggered the error. */
    private final Token     token;

    // ------------------------------------------------------------------ //
    //  Constructor                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Constructs a new {@code ParseError}.
     *
     * @param errorCode the short diagnostic code
     * @param message   human-readable description of the problem
     * @param token     the token at which the error was detected
     */
    public ParseError(String errorCode, String message, Token token) {
        super(message);
        this.errorCode = errorCode;
        this.token     = token;
    }

    // ------------------------------------------------------------------ //
    //  Factory methods                                                   //
    // ------------------------------------------------------------------ //

    /** Creates a {@code P001} error for a mismatched token. */
    public static ParseError unexpectedToken(Token found, String expected) {
        String msg = String.format(
            "expected %s but found '%s'", expected, tokenDescription(found));
        return new ParseError(ERR_UNEXPECTED_TOKEN, msg, found);
    }

    /** Creates a {@code P002} error when no expression could be parsed. */
    public static ParseError expectedExpression(Token found) {
        String msg = String.format(
            "expected expression but found '%s'", tokenDescription(found));
        return new ParseError(ERR_EXPECTED_EXPRESSION, msg, found);
    }

    /** Creates a {@code P003} error for a missing closing paren in a grouping. */
    public static ParseError missingRParenGroup(Token found) {
        String msg = String.format(
            "expected ')' after grouped expression but found '%s'",
            tokenDescription(found));
        return new ParseError(ERR_MISSING_RPAREN_GROUP, msg, found);
    }

    /** Creates a {@code P004} error for a missing closing paren in a call. */
    public static ParseError missingRParenCall(Token found) {
        String msg = String.format(
            "expected ')' after argument list but found '%s'",
            tokenDescription(found));
        return new ParseError(ERR_MISSING_RPAREN_CALL, msg, found);
    }

    /** Creates a {@code P005} error when argument count exceeds the limit. */
    public static ParseError tooManyArguments(Token paren) {
        String msg = String.format(
            "cannot have more than %d arguments in a single call", MAX_ARGS);
        return new ParseError(ERR_TOO_MANY_ARGS, msg, paren);
    }

    /** Creates a {@code P006} error for a missing '=' in a var declaration. */
    public static ParseError missingEqual(Token found) {
        String msg = String.format(
            "expected '=' after variable name in declaration but found '%s'",
            tokenDescription(found));
        return new ParseError(ERR_MISSING_EQUAL, msg, found);
    }

    /** Creates a {@code P007} error for a missing '(' after 'if' or 'while'. */
    public static ParseError missingLParenCondition(Token keyword, Token found) {
        String msg = String.format(
            "expected '(' after '%s' but found '%s'",
            keyword.lexeme, tokenDescription(found));
        return new ParseError(ERR_MISSING_LPAREN_COND, msg, found);
    }

    /** Creates a {@code P008} error for a missing ')' after the condition. */
    public static ParseError missingRParenCondition(Token keyword, Token found) {
        String msg = String.format(
            "expected ')' after '%s' condition but found '%s'",
            keyword.lexeme, tokenDescription(found));
        return new ParseError(ERR_MISSING_RPAREN_COND, msg, found);
    }

    /** Creates a {@code P009} error for a missing '{' to open a block. */
    public static ParseError missingLBrace(Token found) {
        String msg = String.format(
            "expected '{' to begin block but found '%s'",
            tokenDescription(found));
        return new ParseError(ERR_MISSING_LBRACE, msg, found);
    }

    /** Creates a {@code P010} error for a missing '}' to close a block. */
    public static ParseError missingRBrace(Token found) {
        String msg = String.format(
            "expected '}' to close block but found '%s'",
            tokenDescription(found));
        return new ParseError(ERR_MISSING_RBRACE, msg, found);
    }

    /** Creates a {@code P011} error for a missing '=' in an assignment. */
    public static ParseError missingEqualAssign(Token name, Token found) {
        String msg = String.format(
            "expected '=' after '%s' in assignment but found '%s'",
            name.lexeme, tokenDescription(found));
        return new ParseError(ERR_MISSING_EQUAL_ASSIGN, msg, found);
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
     * Returns a formatted diagnostic string matching the SIlang spec style.
     *
     * @param fileName the source file name (for the {@code -->} line)
     * @param sourceLine the raw text of the offending source line
     * @return the formatted multi-line diagnostic
     */
    public String formatDiagnostic(String fileName, String sourceLine) {
        int line = token.line;
        int col  = token.column;
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

    /**
     * Short one-line description suitable for inline error lists.
     */
    @Override
    public String toString() {
        return String.format("ParseError(%s) at %d:%d — %s",
            errorCode, token.line, token.column, getMessage());
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                   //
    // ------------------------------------------------------------------ //

    /**
     * Returns a human-friendly description of a token for error messages.
     */
    private static String tokenDescription(Token t) {
        if (t.type == TokenType.EOF)        return "end of file";
        if (t.type == TokenType.IDENTIFIER) return "identifier '" + t.lexeme + "'";
        return "'" + t.lexeme + "'";
    }
}
