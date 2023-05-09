package silang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-pass lexer (tokenizer) for SIlang Version 0.1.
 *
 * <p>The lexer converts raw SIlang source text into a flat, ordered
 * {@link List} of {@link Token} objects.  The final token in the list is
 * always {@link TokenType#EOF}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Lexer lexer = new Lexer(sourceCode, "hello.si");
 * List<Token> tokens = lexer.scanTokens();
 * }</pre>
 *
 * <h2>Error handling</h2>
 * <p>The lexer uses a <em>panic-and-continue</em> strategy: when an invalid
 * character or unterminated literal is detected, a {@link LexerError} is
 * collected and scanning resumes from the next character.  After the full
 * source has been processed, if any errors were collected the lexer throws a
 * {@link LexerErrorCollection} that bundles all of them so the caller can
 * display every problem at once rather than one at a time.
 *
 * <h2>Design notes (forward-compatibility)</h2>
 * <ul>
 *   <li>The {@code KEYWORDS} map already reserves all future SIlang keywords;
 *       adding new keywords in later versions requires only adding entries to
 *       that map, not changing the scanning logic.</li>
 *   <li>The {@code scanToken()} dispatch uses a {@code switch} on the first
 *       character.  Single- vs. multi-character operators (e.g. {@code =} vs.
 *       {@code ==}) are resolved with {@link #matchNext(char)}, which reads
 *       one character of lookahead without consuming it on a mismatch.
 *       Adding {@code ==}, {@code !=}, {@code <=}, {@code >=} in Version 0.2
 *       only requires new {@code case} arms.</li>
 *   <li>The {@code line} and {@code column} counters are maintained throughout
 *       whitespace, comments, and multi-line strings so that all error messages
 *       and token positions are accurate.</li>
 * </ul>
 */
// v0.3 — fn keyword active, and/or keyword aliases added
public final class Lexer {

    // ------------------------------------------------------------------ //
    //  Reserved keywords                                                 //
    // ------------------------------------------------------------------ //

    /**
     * Maps every reserved word to its {@link TokenType}.
     *
     * <p>All future keywords are included here so that the lexer immediately
     * rejects them as identifiers even before the corresponding language
     * features are implemented.  The parser for Version 0.1 will simply never
     * consume those token types.
     */
    private static final Map<String, TokenType> KEYWORDS;

    static {
        Map<String, TokenType> kw = new HashMap<>();

        // v0.1 keywords
        kw.put("var",        TokenType.VAR);
        kw.put("true",       TokenType.BOOLEAN);
        kw.put("false",      TokenType.BOOLEAN);

        // Future keywords — reserved now to prevent identifier clashes
        kw.put("class",      TokenType.CLASS);
        kw.put("interface",  TokenType.INTERFACE);
        kw.put("extends",    TokenType.EXTENDS);
        kw.put("implements", TokenType.IMPLEMENTS);
        kw.put("new",        TokenType.NEW);
        kw.put("this",       TokenType.THIS);
        kw.put("super",      TokenType.SUPER);
        kw.put("return",     TokenType.RETURN);
        kw.put("if",         TokenType.IF);
        kw.put("else",       TokenType.ELSE);
        kw.put("while",      TokenType.WHILE);
        kw.put("for",        TokenType.FOR);
        kw.put("fun",        TokenType.FUN);   // reserved alias
        kw.put("fn",         TokenType.FUN);   // SILang active function keyword
        kw.put("null",       TokenType.NULL);
        kw.put("static",     TokenType.STATIC);
        kw.put("public",     TokenType.PUBLIC);
        kw.put("private",    TokenType.PRIVATE);
        kw.put("protected",  TokenType.PROTECTED);
        kw.put("import",     TokenType.IMPORT);
        kw.put("module",     TokenType.MODULE);
        kw.put("and",        TokenType.AND);   // keyword alias for &&
        kw.put("or",         TokenType.OR);    // keyword alias for ||

        KEYWORDS = Collections.unmodifiableMap(kw);
    }

    // ------------------------------------------------------------------ //
    //  State                                                             //
    // ------------------------------------------------------------------ //

    /** Complete source text being scanned. */
    private final String source;

    /**
     * Name of the source file (used in error messages).
     * Defaults to {@code "<input>"} for in-memory sources.
     */
    private final String fileName;

    /** Accumulated token list — built up during {@link #scanTokens()}. */
    private final List<Token> tokens = new ArrayList<>();

    /** Accumulated non-fatal errors — reported together after scanning. */
    private final List<LexerError> errors = new ArrayList<>();

    /** Lines of source text split on {@code '\n'} — used for error snippets. */
    private final String[] sourceLines;

    /**
     * Index into {@link #source} marking the start of the token currently
     * being scanned.
     */
    private int start = 0;

    /**
     * Index into {@link #source} pointing at the next character to be
     * consumed.
     */
    private int current = 0;

    /** 1-based line counter. Incremented every time a {@code '\n'} is consumed. */
    private int line = 1;

    /**
     * 1-based column counter.  Tracks the column of {@link #current} — i.e.
     * the column of the <em>next</em> character to be read.  The column at
     * which a token <em>starts</em> is captured into {@link #tokenStartCol}
     * before advancing.
     */
    private int column = 1;

    /** Column at which the current token started (captured in {@link #advance()}). */
    private int tokenStartCol = 1;

    /** Line at which the current token started. */
    private int tokenStartLine = 1;

    // ------------------------------------------------------------------ //
    //  Constructor                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Creates a lexer for the given source text.
     *
     * @param source   the complete SIlang source to lex; must not be {@code null}
     * @param fileName the source-file name for error messages
     */
    public Lexer(String source, String fileName) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        this.source      = source;
        this.fileName    = (fileName != null && !fileName.isBlank()) ? fileName : "<input>";
        this.sourceLines = source.split("\n", -1);
    }

    /**
     * Creates a lexer for an in-memory source string with a generic file name.
     */
    public Lexer(String source) {
        this(source, "<input>");
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Scans the entire source and returns the resulting token list.
     *
     * <p>The last element of the returned list is always an {@link TokenType#EOF}
     * token.
     *
     * @return immutable-view list of tokens
     * @throws LexerErrorCollection if one or more lexical errors were found
     */
    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            // Mark the beginning of the next token
            start          = current;
            tokenStartLine = line;
            tokenStartCol  = column;
            scanToken();
        }

        tokens.add(Token.eof(line, column));

        if (!errors.isEmpty()) {
            throw new LexerErrorCollection(errors);
        }

        return Collections.unmodifiableList(tokens);
    }

    // ------------------------------------------------------------------ //
    //  Core scanning loop                                                //
    // ------------------------------------------------------------------ //

    /**
     * Scans a single token starting at the current position and adds it to
     * {@link #tokens}, or records a {@link LexerError} if the character is
     * not recognised.
     */
    private void scanToken() {
        char c = advance();

        switch (c) {

            // ----------------------------------------------------------
            //  Single-character operators and punctuation
            // ----------------------------------------------------------
            case '+' -> addToken(TokenType.PLUS);
            case '-' -> addToken(TokenType.MINUS);
            case '*' -> addToken(TokenType.STAR);
            case ',' -> addToken(TokenType.COMMA);
            case '(' -> addToken(TokenType.LPAREN);
            case ')' -> addToken(TokenType.RPAREN);

            // Braces — active from v0.2 (control flow blocks)
            case '{' -> addToken(TokenType.LBRACE);
            case '}' -> addToken(TokenType.RBRACE);
            // Future punctuation — uncomment when parser is ready
            // case '[' -> addToken(TokenType.LBRACKET);
            // case ']' -> addToken(TokenType.RBRACKET);
            case ';' -> addToken(TokenType.SEMICOLON);  // active from v0.3 (for loops)
            // case ':' -> addToken(TokenType.COLON);

            // ----------------------------------------------------------
            //  '/'  — could start a line comment, block comment, or SLASH
            // ----------------------------------------------------------
            case '/' -> scanSlashOrComment();

            // ----------------------------------------------------------
            //  '='  — could be '=' (assign) or '==' (equality, future)
            // ----------------------------------------------------------
            case '=' -> {
                if (matchNext('=')) {
                    addToken(TokenType.EQUAL_EQUAL); // future
                } else {
                    addToken(TokenType.EQUAL);
                }
            }

            // ----------------------------------------------------------
            //  Future two-character operators  (recognised by lexer but
            //  will cause a parse error in v0.1 if used in source)
            // ----------------------------------------------------------
            case '!' -> {
                if (matchNext('=')) {
                    addToken(TokenType.BANG_EQUAL);
                } else {
                    addToken(TokenType.BANG);
                }
            }
            case '<' -> {
                if (matchNext('=')) {
                    addToken(TokenType.LESS_EQUAL);
                } else {
                    addToken(TokenType.LESS);
                }
            }
            case '>' -> {
                if (matchNext('=')) {
                    addToken(TokenType.GREATER_EQUAL);
                } else {
                    addToken(TokenType.GREATER);
                }
            }
            case '&' -> {
                if (matchNext('&')) addToken(TokenType.AND);
                else recordError(LexerError.unexpectedChar(c, fileName, tokenStartLine,
                    tokenStartCol, currentSourceLine()));
            }
            case '|' -> {
                if (matchNext('|')) addToken(TokenType.OR);
                else recordError(LexerError.unexpectedChar(c, fileName, tokenStartLine,
                    tokenStartCol, currentSourceLine()));
            }

            // ----------------------------------------------------------
            //  Whitespace — skip, but maintain line/column counters
            //  (newlines are already counted inside advance())
            // ----------------------------------------------------------
            case ' ', '\t', '\r', '\n' -> { /* consumed, counters already updated */ }

            // ----------------------------------------------------------
            //  String literals
            // ----------------------------------------------------------
            case '"' -> scanString();

            // ----------------------------------------------------------
            //  Everything else: numbers, identifiers, or errors
            // ----------------------------------------------------------
            default -> {
                if (isDigit(c)) {
                    scanNumber(c);
                } else if (isLetter(c)) {
                    scanIdentifierOrKeyword();
                } else {
                    recordError(LexerError.unexpectedChar(
                        c, fileName, tokenStartLine, tokenStartCol, currentSourceLine()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Token-specific scanners                                           //
    // ------------------------------------------------------------------ //

    /**
     * Called after consuming {@code '/'}.  Determines whether this is a
     * line comment ({@code //}), a block comment ({@code /* … *}{@code /}),
     * or a plain {@code SLASH} token.
     */
    private void scanSlashOrComment() {
        if (matchNext('/')) {
            // Line comment — consume everything up to (but not including) '\n'
            while (!isAtEnd() && peek() != '\n') {
                advance();
            }
            // The '\n' is consumed on the next iteration of the main loop
        } else if (matchNext('*')) {
            // Block comment — consume until '*/'
            scanBlockComment();
        } else {
            addToken(TokenType.SLASH);
        }
    }

    /**
     * Scans the body of a block comment, expecting {@code *}{@code /} as
     * terminator.  Handles multi-line comments, updating line and column
     * counters appropriately.
     *
     * <p>If EOF is reached before the terminator an {@code L002} error is
     * recorded.
     */
    private void scanBlockComment() {
        int startLine = tokenStartLine;
        int startCol  = tokenStartCol;

        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance(); // consume '*'
                advance(); // consume '/'
                return;    // comment closed cleanly
            }
            advance();     // keep consuming (newlines handled inside advance())
        }

        // EOF reached before '*/'
        recordError(LexerError.unterminatedComment(fileName, startLine, startCol));
    }

    /**
     * Scans a string literal.  The opening {@code '"'} has already been
     * consumed when this method is called.
     *
     * <p>Escape sequences ({@code \"}, {@code \\}, {@code \n}, {@code \t},
     * {@code \r}) are resolved and stored in the {@code literal} field.
     *
     * <p>An unterminated string (EOF or newline before closing quote)
     * produces an {@code L001} error.
     */
    private void scanString() {
        int          strStartLine = tokenStartLine;
        int          strStartCol  = tokenStartCol;
        StringBuilder value       = new StringBuilder();

        while (!isAtEnd()) {
            char c = peek();

            if (c == '"') {
                advance(); // consume closing '"'
                // The lexeme includes both enclosing quotes
                String lexeme = source.substring(start, current);
                addTokenWithLiteral(TokenType.STRING, lexeme, value.toString());
                return;
            }

            if (c == '\n') {
                // Newlines inside strings are disallowed in SIlang v0.1
                recordError(LexerError.unterminatedString(
                    fileName, strStartLine, strStartCol, currentSourceLine()));
                return;
            }

            if (c == '\\') {
                advance(); // consume '\'
                if (isAtEnd()) break;
                char escaped = advance();
                switch (escaped) {
                    case '"'  -> value.append('"');
                    case '\\' -> value.append('\\');
                    case 'n'  -> value.append('\n');
                    case 't'  -> value.append('\t');
                    case 'r'  -> value.append('\r');
                    default   -> recordError(LexerError.invalidEscape(
                        escaped, fileName, line, column - 1, currentSourceLine()));
                }
            } else {
                value.append(advance());
            }
        }

        // Reached EOF without closing '"'
        recordError(LexerError.unterminatedString(
            fileName, strStartLine, strStartCol, currentSourceLine()));
    }

    /**
     * Scans a numeric literal (integer or float).  The first digit {@code c}
     * has already been consumed.
     *
     * <p>A {@code '.'} followed by at least one digit promotes the token
     * from {@code INTEGER} to {@code FLOAT}.  A lone {@code '.'} with no
     * following digit leaves the integer as-is (the dot will be the next
     * token — future member-access operator).
     */
    private void scanNumber(char firstDigit) {
        // Consume remaining integer digits
        while (!isAtEnd() && isDigit(peek())) {
            advance();
        }

        // Check for fractional part: '.' followed by at least one digit
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // consume '.'
            while (!isAtEnd() && isDigit(peek())) {
                advance();
            }
            String lexeme = source.substring(start, current);
            addTokenWithLiteral(TokenType.FLOAT, lexeme, Double.parseDouble(lexeme));
        } else {
            String lexeme = source.substring(start, current);
            addTokenWithLiteral(TokenType.INTEGER, lexeme, Integer.parseInt(lexeme));
        }
    }

    /**
     * Scans an identifier or keyword.  The first character has already been
     * consumed; this method consumes all subsequent letters, digits, and
     * underscores.
     *
     * <p>After extracting the full word, the {@link #KEYWORDS} map is
     * consulted.  If the word is a known keyword the appropriate token type
     * is used; otherwise {@link TokenType#IDENTIFIER} is emitted.
     *
     * <p>Boolean literals ({@code true}/{@code false}) receive a
     * {@link Boolean} as their literal value.
     */
    private void scanIdentifierOrKeyword() {
        while (!isAtEnd() && isIdentifierPart(peek())) {
            advance();
        }

        String word = source.substring(start, current);
        TokenType type = KEYWORDS.getOrDefault(word, TokenType.IDENTIFIER);

        if (type == TokenType.BOOLEAN) {
            boolean boolValue = word.equals("true");
            addTokenWithLiteral(type, word, boolValue);
        } else {
            // Identifiers and non-literal keywords carry no literal value
            addToken(type);
        }
    }

    // ------------------------------------------------------------------ //
    //  Character-level helpers                                           //
    // ------------------------------------------------------------------ //

    /**
     * Returns {@code true} if all source characters have been consumed.
     */
    private boolean isAtEnd() {
        return current >= source.length();
    }

    /**
     * Consumes and returns the current character, advancing the cursor and
     * updating line/column counters.
     *
     * <p>When a {@code '\n'} is consumed, {@code line} is incremented and
     * {@code column} is reset to {@code 1}.  For all other characters,
     * {@code column} is incremented by one.
     */
    private char advance() {
        char c = source.charAt(current++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    /**
     * Returns the current character <em>without</em> consuming it (1-char
     * lookahead).  Returns {@code '\0'} at EOF.
     */
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    /**
     * Returns the character after the current one <em>without</em> consuming
     * either (2-char lookahead).  Returns {@code '\0'} if not available.
     */
    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    /**
     * Conditionally consumes the current character if it matches {@code expected}.
     *
     * @return {@code true} if the character was consumed; {@code false} otherwise
     */
    private boolean matchNext(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        advance();
        return true;
    }

    // ------------------------------------------------------------------ //
    //  Character classification                                          //
    // ------------------------------------------------------------------ //

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** A letter is any ASCII a-z or A-Z. */
    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Returns {@code true} for characters that may appear after the first
     * character of an identifier (letters, digits, underscore).
     */
    private static boolean isIdentifierPart(char c) {
        return isLetter(c) || isDigit(c) || c == '_';
    }

    // ------------------------------------------------------------------ //
    //  Token emission                                                    //
    // ------------------------------------------------------------------ //

    /**
     * Adds a token with no literal value, using the text from
     * {@link #start} to {@link #current} as the lexeme.
     */
    private void addToken(TokenType type) {
        String lexeme = source.substring(start, current);
        tokens.add(new Token(type, lexeme, null, tokenStartLine, tokenStartCol));
    }

    /**
     * Adds a token with an explicit lexeme and literal value.
     */
    private void addTokenWithLiteral(TokenType type, String lexeme, Object literal) {
        tokens.add(new Token(type, lexeme, literal, tokenStartLine, tokenStartCol));
    }

    // ------------------------------------------------------------------ //
    //  Error helpers                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Collects a non-fatal {@link LexerError} so scanning can continue.
     */
    private void recordError(LexerError error) {
        errors.add(error);
    }

    /**
     * Returns the source line at {@link #line} (1-based), used when
     * constructing error snippets.  Falls back to an empty string if the
     * line index is out of range.
     */
    private String currentSourceLine() {
        int idx = line - 1;
        if (idx >= 0 && idx < sourceLines.length) {
            return sourceLines[idx];
        }
        return "";
    }

    // ------------------------------------------------------------------ //
    //  Nested error-collection type                                      //
    // ------------------------------------------------------------------ //

    /**
     * Thrown when one or more {@link LexerError}s were accumulated during
     * scanning.  Bundles all errors so callers can report every problem at
     * once.
     */
    public static final class LexerErrorCollection extends RuntimeException {

        private final List<LexerError> errors;

        LexerErrorCollection(List<LexerError> errors) {
            super(errors.size() + " lexer error(s) found");
            this.errors = List.copyOf(errors);
        }

        /** Returns an unmodifiable view of all accumulated errors. */
        public List<LexerError> getErrors() {
            return errors;
        }

        /** Prints each error's full diagnostic to {@link System#err}. */
        public void printAll() {
            for (LexerError err : errors) {
                System.err.println(err.formatDiagnostic());
                System.err.println();
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(getMessage()).append('\n');
            for (LexerError e : errors) {
                sb.append(e.formatDiagnostic()).append("\n\n");
            }
            return sb.toString().stripTrailing();
        }
    }
}
