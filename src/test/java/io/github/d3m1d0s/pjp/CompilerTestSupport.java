package io.github.d3m1d0s.pjp;

import io.github.d3m1d0s.pjp.codegen.CodeGeneratorVisitor;
import io.github.d3m1d0s.pjp.codegen.Instruction;
import io.github.d3m1d0s.pjp.runtime.StackMachine;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

// shared plumbing for the test classes: compile, run and drive helpers
abstract class CompilerTestSupport {

    // compiles source to instructions, failing the test on any type error
    static List<Instruction> generate(String source) {
        CharStream input = CharStreams.fromString(source);
        LanguageLexer lexer = new LanguageLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LanguageParser parser = new LanguageParser(tokens);
        ParseTree tree = parser.program();

        TypeCheckerVisitor checker = new TypeCheckerVisitor();
        checker.visit(tree);
        assertTrue(checker.getErrors().isEmpty(), "Type errors: " + checker.getErrors());

        CodeGeneratorVisitor generator = new CodeGeneratorVisitor(checker.getSymbolTable());
        generator.visit(tree);
        return generator.getInstructions();
    }

    static List<String> lines(List<Instruction> instructions) {
        return instructions.stream().map(Instruction::toString).toList();
    }

    static void run(String source) {
        new StackMachine().execute(lines(generate(source)));
    }

    static void runWithInput(String source, String input) {
        InputStream original = System.in;
        System.setIn(new ByteArrayInputStream(input.getBytes()));
        try {
            run(source);
        } finally {
            System.setIn(original);
        }
    }

    record AppResult(int exitCode, String stdout, String stderr) {}

    // runs the App driver with System.in/out/err swapped for buffers
    static AppResult runApp(String stdin, String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        InputStream originalIn = System.in;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
        System.setIn(new ByteArrayInputStream(stdin.getBytes()));
        try {
            int code = App.run(args);
            return new AppResult(code, out.toString(), err.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            System.setIn(originalIn);
        }
    }

    static Path sourceFile(String name, String source) throws IOException {
        Path file = scratchFile(name);
        Files.writeString(file, source);
        return file;
    }

    // fresh path under target/, callers flip backslashes before embedding it in a program literal
    static Path scratchFile(String name) throws IOException {
        Path dir = Path.of("target", "file-tests");
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        Files.deleteIfExists(file);
        return file;
    }

    // counts syntax errors with the same listener wiring as App, printing swallowed
    static int syntaxErrors(String source) {
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try {
            CharStream input = CharStreams.fromString(source);
            LanguageLexer lexer = new LanguageLexer(input);
            VerboseListener listener = new VerboseListener();
            lexer.removeErrorListeners();
            lexer.addErrorListener(listener);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            LanguageParser parser = new LanguageParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(listener);
            parser.program();
            return listener.getErrorCount();
        } finally {
            System.setErr(originalErr);
        }
    }

    static List<String> typeErrors(String source) {
        CharStream input = CharStreams.fromString(source);
        LanguageLexer lexer = new LanguageLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LanguageParser parser = new LanguageParser(tokens);
        ParseTree tree = parser.program();

        TypeCheckerVisitor checker = new TypeCheckerVisitor();
        checker.visit(tree);
        return checker.getErrors();
    }
}
