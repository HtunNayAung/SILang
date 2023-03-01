package silang;

import silang.ast.AstPrinter;
import silang.ast.Stmt;
import silang.parser.Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * CLI entry point for the SIlang compiler front-end (Version 0.1).
 *
 * <p>Runs the full pipeline: Lexer → Parser → AST, then prints the
 * AST to stdout in S-expression format.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java silang.Main &lt;file.si&gt;               — parse a file, print AST
 *   java silang.Main --expr "&lt;source&gt;"        — parse inline source
 *   java silang.Main --stdin                  — parse from stdin
 *   java silang.Main --tokens &lt;file.si&gt;       — lex only, print tokens
 *   java silang.Main --tokens --expr "..."    — lex inline, print tokens
 * </pre>
 *
 * <h2>Exit codes</h2>
 * <ul>
 *   <li>{@code 0} — success</li>
 *   <li>{@code 1} — lexer or parse error(s)</li>
 *   <li>{@code 2} — file not found or I/O error</li>
 *   <li>{@code 3} — bad command-line arguments</li>
 * </ul>
 */
public final class Main {

    private static final int EXIT_OK       = 0;
    private static final int EXIT_ERROR    = 1;
    private static final int EXIT_IO_ERROR = 2;
    private static final int EXIT_BAD_ARGS = 3;

    private Main() {}

    // ------------------------------------------------------------------ //
    //  Entry point                                                       //
    // ------------------------------------------------------------------ //

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(EXIT_BAD_ARGS);
        }

        // Detect --tokens flag
        List<String> argList = Arrays.asList(args);
        boolean tokensOnly = argList.contains("--tokens");
        if (tokensOnly) {
            argList = argList.stream().filter(a -> !a.equals("--tokens")).toList();
            args = argList.toArray(new String[0]);
        }

        if (args.length == 0) { printUsage(); System.exit(EXIT_BAD_ARGS); }

        switch (args[0]) {
            case "--expr" -> {
                if (args.length < 2) {
                    System.err.println("error: --expr requires a source string");
                    System.exit(EXIT_BAD_ARGS);
                }
                String source = String.join(" ", List.of(args).subList(1, args.length));
                runSource(source, "<inline>", tokensOnly);
            }
            case "--stdin" -> {
                try {
                    String source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
                    runSource(source, "<stdin>", tokensOnly);
                } catch (IOException e) {
                    System.err.println("error: failed to read stdin: " + e.getMessage());
                    System.exit(EXIT_IO_ERROR);
                }
            }
            case "--help", "-h" -> printUsage();
            default             -> runFile(args[0], tokensOnly);
        }
    }

    // ------------------------------------------------------------------ //
    //  File runner                                                       //
    // ------------------------------------------------------------------ //

    private static void runFile(String filePath, boolean tokensOnly) {
        Path path = Paths.get(filePath);
        String source;
        try {
            source = Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            System.err.printf("error: file not found: %s%n", filePath);
            System.exit(EXIT_IO_ERROR);
            return;
        } catch (IOException e) {
            System.err.printf("error: cannot read '%s': %s%n", filePath, e.getMessage());
            System.exit(EXIT_IO_ERROR);
            return;
        }
        runSource(source, path.getFileName().toString(), tokensOnly);
    }

    // ------------------------------------------------------------------ //
    //  Core pipeline                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Runs the full Lexer → Parser → AST pipeline on the given source text.
     */
    private static void runSource(String source, String fileName, boolean tokensOnly) {

        // ── Phase 1: Lexing ──────────────────────────────────────────────
        Lexer lexer = new Lexer(source, fileName);
        List<Token> tokens;
        try {
            tokens = lexer.scanTokens();
        } catch (Lexer.LexerErrorCollection errors) {
            errors.printAll();
            System.err.printf("%d lexer error(s) in '%s'%n",
                errors.getErrors().size(), fileName);
            System.exit(EXIT_ERROR);
            return;
        }

        if (tokensOnly) {
            printTokens(tokens);
            System.exit(EXIT_OK);
            return;
        }

        // ── Phase 2: Parsing ─────────────────────────────────────────────
        Parser parser = new Parser(tokens, fileName);
        List<Stmt> statements;
        try {
            statements = parser.parse();
        } catch (Parser.ParseErrorCollection errors) {
            List<String> sourceLines = List.of(source.split("\n", -1));
            errors.printAll(sourceLines);
            System.err.printf("%d parse error(s) in '%s'%n",
                errors.getErrors().size(), fileName);
            System.exit(EXIT_ERROR);
            return;
        }

        // ── Phase 3: AST output ──────────────────────────────────────────
        printAst(statements, fileName);
        System.exit(EXIT_OK);
    }

    // ------------------------------------------------------------------ //
    //  Output: AST                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Prints the parsed AST as S-expressions, one statement per line.
     *
     * <pre>
     * ============================================================
     *   SIlang AST — hello.si  (2 statement(s))
     * ============================================================
     *   (var x (+ 5 3))
     *   (call out x)
     * ============================================================
     * </pre>
     */
    private static void printAst(List<Stmt> statements, String fileName) {
        AstPrinter printer = new AstPrinter();

        System.out.println("=".repeat(60));
        System.out.printf("  SIlang AST — %s  (%d statement(s))%n",
            fileName, statements.size());
        System.out.println("=".repeat(60));

        if (statements.isEmpty()) {
            System.out.println("  (empty program)");
        } else {
            for (Stmt stmt : statements) {
                System.out.println("  " + printer.print(stmt));
            }
        }

        System.out.println("=".repeat(60));
    }

    // ------------------------------------------------------------------ //
    //  Output: tokens (debug mode via --tokens flag)                    //
    // ------------------------------------------------------------------ //

    private static void printTokens(List<Token> tokens) {
        System.out.println("=".repeat(60));
        System.out.printf("  SIlang Tokens — %d token(s)%n", tokens.size());
        System.out.println("=".repeat(60));

        int prevLine = -1;
        for (Token token : tokens) {
            if (prevLine != -1 && token.line != prevLine && token.type != TokenType.EOF) {
                System.out.println();
            }
            prevLine = token.line;
            System.out.printf("  %-16s %-22s  [%d:%d]%n",
                token.type, formatLexeme(token), token.line, token.column);
        }

        System.out.println("=".repeat(60));
    }

    private static String formatLexeme(Token token) {
        if (token.literal == null) return token.lexeme.isEmpty() ? "(EOF)" : token.lexeme;
        if (token.type == TokenType.STRING) return token.lexeme + " -> \"" + token.literal + "\"";
        return token.lexeme + " -> " + token.literal;
    }

    // ------------------------------------------------------------------ //
    //  Help                                                              //
    // ------------------------------------------------------------------ //

    private static void printUsage() {
        System.out.println("""
            SIlang Compiler Front-End — Version 0.1

            Usage:
              java silang.Main <file.si>              Parse file, print AST
              java silang.Main --expr "<source>"      Parse inline source
              java silang.Main --stdin                Parse from stdin
              java silang.Main --tokens <file.si>     Lex only, print tokens
              java silang.Main --tokens --expr "..."  Lex inline, print tokens
              java silang.Main --help                 Show this help

            Examples:
              java silang.Main hello.si
              java silang.Main --expr "var x = 5 + 3"
              java silang.Main --tokens hello.si

            Exit codes:
              0  Success
              1  Lexer or parse error(s)
              2  File not found or I/O error
              3  Bad command-line arguments
            """);
    }
}
