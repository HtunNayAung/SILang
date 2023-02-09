package silang;

import java.util.Objects;

/**
 * Represents a lexical token produced by the SIlang analyzer.
 *
 * <p>A token encapsulates all information about a recognized lexical unit in
 * SIlang source code, including:
 * <ul>
 *   <li>{@code type}    — The token's syntactic category ({@link TokenType})</li>
 *   <li>{@code lexeme}  — The exact character sequence from source forming this token</li>
 *   <li>{@code literal} — The semantic value for literal tokens:
 *       {@link Integer} for {@code INTEGER}, {@link Double} for {@code FLOAT},
 *       {@link String} for {@code STRING}, {@link Boolean} for {@code BOOLEAN};
 *       {@code null} for operators, keywords, and punctuation</li>
 *   <li>{@code line}    — The 1-based line number where the token begins</li>
 *   <li>{@code column}  — The 1-based column position where the token begins</li>
 * </ul>
 *
 * <p>Token instances are immutable after construction, ensuring thread safety
 * and consistent behavior throughout the compilation pipeline.
 */
public final class Token {

    /** The category of this token. Never {@code null}. */
    public final TokenType type;

    /**
     * The exact source text that produced this token.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "var"}     for a {@code VAR} token</li>
     *   <li>{@code "x"}       for an {@code IDENTIFIER} token</li>
     *   <li>{@code "42"}      for an {@code INTEGER} token</li>
     *   <li>{@code "\"hi\""}  for a {@code STRING} token (quotes included)</li>
     *   <li>{@code "+"}       for a {@code PLUS} token</li>
     *   <li>{@code ""}        for the {@code EOF} sentinel</li>
     * </ul>
     */
    public final String lexeme;

    /**
     * The parsed value carried by literal tokens; {@code null} otherwise.
     *
     * <ul>
     *   <li>{@link Integer}  — for {@code INTEGER} tokens</li>
     *   <li>{@link Double}   — for {@code FLOAT} tokens</li>
     *   <li>{@link String}   — for {@code STRING} tokens (escape sequences resolved)</li>
     *   <li>{@link Boolean}  — for {@code BOOLEAN} tokens</li>
     * </ul>
     */
    public final Object literal;

    /** 1-based line number where this token begins in the source file. */
    public final int line;

    /** 1-based column number where this token begins on its source line. */
    public final int column;

    // ------------------------------------------------------------------ //
    //  Constructor                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Constructs a new {@code Token}.
     *
     * @param type    the token category; must not be {@code null}
     * @param lexeme  the raw source text; must not be {@code null}
     * @param literal the parsed literal value, or {@code null}
     * @param line    1-based source line number
     * @param column  1-based source column number
     */
    public Token(TokenType type, String lexeme, Object literal, int line, int column) {
        this.type    = Objects.requireNonNull(type,   "type must not be null");
        this.lexeme  = Objects.requireNonNull(lexeme, "lexeme must not be null");
        this.literal = literal;
        this.line    = line;
        this.column  = column;
    }

    // ------------------------------------------------------------------ //
    //  Convenience factories                                             //
    // ------------------------------------------------------------------ //

    /**
     * Creates a token that carries no literal value (operators, keywords,
     * punctuation, and the EOF sentinel).
     */
    public static Token of(TokenType type, String lexeme, int line, int column) {
        return new Token(type, lexeme, null, line, column);
    }

    /**
     * Creates the EOF sentinel token at the given position.
     */
    public static Token eof(int line, int column) {
        return new Token(TokenType.EOF, "", null, line, column);
    }

    // ------------------------------------------------------------------ //
    //  Accessors                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Returns {@code true} if this token carries a non-null literal value.
     */
    public boolean hasLiteral() {
        return literal != null;
    }

    /**
     * Returns the literal cast to the expected type.
     *
     * @param <T>   the expected literal type
     * @param clazz the class to cast to
     * @return the literal value, or {@code null} if none
     * @throws ClassCastException if the literal is not of type {@code T}
     */
    @SuppressWarnings("unchecked")
    public <T> T literalAs(Class<T> clazz) {
        return clazz.cast(literal);
    }

    // ------------------------------------------------------------------ //
    //  Object overrides                                                  //
    // ------------------------------------------------------------------ //

    /**
     * Returns a human-readable representation of this token, useful for
     * debugging and the CLI output produced by {@code Main}.
     *
     * <p>Format:
     * <pre>
     *   TYPE lexeme               (no literal)
     *   TYPE lexeme -> literal    (with literal)
     * </pre>
     *
     * <p>Examples:
     * <pre>
     *   VAR var
     *   IDENTIFIER x
     *   INTEGER 42 -> 42
     *   FLOAT 3.14 -> 3.14
     *   STRING "Hello" -> Hello
     *   BOOLEAN true -> true
     *   PLUS +
     *   EOF
     * </pre>
     */
    @Override
    public String toString() {
        if (literal == null) {
            return String.format("%-16s %s", type, lexeme);
        }
        return String.format("%-16s %-12s -> %s", type, lexeme, literal);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Token other)) return false;
        return type == other.type
            && lexeme.equals(other.lexeme)
            && Objects.equals(literal, other.literal)
            && line   == other.line
            && column == other.column;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lexeme, literal, line, column);
    }
}
