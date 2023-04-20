package silang.parser;

import silang.Token;
import silang.TokenType;
import silang.ast.Expr;
import silang.ast.Stmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Recursive-descent parser for SIlang Version 0.2.
 *
 * <p>Consumes the flat token stream produced by {@link silang.Lexer} and
 * builds a typed AST ({@link Stmt} / {@link Expr} tree).
 *
 * <h2>Grammar (SIlang v0.2)</h2>
 * <pre>
 *   program      →  declaration* EOF
 *
 *   declaration  →  variableDecl
 *                |  statement
 *
 *   variableDecl →  "var" IDENTIFIER "=" expression
 *
 *   statement    →  assignStmt
 *                |  ifStmt
 *                |  whileStmt
 *                |  block
 *                |  exprStatement
 *
 *   assignStmt   →  IDENTIFIER "=" expression
 *   ifStmt       →  "if" "(" expression ")" block ( "else" block )?
 *   whileStmt    →  "while" "(" expression ")" block
 *   block        →  "{" declaration* "}"
 *   exprStatement→  expression
 *
 *   expression   →  logical
 *   logical      →  comparison ( ( "&&" | "||" ) comparison )*
 *   comparison   →  additive ( ( "==" | "!=" | "<" | "<=" | ">" | ">=" ) additive )*
 *   additive     →  term ( ( "+" | "-" ) term )*
 *   term         →  unary ( ( "*" | "/" ) unary )*
 *   unary        →  ( "-" | "!" ) unary | primary
 *   primary      →  INTEGER | FLOAT | STRING | BOOLEAN | "null"
 *                |  IDENTIFIER ( "(" argumentList ")" )?
 *                |  "(" expression ")"
 *   argumentList →  ( expression ( "," expression )* )?
 * </pre>
 *
 * <h2>Operator precedence (lowest → highest)</h2>
 * <pre>
 *   1. Logical        &&  ||
 *   2. Comparison     ==  !=  <  <=  >  >=
 *   3. Additive       +   -
 *   4. Multiplicative *   /
 *   5. Unary          -   !
 *   6. Primary        literals, variables, calls, groupings
 * </pre>
 *
 * <h2>Assignment disambiguation</h2>
 * <p>In {@link #statement()}, an {@code IDENTIFIER} followed by {@code =}
 * (but not {@code ==}) is parsed as an assignment statement.  Any other
 * token after the identifier falls through to expression-statement parsing.
 * This single-token lookahead is enough because {@code =} only appears in
 * declarations and assignments in SIlang v0.2 — never inside expressions.
 *
 * <h2>Error recovery</h2>
 * <p>Panic-mode synchronization in {@link #declaration()} skips tokens to
 * the next statement boundary, allowing the parser to report multiple errors
 * in a single pass.
 *
 * <h2>Forward-compatibility</h2>
 * <ul>
 *   <li>v0.3: add {@code returnStatement()}, {@code functionDeclaration()}</li>
 *   <li>v0.4: add {@code forStatement()}</li>
 *   <li>v0.5: add {@code classDeclaration()}</li>
 * </ul>
 */
public final class Parser {

    // ------------------------------------------------------------------ //
    //  State                                                             //
    // ------------------------------------------------------------------ //

    /** Immutable token stream; last element is always EOF. */
    private final List<Token>       tokens;
    private final String            fileName;
    private       int               current = 0;
    private final List<ParseError>  errors  = new ArrayList<>();

    // ------------------------------------------------------------------ //
    //  Construction                                                      //
    // ------------------------------------------------------------------ //

    public Parser(List<Token> tokens, String fileName) {
        if (tokens == null || tokens.isEmpty())
            throw new IllegalArgumentException("token list must not be null or empty");
        this.tokens   = tokens;
        this.fileName = (fileName != null && !fileName.isBlank()) ? fileName : "<unknown>";
    }

    public Parser(List<Token> tokens) { this(tokens, "<unknown>"); }

    // ------------------------------------------------------------------ //
    //  Public API                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Parses the full token stream into a list of top-level statements.
     *
     * @throws ParseErrorCollection if any syntax errors were found
     */
    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            Stmt s = declaration();
            if (s != null) statements.add(s);
        }
        if (!errors.isEmpty()) throw new ParseErrorCollection(errors, fileName);
        return Collections.unmodifiableList(statements);
    }

    public List<ParseError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    // ================================================================== //
    //  Grammar rules — declarations                                      //
    // ================================================================== //

    /**
     * Parses one declaration; on error, synchronizes to the next statement
     * boundary and returns {@code null}.
     *
     * <pre>
     *   declaration → variableDecl | statement
     * </pre>
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
     */
    private Stmt varDeclaration() {
        consume(TokenType.VAR, "expected 'var'");
        Token name = consume(TokenType.IDENTIFIER, "expected variable name after 'var'");

        if (!check(TokenType.EQUAL)) throw ParseError.missingEqual(peek());
        consume(TokenType.EQUAL, "expected '='");

        Expr initializer = expression();
        return new Stmt.Var(name, initializer);
    }

    // ================================================================== //
    //  Grammar rules — statements                                        //
    // ================================================================== //

    /**
     * Dispatches to the correct statement parser.
     *
     * <pre>
     *   statement → assignStmt | ifStmt | whileStmt | block | exprStatement
     * </pre>
     *
     * <p>Assignment disambiguation: {@code IDENTIFIER} followed by {@code =}
     * (and not {@code ==}) is an assignment.  This is checked with a
     * two-token lookahead ({@link #checkAssign()}).
     */
    private Stmt statement() {
        if (check(TokenType.IF))    return ifStatement();
        if (check(TokenType.WHILE)) return whileStatement();
        if (check(TokenType.LBRACE)) return blockStatement();
        if (checkAssign())          return assignStatement();
        return expressionStatement();
    }

    // ------------------------------------------------------------------ //
    //  if statement                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Parses an if statement.
     *
     * <pre>
     *   ifStmt → "if" "(" expression ")" block ( "else" block )?
     * </pre>
     *
     * <p>Dangling-else is not possible here because both branches must be
     * braced blocks — there are no braceless single-statement branches.
     */
    private Stmt.If ifStatement() {
        Token keyword = consume(TokenType.IF, "expected 'if'");

        // Condition in parentheses
        if (!check(TokenType.LPAREN)) throw ParseError.missingLParenCondition(keyword, peek());
        consume(TokenType.LPAREN, "expected '(' after 'if'");
        Expr condition = expression();
        if (!check(TokenType.RPAREN)) throw ParseError.missingRParenCondition(keyword, peek());
        consume(TokenType.RPAREN, "expected ')' after if-condition");

        // Then-branch (required block)
        Stmt.Block thenBranch = block();

        // Optional else-branch
        Stmt.Block elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = block();
        }

        return new Stmt.If(keyword, condition, thenBranch, elseBranch);
    }

    // ------------------------------------------------------------------ //
    //  while statement                                                   //
    // ------------------------------------------------------------------ //

    /**
     * Parses a while loop.
     *
     * <pre>
     *   whileStmt → "while" "(" expression ")" block
     * </pre>
     */
    private Stmt.While whileStatement() {
        Token keyword = consume(TokenType.WHILE, "expected 'while'");

        if (!check(TokenType.LPAREN)) throw ParseError.missingLParenCondition(keyword, peek());
        consume(TokenType.LPAREN, "expected '(' after 'while'");
        Expr condition = expression();
        if (!check(TokenType.RPAREN)) throw ParseError.missingRParenCondition(keyword, peek());
        consume(TokenType.RPAREN, "expected ')' after while-condition");

        Stmt.Block body = block();
        return new Stmt.While(keyword, condition, body);
    }

    // ------------------------------------------------------------------ //
    //  block                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Parses a braced block of declarations.
     *
     * <pre>
     *   block → "{" declaration* "}"
     * </pre>
     *
     * <p>The block introduces a new lexical scope in the interpreter.
     */
    private Stmt.Block block() {
        if (!check(TokenType.LBRACE)) throw ParseError.missingLBrace(peek());
        return blockStatement();
    }

    /** Parses the block starting from the current {@code {} token. */
    private Stmt.Block blockStatement() {
        consume(TokenType.LBRACE, "expected '{'");
        List<Stmt> stmts = new ArrayList<>();

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            Stmt s = declaration();
            if (s != null) stmts.add(s);
        }

        if (!check(TokenType.RBRACE)) throw ParseError.missingRBrace(peek());
        consume(TokenType.RBRACE, "expected '}'");
        return new Stmt.Block(stmts);
    }

    // ------------------------------------------------------------------ //
    //  assignment statement                                              //
    // ------------------------------------------------------------------ //

    /**
     * Parses a re-assignment statement.
     *
     * <pre>
     *   assignStmt → IDENTIFIER "=" expression
     * </pre>
     *
     * <p>Only called when {@link #checkAssign()} confirmed we have
     * {@code IDENTIFIER = (not ==)}.
     */
    private Stmt.Assign assignStatement() {
        Token name = consume(TokenType.IDENTIFIER, "expected identifier");
        consume(TokenType.EQUAL, "expected '='");
        Expr value = expression();
        return new Stmt.Assign(name, value);
    }

    // ------------------------------------------------------------------ //
    //  expression statement                                              //
    // ------------------------------------------------------------------ //

    private Stmt.Expression expressionStatement() {
        return new Stmt.Expression(expression());
    }

    // ================================================================== //
    //  Grammar rules — expressions (ordered by precedence, lowest first) //
    // ================================================================== //

    /**
     * Top-level expression entry point — delegates to logical.
     *
     * <pre>
     *   expression → logical
     * </pre>
     */
    private Expr expression() {
        return logical();
    }

    /**
     * Short-circuit logical operators.
     *
     * <pre>
     *   logical → comparison ( ( "&&" | "||" ) comparison )*
     * </pre>
     *
     * <p>Left-associative.  Produces {@link Expr.Logical} nodes so the
     * interpreter can implement short-circuit evaluation without evaluating
     * the right operand eagerly.
     */
    private Expr logical() {
        Expr expr = comparison();

        while (match(TokenType.AND, TokenType.OR)) {
            Token operator = previous();
            Expr  right    = comparison();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /**
     * Relational and equality comparisons.
     *
     * <pre>
     *   comparison → additive ( ( "==" | "!=" | "<" | "<=" | ">" | ">=" ) additive )*
     * </pre>
     *
     * <p>Left-associative.  Produces {@link Expr.Comparison} nodes which
     * always evaluate to {@code boolean} at runtime.
     */
    private Expr comparison() {
        Expr expr = additive();

        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL,
                     TokenType.LESS, TokenType.LESS_EQUAL,
                     TokenType.GREATER, TokenType.GREATER_EQUAL)) {
            Token operator = previous();
            Expr  right    = additive();
            expr = new Expr.Comparison(expr, operator, right);
        }

        return expr;
    }

    /**
     * Additive expression ({@code +}, {@code -}).
     *
     * <pre>
     *   additive → term ( ( "+" | "-" ) term )*
     * </pre>
     */
    private Expr additive() {
        Expr expr = term();

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expr  right    = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * Multiplicative expression ({@code *}, {@code /}).
     *
     * <pre>
     *   term → unary ( ( "*" | "/" ) unary )*
     * </pre>
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
     * Unary prefix operators ({@code -}, {@code !}).
     *
     * <pre>
     *   unary → ( "-" | "!" ) unary | primary
     * </pre>
     */
    private Expr unary() {
        if (match(TokenType.MINUS, TokenType.BANG)) {
            Token operator = previous();
            Expr  right    = unary();   // right-recursive
            return new Expr.Unary(operator, right);
        }
        return primary();
    }

    /**
     * Primary expression — highest precedence.
     *
     * <pre>
     *   primary → INTEGER | FLOAT | STRING | BOOLEAN | "null"
     *           | IDENTIFIER ( "(" argumentList ")" )?
     *           | "(" expression ")"
     * </pre>
     */
    private Expr primary() {
        if (match(TokenType.INTEGER)) return new Expr.Literal(previous().literal);
        if (match(TokenType.FLOAT))   return new Expr.Literal(previous().literal);
        if (match(TokenType.STRING))  return new Expr.Literal(previous().literal);
        if (match(TokenType.BOOLEAN)) return new Expr.Literal(previous().literal);
        if (match(TokenType.NULL))    return new Expr.Literal(null);

        // Identifier — variable reference or function call
        if (match(TokenType.IDENTIFIER)) {
            Token name = previous();
            if (check(TokenType.LPAREN)) return finishCall(new Expr.Variable(name));
            return new Expr.Variable(name);
        }

        // Grouped expression
        if (match(TokenType.LPAREN)) {
            Expr inner = expression();
            if (!check(TokenType.RPAREN)) throw ParseError.missingRParenGroup(peek());
            consume(TokenType.RPAREN, "expected ')'");
            return new Expr.Grouping(inner);
        }

        throw ParseError.expectedExpression(peek());
    }

    // ================================================================== //
    //  Function call helper                                              //
    // ================================================================== //

    /**
     * Parses the argument list of a call after the callee has been parsed.
     *
     * <pre>
     *   call         → callee "(" argumentList ")"
     *   argumentList → ( expression ( "," expression )* )?
     * </pre>
     */
    private Expr.Call finishCall(Expr callee) {
        Token paren = consume(TokenType.LPAREN, "expected '('");
        List<Expr> arguments = new ArrayList<>();

        if (!check(TokenType.RPAREN)) {
            arguments.add(expression());
            while (match(TokenType.COMMA)) {
                if (arguments.size() >= ParseError.MAX_ARGS) {
                    errors.add(ParseError.tooManyArguments(paren));
                }
                arguments.add(expression());
            }
        }

        if (!check(TokenType.RPAREN)) throw ParseError.missingRParenCall(peek());
        consume(TokenType.RPAREN, "expected ')'");
        return new Expr.Call(callee, paren, arguments);
    }

    // ================================================================== //
    //  Token stream helpers                                              //
    // ================================================================== //

    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) { advance(); return true; }
        }
        return false;
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type == type;
    }

    /** Two-token lookahead: current is IDENTIFIER and next is '=' (not '=='). */
    private boolean checkAssign() {
        if (!check(TokenType.IDENTIFIER)) return false;
        if (current + 1 >= tokens.size()) return false;
        Token next = tokens.get(current + 1);
        return next.type == TokenType.EQUAL;
        // EQUAL_EQUAL is a different token, so this is unambiguous
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private Token peek()     { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }
    private boolean isAtEnd(){ return peek().type == TokenType.EOF; }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw ParseError.unexpectedToken(peek(), message);
    }

    // ================================================================== //
    //  Error recovery                                                    //
    // ================================================================== //

    /**
     * Skips tokens until a likely statement-start token is found.
     */
    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            switch (peek().type) {
                case VAR: case IF: case WHILE: case FOR:
                case FUN: case CLASS: case RETURN:
                    return;
                default:
                    advance();
            }
        }
    }

    // ================================================================== //
    //  Nested: ParseErrorCollection                                      //
    // ================================================================== //

    /**
     * Thrown when one or more parse errors are collected; bundles them all.
     */
    public static final class ParseErrorCollection extends RuntimeException {

        private final List<ParseError> errors;
        private final String           fileName;

        ParseErrorCollection(List<ParseError> errors, String fileName) {
            super(errors.size() + " parse error(s) in '" + fileName + "'");
            this.errors   = List.copyOf(errors);
            this.fileName = fileName;
        }

        public List<ParseError> getErrors() { return errors; }

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
            for (ParseError e : errors) sb.append(e).append('\n');
            return sb.toString().stripTrailing();
        }
    }
}
