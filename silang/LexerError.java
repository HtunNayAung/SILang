package silang;

/**
 * Thrown by the SIlang lexer when it encounters source text that cannot be
 * converted into a valid token.
 *
 * <p>Every {@code LexerError} records the exact source position (file, line,
 * column) where the problem was detected so that the compiler can display a
 * useful diagnostic message.
 *
 * <h2>Error Codes</h2>
 * <p>Each distinct error condition has a short code of the form
 * {@code L<nnn>}, mirroring the error-code convention in the SIlang spec:
 * <ul>
 *   <li>{@code L001} — Unterminated string literal</li>
 *   <li>{@code L002} — Unterminated block comment</li>
 *   <li>{@code L003} — Invalid / unexpected character</li>
 *   <li>{@code L004} — Invalid escape sequence inside string</li>
 * </ul>
 *
 * <h2>Sample output</h2>
 * <pre>
 * error[L001]: unterminated string literal
 *   --> hello.si:3:5
 *    |
 *  3 | var x = "Hello
 *    |         ^ string started here, never closed
 * </pre>
 */
public final class LexerError extends RuntimeException {

    // ------------------------------------------------------------------ //
    //  Error codes                                                       //
    // ------------------------------------------------------------------ //

    public static final String ERR_UNTERMINATED_STRING  = "L001";
    public static final String ERR_UNTERMINATED_COMMENT = "L002";
    public static final String ERR_UNEXPECTED_CHAR      = "L003";
    public static final String ERR_INVALID_ESCAPE       = "L004";

    // ------------------------------------------------------------------ //
    //  Fields                                                            //
    // ------------------------------------------------------------------ //

    /** The error code, e.g. {@code "L001"}. */
    private final String errorCode;

    /** Source file name (may be {@code "<input>"} for in-memory sources). */
    private final String fileName;

    /** 1-based line number where the error was detected. */
    private final int line;

    /** 1-based column number where the error was detected. */
    private final int column;

    /** The raw source line on which the error occurred (for display). */
    private final String sourceLine;

    // ------------------------------------------------------------------ //
    //  Constructors                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Constructs a {@code LexerError} with full diagnostic context.
     *
     * @param errorCode  the short code identifying the error class (e.g. {@code "L001"})
     * @param message    a human-readable description of the problem
     * @param fileName   the source file being lexed
     * @param line       1-based line number
     * @param column     1-based column number
     * @param sourceLine the raw text of the offending source line (used in display)
     */
    public LexerError(
            String errorCode,
            String message,
            String fileName,
            int    line,
            int    column,
            String sourceLine) {
        super(message);
        this.errorCode  = errorCode;
        this.fileName   = fileName;
        this.line       = line;
        this.column     = column;
        this.sourceLine = sourceLine;
    }

    /**
     * Convenience constructor without a source-line snippet.
     */
    public LexerError(
            String errorCode,
            String message,
            String fileName,
            int    line,
            int    column) {
        this(errorCode, message, fileName, line, column, "");
    }

    // ------------------------------------------------------------------ //
    //  Factory methods                                                   //
    // ------------------------------------------------------------------ //

    /** Creates an {@code L001} error for an unterminated string literal. */
    public static LexerError unterminatedString(
            String fileName, int line, int column, String sourceLine) {
        return new LexerError(
            ERR_UNTERMINATED_STRING,
            "unterminated string literal",
            fileName, line, column, sourceLine);
    }

    /** Creates an {@code L002} error for an unterminated block comment. */
    public static LexerError unterminatedComment(
            String fileName, int line, int column) {
        return new LexerError(
            ERR_UNTERMINATED_COMMENT,
            "unterminated block comment",
            fileName, line, column);
    }

    /** Creates an {@code L003} error for an unrecognised source character. */
    public static LexerError unexpectedChar(
            char ch, String fileName, int line, int column, String sourceLine) {
        String msg = String.format(
            "unexpected character '%s' (U+%04X)", ch, (int) ch);
        return new LexerError(
            ERR_UNEXPECTED_CHAR,
            msg,
            fileName, line, column, sourceLine);
    }

    /** Creates an {@code L004} error for an invalid escape sequence. */
    public static LexerError invalidEscape(
            char seq, String fileName, int line, int column, String sourceLine) {
        String msg = String.format("invalid escape sequence '\\%c'", seq);
        return new LexerError(
            ERR_INVALID_ESCAPE,
            msg,
            fileName, line, column, sourceLine);
    }

    // ------------------------------------------------------------------ //
    //  Accessors                                                         //
    // ------------------------------------------------------------------ //

    public String getErrorCode()  { return errorCode;  }
    public String getFileName()   { return fileName;   }
    public int    getLine()       { return line;        }
    public int    getColumn()     { return column;      }
    public String getSourceLine() { return sourceLine;  }

    // ------------------------------------------------------------------ //
    //  Formatted diagnostic                                              //
    // ------------------------------------------------------------------ //

    /**
     * Returns a compiler-style diagnostic string modelled on the format
     * shown in the SIlang specification, e.g.:
     *
     * <pre>
     * error[L001]: unterminated string literal
     *   --> hello.si:3:5
     *    |
     *  3 | var x = "Hello
     *    |         ^
     * </pre>
     */
    public String formatDiagnostic() {
        StringBuilder sb = new StringBuilder();

        // Header line
        sb.append(String.format("error[%s]: %s%n", errorCode, getMessage()));

        // Location line
        sb.append(String.format("  --> %s:%d:%d%n", fileName, line, column));

        // Source snippet (only when available)
        if (sourceLine != null && !sourceLine.isEmpty()) {
            String lineLabel = String.valueOf(line);
            String pad       = " ".repeat(lineLabel.length());

            sb.append(String.format("   %s |%n", pad));
            sb.append(String.format(" %s  | %s%n", lineLabel, sourceLine));
            sb.append(String.format("   %s | %s^%n", pad, " ".repeat(Math.max(0, column - 1))));
        }

        return sb.toString().stripTrailing();
    }

    /**
     * Overrides the default message to include the position prefix so that
     * un-caught errors still show useful context in stack traces.
     */
    @Override
    public String toString() {
        return String.format("LexerError(%s) at %s:%d:%d — %s",
            errorCode, fileName, line, column, getMessage());
    }
}
