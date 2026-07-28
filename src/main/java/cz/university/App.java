package cz.university;

import cz.university.codegen.CodeGeneratorVisitor;
import cz.university.codegen.Instruction;
import cz.university.runtime.MachineException;
import cz.university.runtime.StackMachine;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.util.List;

public class App {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * Compiles and runs one source file. Returns the process exit code:
     * 0 on success, 1 for compile errors, 2 for runtime errors.
     */
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

        // The listing is an artifact for inspection; execution does not depend on it.
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
