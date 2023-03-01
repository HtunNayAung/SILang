package silang.parser;

import silang.Token;
import silang.TokenType;
import silang.ast.Expr;
import silang.ast.Stmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Recursive-descent parser for SIlang Version 0.1.
 *
 * <p>Consumes the flat token stream produced by {@link silang.Lexer} and
 * builds a typed AST ({@link Stmt} / {@link Expr} tree) that represents the
 * structure of the entire source program.
 *
 * <h2>Grammar implemented (SIlang v0.1)</h2>
 * <pre>
 *   program      →  statement* EOF
 *
 *   statement    →  variableDecl
 *                |  exprStatement
 *
 *   variableDecl →  "var" IDENTIFIER "=" expression
 *   exprStatement→  expression
 *
 *   expression   →  term ( ( "+" | "-" ) term )*
 *   term         →  unary ( ( "*" | "/" ) unary )*
 *   unary        →  "-" unary | primary
 *   primary      →  INTEGER | FLOAT | STRING | BOOLEAN
 *                |  IDENTIFIER ( "(" argumentList ")" )?
 *                |  "(" expression ")"
 *
 *   argumentList →  ( expression ( "," expression )* )?
 * </pre>
 *
 * <h2>Operator precedence (lowest → highest)</h2>
 * <pre>
 *   1. Additive    +  -      (left-associative, parsed in expression())
 *   2. Multiplicative  *  /  (left-associative, parsed in term())
 *   3. Unary        -        (right-associative, parsed in unary())
 *   4. Primary      literals, variables, calls, groupings
 * </pre>
 *
 * <h2>Error recovery</h2>
 * <p>The parser uses <em>panic-mode synchronization</em>: when a
 * {@link ParseError} is thrown, it is caught in {@link #declaration()} and
 * the parser skips tokens until it reaches what looks like the start of the
 * next statement.  This lets a single parse pass report multiple errors
 * rather than stopping at the first one.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * List<Token>  tokens = new Lexer(source, fileName).scanTokens();
 * List<Stmt>   ast    = new Parser(tokens, fileName).parse();
 * }</pre>
 *
 * <h2>Forward-compatibility</h2>
 * <p>The method structure mirrors the grammar directly.  Future versions add:
 * <ul>
 *   <li>{@code ifStatement()}    — v0.2</li>
 *   <li>{@code whileStatement()} — v0.2</li>
 *   <li>{@code block()}          — v0.2</li>
 *   <li>{@code returnStatement()}— v0.3</li>
 *   <li>{@code function()}       — v0.3</li>
 *   <li>{@code classDeclaration()}— v0.5</li>
 * </ul>
 * None of these require changes to existing methods.
 */
public final class Parser {

    // ------------------------------------------------------------------ //
    //  State                                                             //
    // ------------------------------------------------------------------ //

    /** Immutable token stream from the lexer. The last element is always EOF. */
    private final List<Token>  tokens;

    /**
     * Source file name — stored for error messages.
     * Defaults to {@code "<unknown>"} if not provided.
     */
    private final String       fileName;

    /** Index of the token that will be returned by the next {@link #peek()}. */
    private int                current = 0;

    /**
     * Errors collected during error-recovery parsing.
     * Non-empty after {@link #parse()} only if the grammar was violated but
     * recovery was possible.
     */
    private final List<ParseError> errors = new ArrayList<>();

