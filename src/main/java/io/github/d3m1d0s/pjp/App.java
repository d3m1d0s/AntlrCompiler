package io.github.d3m1d0s.pjp;

import io.github.d3m1d0s.pjp.codegen.CodeGeneratorVisitor;
import io.github.d3m1d0s.pjp.codegen.Instruction;
import io.github.d3m1d0s.pjp.runtime.MachineException;
import io.github.d3m1d0s.pjp.runtime.StackMachine;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.util.List;

/**
 * Command-line driver: compiles one source file and runs it on the stack
 * machine. Exits 0 on success, 1 for bad usage, unreadable input or compile
 * errors, 2 for runtime errors.
 */
public class App {

    public static void main(String[] args) {
        // exit code comes from run() so tests can drive the pipeline without System.exit
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: App <source file>");
            return 1;
        }

        CharStream input;
        try {
            input = CharStreams.fromFileName(args[0]);
        } catch (IOException e) {
            System.err.println("Cannot read file: " + args[0] + " (" + e.getMessage() + ")");
            return 1;
        }

        LanguageLexer lexer = new LanguageLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LanguageParser parser = new LanguageParser(tokens);

        VerboseListener errorListener = new VerboseListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        ParseTree tree = parser.program();

        if (errorListener.getErrorCount() > 0) {
            System.err.println("Aborted due to syntax errors.");
            return 1;
        }

        TypeCheckerVisitor checker = new TypeCheckerVisitor();
        checker.visit(tree);

        if (!checker.getErrors().isEmpty()) {
            checker.getErrors().forEach(System.err::println);
            System.err.println("Aborted due to type errors.");
            return 1;
        }

        CodeGeneratorVisitor generator = new CodeGeneratorVisitor(checker.getSymbolTable());
        generator.visit(tree);

        // the listing is only for inspection, so a failed write is a warning, not an abort
        try {
            generator.saveToFile("output.out");
        } catch (IOException e) {
            System.err.println("Warning: could not write output.out (" + e.getMessage() + ")");
        }

        List<String> instructions = generator.getInstructions().stream()
                .map(Instruction::toString)
                .toList();

        try {
            new StackMachine().execute(instructions);
        } catch (MachineException e) {
            System.err.println("Runtime error: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getMessage());
            }
            return 2;
        }

        return 0;
    }
}
