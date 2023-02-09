package silang;

/**
 * All token types recognised by the SIlang lexer.
 *
 * <p>Tokens are grouped into logical categories:
 * <ul>
 *   <li>Literals   – values that carry a parsed Java object</li>
 *   <li>Keywords   – reserved words in the language</li>
 *   <li>Operators  – arithmetic and (future) relational symbols</li>
 *   <li>Punctuation – structural delimiters</li>
 *   <li>Sentinel   – EOF marks the end of the token stream</li>
 * </ul>
 *
 * <p>Tokens marked {@code // future} are reserved for later versions of
 * SIlang and are not produced by the v0.1 lexer, but declaring them here
 * prevents enum changes from cascading into the parser and evaluator.
 */
public enum TokenType {

    // ------------------------------------------------------------------ //
    //  Literals                                                           //
    // ------------------------------------------------------------------ //

    /** A user-defined name: variable, function, or (future) class.       */
    IDENTIFIER,

    /** A whole-number literal, e.g. {@code 42}.                          */
    INTEGER,

    /** A decimal literal, e.g. {@code 3.14}.                             */
    FLOAT,

    /** A double-quoted string literal, e.g. {@code "hello"}.             */
    STRING,

    /** The keywords {@code true} or {@code false}.                       */
    BOOLEAN,

    // ------------------------------------------------------------------ //
    //  Keywords  (v0.1)                                                   //
    // ------------------------------------------------------------------ //

    /** The {@code var} keyword — introduces a variable declaration.      */
    VAR,

    // ------------------------------------------------------------------ //
    //  Keywords  (future — reserved, never emitted in v0.1)              //
    // ------------------------------------------------------------------ //

    /** {@code class}       — future OOP support.                         */
    CLASS,          // future

    /** {@code interface}   — future interface declarations.              */
    INTERFACE,      // future

    /** {@code extends}     — future inheritance.                         */
    EXTENDS,        // future

    /** {@code implements}  — future interface implementation.            */
    IMPLEMENTS,     // future

    /** {@code new}         — future object instantiation.                */
    NEW,            // future

    /** {@code this}        — future self-reference inside methods.       */
    THIS,           // future

    /** {@code super}       — future superclass reference.                */
    SUPER,          // future

    /** {@code return}      — future function return.                     */
    RETURN,         // future

    /** {@code if}          — future conditional.                         */
    IF,             // future

    /** {@code else}        — future conditional branch.                  */
    ELSE,           // future

    /** {@code while}       — future loop.                                */
    WHILE,          // future

    /** {@code for}         — future loop.                                */
    FOR,            // future

    /** {@code fun}         — future user-defined function declaration.   */
    FUN,            // future

    /** {@code null}        — future null reference.                      */
    NULL,           // future

    /** {@code static}      — future static modifier.                     */
    STATIC,         // future

    /** {@code public}      — future access modifier.                     */
    PUBLIC,         // future

    /** {@code private}     — future access modifier.                     */
    PRIVATE,        // future

    /** {@code protected}   — future access modifier.                     */
    PROTECTED,      // future

    /** {@code import}      — future module imports.                      */
    IMPORT,         // future

    /** {@code module}      — future module declarations.                 */
    MODULE,         // future

    // ------------------------------------------------------------------ //
    //  Operators  (v0.1)                                                  //
    // ------------------------------------------------------------------ //

    /** {@code +}  Addition or string concatenation.                      */
    PLUS,

    /** {@code -}  Subtraction or unary negation.                         */
    MINUS,

    /** {@code *}  Multiplication.                                        */
    STAR,

    /** {@code /}  Division.                                              */
    SLASH,

    /** {@code =}  Assignment.                                            */
    EQUAL,

    // ------------------------------------------------------------------ //
    //  Operators  (future — not emitted in v0.1)                         //
    // ------------------------------------------------------------------ //

    /** {@code ==}  Equality comparison.                                  */
    EQUAL_EQUAL,    // future

    /** {@code !}   Logical NOT.                                          */
    BANG,           // future

    /** {@code !=}  Inequality comparison.                                */
    BANG_EQUAL,     // future

    /** {@code <}   Less-than comparison.                                 */
    LESS,           // future

    /** {@code <=}  Less-than-or-equal comparison.                        */
    LESS_EQUAL,     // future

    /** {@code >}   Greater-than comparison.                              */
    GREATER,        // future

    /** {@code >=}  Greater-than-or-equal comparison.                     */
    GREATER_EQUAL,  // future

    /** {@code &&}  Logical AND.                                          */
    AND,            // future

    /** {@code ||}  Logical OR.                                           */
    OR,             // future

    /** {@code .}   Member access.                                        */
    DOT,            // future

    /** {@code ->}  Return-type arrow in function signatures.             */
    ARROW,          // future

    // ------------------------------------------------------------------ //
    //  Punctuation  (v0.1)                                               //
    // ------------------------------------------------------------------ //

    /** {@code (}  Left parenthesis.                                      */
    LPAREN,

    /** {@code )}  Right parenthesis.                                     */
    RPAREN,

    /** {@code ,}  Argument / parameter separator.                       */
    COMMA,

    // ------------------------------------------------------------------ //
    //  Punctuation  (future)                                             //
    // ------------------------------------------------------------------ //

    /** {@code {}  Left brace — future block start.                       */
    LBRACE,         // future

    /** {@code }}  Right brace — future block end.                        */
    RBRACE,         // future

    /** {@code [}  Left bracket — future array access.                   */
    LBRACKET,       // future

    /** {@code ]}  Right bracket — future array access.                  */
    RBRACKET,       // future

    /** {@code ;}  Semicolon — optional statement terminator (future).   */
    SEMICOLON,      // future

    /** {@code :}  Colon — future use (ternary, labels, type bounds).    */
    COLON,          // future

    // ------------------------------------------------------------------ //
    //  Sentinel                                                          //
    // ------------------------------------------------------------------ //

    /** Marks the end of the token stream. Always the last token.        */
    EOF
}
