package cz.university;

import cz.university.codegen.CodeGeneratorVisitor;
import cz.university.runtime.MachineException;
import cz.university.runtime.StackMachine;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class App {
    private static final String EXT = "lang";
    private static final String DIR = "src/test/resources/";
    public static void main(String[] args) throws IOException {
        String file = args.length == 0 ? "test." + EXT : args[0];
        System.out.println("START: " + file);

        CharStream input = CharStreams.fromFileName(DIR + file);
        cz.university.LanguageLexer lexer = new cz.university.LanguageLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        cz.university.LanguageParser parser = new cz.university.LanguageParser(tokens);

        VerboseListener errorListener = new VerboseListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        ParseTree tree = parser.program(); // start rule

        if (errorListener.getErrorCount() > 0) {
            System.err.println("Aborted due to syntax errors.");
            System.exit(1);
        }

        TypeCheckerVisitor checker = new TypeCheckerVisitor();
        checker.visit(tree);

        //System.out.println(checker.getSymbolTableDebug());

        if (!checker.getErrors().isEmpty()) {
            checker.getErrors().forEach(System.err::println);
            System.err.println("Aborted due to type errors.");
            System.exit(1);
        }

        System.out.println(tree.toStringTree(parser));

        CodeGeneratorVisitor generator = new CodeGeneratorVisitor(checker.getSymbolTable());
        generator.visit(tree);

        // === Save to file ===
        generator.saveToFile("output.out");
        System.out.println("Code successfully generated to output.out");

        List<String> instructions = Files.readAllLines(Paths.get("output.out"));
        StackMachine machine = new StackMachine();
        try {
            machine.execute(instructions);
        } catch (MachineException e) {
            System.err.println("Runtime error: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getMessage());
            }
            System.exit(2);
        }

        System.out.println("FINISH: " + file);
    }
}
