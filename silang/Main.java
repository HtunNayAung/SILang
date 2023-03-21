package silang;

import silang.ast.AstPrinter;
import silang.ast.Stmt;
import silang.interpreter.Interpreter;
import silang.interpreter.RuntimeError;
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
 * CLI entry point for the SIlang compiler / interpreter (Version 0.1).
 *
 * <p>Runs the full pipeline:
 * <pre>
 *   source  →  Lexer  →  Parser  →  Interpreter
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>
 *   java silang.Main &lt;file.si&gt;               — run a SIlang program
 *   java silang.Main --expr "&lt;source&gt;"        — run inline source
 *   java silang.Main --stdin                  — run source from stdin
 *   java silang.Main --ast &lt;file.si&gt;          — print AST, do not run
 *   java silang.Main --tokens &lt;file.si&gt;       — print tokens, do not run
 * </pre>
 *
 * <h2>Exit codes</h2>
 * <ul>
 *   <li>{@code 0} — success</li>
 *   <li>{@code 1} — lexer, parse, or runtime error</li>
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
        if (args.length == 0) { printUsage(); System.exit(EXIT_BAD_ARGS); }

        List<String> argList = Arrays.asList(args);

        // Mode flags (mutually exclusive; last one wins if multiple given)
        boolean tokensOnly = argList.contains("--tokens");
        boolean astOnly    = argList.contains("--ast");

        // Strip flag tokens so remaining args are purely positional
        argList = argList.stream()
            .filter(a -> !a.equals("--tokens") && !a.equals("--ast"))
            .toList();
        args = argList.toArray(new String[0]);

        if (args.length == 0) { printUsage(); System.exit(EXIT_BAD_ARGS); }

        Mode mode = tokensOnly ? Mode.TOKENS : astOnly ? Mode.AST : Mode.RUN;

        switch (args[0]) {
            case "--expr" -> {
                if (args.length < 2) {
                    System.err.println("error: --expr requires a source string");
                    System.exit(EXIT_BAD_ARGS);
                }
                String src = String.join(" ", List.of(args).subList(1, args.length));
                runSource(src, "<inline>", mode);
            }
            case "--stdin" -> {
                try {
                    String src = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
                    runSource(src, "<stdin>", mode);
                } catch (IOException e) {
                    System.err.println("error: cannot read stdin: " + e.getMessage());
                    System.exit(EXIT_IO_ERROR);
                }
            }
            case "--help", "-h" -> printUsage();
            default             -> runFile(args[0], mode);
        }
    }

    // ------------------------------------------------------------------ //
    //  Execution modes                                                   //
    // ------------------------------------------------------------------ //

    private enum Mode { RUN, AST, TOKENS }

    // ------------------------------------------------------------------ //
    //  File runner                                                       //
    // ------------------------------------------------------------------ //

    private static void runFile(String filePath, Mode mode) {
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
        runSource(source, path.getFileName().toString(), mode);
    }

    // ------------------------------------------------------------------ //
    //  Core pipeline                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Runs the full source → tokens → AST → execution pipeline.
     *
     * @param source   SIlang source code
     * @param fileName display name used in error messages
     * @param mode     what phase to stop at and what to print
     */
    private static void runSource(String source, String fileName, Mode mode) {
        List<String> sourceLines = List.of(source.split("\n", -1));

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

        if (mode == Mode.TOKENS) {
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
            errors.printAll(sourceLines);
            System.err.printf("%d parse error(s) in '%s'%n",
                errors.getErrors().size(), fileName);
            System.exit(EXIT_ERROR);
            return;
        }

        if (mode == Mode.AST) {
            printAst(statements, fileName);
            System.exit(EXIT_OK);
            return;
        }

        // ── Phase 3: Interpretation ──────────────────────────────────────
        Interpreter interpreter = new Interpreter(fileName, sourceLines);
        try {
            interpreter.interpret(statements);
        } catch (RuntimeError error) {
            System.err.println(interpreter.formatError(error));
            System.exit(EXIT_ERROR);
            return;
        }

        System.exit(EXIT_OK);
    }

    // ------------------------------------------------------------------ //
    //  Output: AST (--ast flag)                                         //
    // ------------------------------------------------------------------ //

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
    //  Output: tokens (--tokens flag)                                   //
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
            SIlang Interpreter — Version 0.1

            Usage:
              java silang.Main <file.si>              Run a SIlang program
              java silang.Main --expr "<source>"      Run inline source
              java silang.Main --stdin                Run source from stdin
              java silang.Main --ast <file.si>        Print AST, do not run
              java silang.Main --tokens <file.si>     Print tokens, do not run

            Examples:
              java silang.Main hello.si
              java silang.Main --expr "var x = 5 + 3"
              java silang.Main --expr "out(\\"Hello, World!\\")"
              echo 'out("Hi")' | java silang.Main --stdin
              java silang.Main --ast hello.si
              java silang.Main --tokens hello.si

            Exit codes:
              0  Success
              1  Lexer, parse, or runtime error
              2  File not found or I/O error
              3  Bad command-line arguments
            """);
    }
}