    // ------------------------------------------------------------------ //
    //  Construction                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Constructs a parser over the given token list.
     *
     * @param tokens   token stream from the lexer; must end with {@code EOF}
     * @param fileName source file name for error messages
     */
    public Parser(List<Token> tokens, String fileName) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("token list must not be null or empty");
        }
        this.tokens   = tokens;
        this.fileName = (fileName != null && !fileName.isBlank()) ? fileName : "<unknown>";
    }

    /** Convenience constructor that uses {@code "<unknown>"} as the file name. */
    public Parser(List<Token> tokens) {
        this(tokens, "<unknown>");
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Parses the entire token stream and returns the list of top-level
     * statements that make up the program.
     *
     * <p>If any parse errors occurred and could not be recovered from, a
     * {@link ParseErrorCollection} is thrown after collecting as many errors
     * as possible.  If the parse succeeds the returned list is never
     * {@code null} (but may be empty for an empty source file).
     *
     * @return unmodifiable list of top-level {@link Stmt} nodes
     * @throws ParseErrorCollection if one or more unrecoverable parse errors occurred
     */
    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();

        while (!isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        if (!errors.isEmpty()) {
            throw new ParseErrorCollection(errors, fileName);
        }

        return Collections.unmodifiableList(statements);
    }

    /**
     * Returns all {@link ParseError}s that were recovered from during the
     * last call to {@link #parse()}.
     */
    public List<ParseError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    // ================================================================== //
    //  Grammar rules — top-level                                         //
    // ================================================================== //

    /**
     * Attempts to parse one statement; on {@link ParseError} synchronizes
     * to the next statement boundary (panic-mode recovery).
     *
     * <pre>
     *   declaration → variableDecl | statement
     * </pre>
     *
     * @return the parsed statement, or {@code null} if recovery skipped it
     */
    private Stmt declaration() {
        try {
            if (check(TokenType.VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            errors.add(error);
            synchronize();
            return null;
        }
    }

    /**
     * Parses a variable declaration.
     *
     * <pre>
     *   variableDecl → "var" IDENTIFIER "=" expression
     * </pre>
     *
     * @return a {@link Stmt.Var} node
     * @throws ParseError if the syntax is malformed
     */
    private Stmt varDeclaration() {
        consume(TokenType.VAR, "expected 'var'");          // consume 'var'
        Token name = consume(TokenType.IDENTIFIER,
                             "expected variable name after 'var'");

        // Enforce the '=' — v0.1 has no uninitialized declarations
        if (!check(TokenType.EQUAL)) {
            throw ParseError.missingEqual(peek());
        }
        consume(TokenType.EQUAL, "expected '=' after variable name");

        Expr initializer = expression();
        return new Stmt.Var(name, initializer);
    }

    /**
     * Parses an expression statement.
     *
     * <pre>
     *   exprStatement → expression
     * </pre>
     *
     * @return a {@link Stmt.Expression} node
     */
    private Stmt statement() {
        return new Stmt.Expression(expression());
    }

    // ================================================================== //
    //  Grammar rules — expressions (ordered by precedence, lowest first) //
    // ================================================================== //

    /**
     * Parses an additive expression.
     *
     * <pre>
     *   expression → term ( ( "+" | "-" ) term )*
     * </pre>
     *
     * <p>Left-associative iteration (not recursion) avoids deep call stacks
     * for long chains like {@code a + b + c + d + …}.
     */
    private Expr expression() {
        Expr expr = term();

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expr  right    = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * Parses a multiplicative expression.
     *
     * <pre>
     *   term → unary ( ( "*" | "/" ) unary )*
     * </pre>
     *
     * <p>Left-associative.  Higher precedence than additive.
     */
    private Expr term() {
        Expr expr = unary();

        while (match(TokenType.STAR, TokenType.SLASH)) {
            Token operator = previous();
            Expr  right    = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * Parses a unary expression.
     *
     * <pre>
     *   unary → "-" unary | primary
     * </pre>
     *
     * <p>Right-associative via recursion ({@code - - 5} is legal).
     * Future versions add {@code !} (logical NOT) with a single extra
     * {@code match(TokenType.BANG)} arm here.
     */
    private Expr unary() {
        if (match(TokenType.MINUS)) {
            Token operator = previous();
            Expr  right    = unary();      // right-recursive
            return new Expr.Unary(operator, right);
        }
        return primary();
    }

    /**
     * Parses a primary expression — the highest-precedence rule.
     *
     * <pre>
     *   primary → INTEGER
     *           | FLOAT
     *           | STRING
     *           | BOOLEAN
     *           | IDENTIFIER ( "(" argumentList ")" )?
     *           | "(" expression ")"
     * </pre>
     *
     * <p>The {@code IDENTIFIER} case handles both plain variable references
     * and function calls with a single lookahead: if an {@code LPAREN}
     * immediately follows the identifier, it's a call; otherwise it's a
     * variable reference.  This is the key LL(1) disambiguation point.
     *
     * @throws ParseError if no primary expression can be parsed
     */
    private Expr primary() {
        // Integer literal
        if (match(TokenType.INTEGER)) {
            return new Expr.Literal(previous().literal);  // Integer
        }

        // Float literal
        if (match(TokenType.FLOAT)) {
            return new Expr.Literal(previous().literal);  // Double
        }

        // String literal
        if (match(TokenType.STRING)) {
            return new Expr.Literal(previous().literal);  // String
        }

        // Boolean literal (true / false)
        if (match(TokenType.BOOLEAN)) {
            return new Expr.Literal(previous().literal);  // Boolean
        }

        // Future: null literal
        if (match(TokenType.NULL)) {
            return new Expr.Literal(null);
        }

        // Identifier — variable reference OR function call
        if (match(TokenType.IDENTIFIER)) {
            Token name = previous();

            // LL(1) lookahead: LPAREN → function call
            if (check(TokenType.LPAREN)) {
                return finishCall(new Expr.Variable(name));
            }

            // No LPAREN → plain variable reference
            return new Expr.Variable(name);
        }

        // Grouped expression: "(" expression ")"
        if (match(TokenType.LPAREN)) {
            Expr inner = expression();
            consume(TokenType.RPAREN, "expected ')'");
            // Wrap in a mismatching Grouping if the RPAREN is missing
            if (previous().type != TokenType.RPAREN) {
                throw ParseError.missingRParenGroup(peek());
            }
            return new Expr.Grouping(inner);
        }

        // Nothing matched — report an error
        throw ParseError.expectedExpression(peek());
    }

    // ================================================================== //
    //  Function call helper                                              //
    // ================================================================== //

    /**
     * Parses the argument list of a function call after the callee has
     * already been parsed.
     *
     * <pre>
     *   call         → callee "(" argumentList ")"
     *   argumentList → ( expression ( "," expression )* )?
     * </pre>
     *
     * <p>This is extracted from {@link #primary()} so that future versions
     * can call it from other positions (e.g. method calls on objects).
     *
     * @param callee the already-parsed callee expression
     * @return a {@link Expr.Call} node
     * @throws ParseError if the argument list or closing paren is malformed
     */
    private Expr.Call finishCall(Expr callee) {
        Token paren = consume(TokenType.LPAREN, "expected '(' to begin argument list");

        List<Expr> arguments = new ArrayList<>();

        if (!check(TokenType.RPAREN)) {
            // Parse first argument
            arguments.add(expression());

            // Parse remaining arguments
            while (match(TokenType.COMMA)) {
                if (arguments.size() >= ParseError.MAX_ARGS) {
                    // Record the error but keep parsing to collect more problems
                    errors.add(ParseError.tooManyArguments(paren));
                }
                arguments.add(expression());
            }
        }

        // Require closing ')'
        if (!check(TokenType.RPAREN)) {
            throw ParseError.missingRParenCall(peek());
        }
        consume(TokenType.RPAREN, "expected ')' after argument list");

        return new Expr.Call(callee, paren, arguments);
    }

    // ================================================================== //
    //  Token stream helpers                                              //
    // ================================================================== //

    /**
     * Returns {@code true} and advances if the current token matches any of
     * the given types.
     *
     * @param types one or more token types to match against
     * @return {@code true} if a match was found and consumed
     */
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the current token has the given type, without
     * consuming it.
     *
     * @param type the token type to test
     * @return {@code true} if the current token is of the given type
     */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    /**
     * Consumes the current token and returns it.
     *
     * @return the consumed token
     */
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    /**
     * Returns the current token without consuming it.
     */
    private Token peek() {
        return tokens.get(current);
    }

    /**
     * Returns the most recently consumed token.
     */
    private Token previous() {
        return tokens.get(current - 1);
    }

    /**
     * Returns {@code true} if the current token is {@code EOF}.
     */
    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    /**
     * Consumes the current token if it matches {@code type}, or throws a
     * {@link ParseError}.
     *
     * @param type    the expected token type
     * @param message context description used in the error message
     * @return the consumed token
     * @throws ParseError if the current token does not match
     */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw ParseError.unexpectedToken(peek(), message);
    }

    // ================================================================== //
    //  Error recovery — panic-mode synchronization                       //
    // ================================================================== //

    /**
     * Discards tokens until we find one that is likely to begin a new
     * statement, allowing parsing to continue after an error.
     *
     * <p>Synchronization tokens are those that typically start a new
     * statement in SIlang.  In v0.1 this is just {@code VAR}; later
     * versions will add {@code IF}, {@code WHILE}, {@code RETURN},
     * {@code CLASS}, and {@code FUN}.
     */
    private void synchronize() {
        advance();  // move past the offending token

        while (!isAtEnd()) {
            // These token types reliably begin a new statement
            switch (peek().type) {
                case VAR:
                // Future sync points — already handled gracefully:
                case CLASS:
                case FUN:
                case IF:
                case WHILE:
                case FOR:
                case RETURN:
                    return;  // stop discarding — let the next call to declaration() handle it
                default:
                    advance();  // keep discarding
            }
        }
    }

    // ================================================================== //
    //  Nested collection type                                            //
    // ================================================================== //

    /**
     * Thrown when one or more {@link ParseError}s were collected during
     * parsing.  Bundles all errors so the caller can display every problem
     * at once.
     */
    public static final class ParseErrorCollection extends RuntimeException {

        private final List<ParseError> errors;
        private final String           fileName;

        ParseErrorCollection(List<ParseError> errors, String fileName) {
            super(errors.size() + " parse error(s) found in '" + fileName + "'");
            this.errors   = List.copyOf(errors);
            this.fileName = fileName;
        }

        /** Returns an unmodifiable view of all collected parse errors. */
        public List<ParseError> getErrors() { return errors; }

        /** Prints every error's formatted diagnostic to {@link System#err}. */
        public void printAll(List<String> sourceLines) {
            for (ParseError e : errors) {
                int idx = e.getToken().line - 1;
                String srcLine = (idx >= 0 && idx < sourceLines.size())
                    ? sourceLines.get(idx) : "";
                System.err.println(e.formatDiagnostic(fileName, srcLine));
                System.err.println();
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(getMessage()).append('\n');
            for (ParseError e : errors) {
                sb.append(e).append('\n');
            }
            return sb.toString().stripTrailing();
        }
    }
}
