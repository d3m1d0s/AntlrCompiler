package cz.university;

import cz.university.codegen.CodeGeneratorVisitor;
import cz.university.codegen.Instruction;
import cz.university.runtime.MachineException;
import cz.university.runtime.StackMachine;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class AppTest {

    private List<Instruction> generate(String source) {
        CharStream input = CharStreams.fromString(source);
        cz.university.LanguageLexer lexer = new cz.university.LanguageLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        cz.university.LanguageParser parser = new cz.university.LanguageParser(tokens);
        ParseTree tree = parser.program();

        TypeCheckerVisitor checker = new TypeCheckerVisitor();
        checker.visit(tree);
        assertTrue("Type errors: " + checker.getErrors(), checker.getErrors().isEmpty());

        CodeGeneratorVisitor generator = new CodeGeneratorVisitor(checker.getSymbolTable());
        generator.visit(tree);
        List<Instruction> list = generator.getInstructions();
        return list;
    }

    private void run(String source) {
        List<String> program = generate(source).stream().map(Instruction::toString).toList();
        new StackMachine().execute(program);
    }

    private void runWithInput(String source, String input) {
        InputStream original = System.in;
        System.setIn(new ByteArrayInputStream(input.getBytes()));
        try {
            run(source);
        } finally {
            System.setIn(original);
        }
    }

    private record AppResult(int exitCode, String stdout, String stderr) {}

    /** Runs the App driver with captured streams, never touching the real ones. */
    private AppResult runApp(String stdin, String... args) {
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

    private Path sourceFile(String name, String source) throws IOException {
        Path file = scratchFile(name);
        Files.writeString(file, source);
        return file;
    }

    /** Returns a fresh path under target/, usable inside a program as a string literal. */
    private Path scratchFile(String name) throws IOException {
        Path dir = Path.of("target", "file-tests");
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        Files.deleteIfExists(file);
        return file;
    }

    /** Counts syntax errors the way App wires them up, swallowing the listener's printing. */
    private int syntaxErrors(String source) {
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try {
            CharStream input = CharStreams.fromString(source);
            cz.university.LanguageLexer lexer = new cz.university.LanguageLexer(input);
            VerboseListener listener = new VerboseListener();
            lexer.removeErrorListeners();
            lexer.addErrorListener(listener);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            cz.university.LanguageParser parser = new cz.university.LanguageParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(listener);
            parser.program();
            return listener.getErrorCount();
        } finally {
            System.setErr(originalErr);
        }
    }

    private List<String> typeErrors(String source) {
        CharStream input = CharStreams.fromString(source);
        cz.university.LanguageLexer lexer = new cz.university.LanguageLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        cz.university.LanguageParser parser = new cz.university.LanguageParser(tokens);
        ParseTree tree = parser.program();

        TypeCheckerVisitor checker = new TypeCheckerVisitor();
        checker.visit(tree);
        return checker.getErrors();
    }

    @Test
    public void testInitialValues() throws TypeException {
        SymbolTable st = new SymbolTable();
        st.declare("n", SymbolTable.Type.INT);
        st.declare("t", SymbolTable.Type.STRING);
        assertEquals(0, st.getValue("n"));
        assertEquals("", st.getValue("t"));
    }

    @Test
    public void testBoolAndFloatDefaults() throws TypeException {
        SymbolTable st = new SymbolTable();
        st.declare("b", SymbolTable.Type.BOOL);
        st.declare("f", SymbolTable.Type.FLOAT);
        assertEquals(false, st.getValue("b"));
        assertEquals(0.0, st.getValue("f"));
    }

    @Test(expected = TypeException.class)
    public void testDoubleDeclarationThrowsException() throws TypeException {
        SymbolTable st = new SymbolTable();
        st.declare("x", SymbolTable.Type.INT);
        st.declare("x", SymbolTable.Type.FLOAT);
    }

    @Test(expected = TypeException.class)
    public void testUndeclaredVariableThrowsException() throws TypeException {
        SymbolTable st = new SymbolTable();
        st.getType("y");
    }

    @Test
    public void testIntDeclarationAndAssignmentCodeGen() {
        String input = """
            int a;
            a = 42;
            """;
        List<Instruction> instr = generate(input);
        assertEquals("push I 0", instr.get(0).toString());
        assertEquals("save a", instr.get(1).toString());
        assertEquals("push I 42", instr.get(2).toString());
        assertEquals("save a", instr.get(3).toString());
    }

    @Test
    public void testFloatPromotionAssignmentCodeGen() {
        String input = """
            float c;
            c = 10;
            """;
        List<Instruction> instr = generate(input);
        assertEquals("push F 0.0", instr.get(0).toString());
        assertEquals("save c", instr.get(1).toString());
        assertEquals("push I 10", instr.get(2).toString());
        assertEquals("itof", instr.get(3).toString());
        assertEquals("save c", instr.get(4).toString());
    }

    @Test
    public void testStringDeclarationAndAssignment() {
        String input = """
            string msg;
            msg = "hello";
            """;
        List<Instruction> instr = generate(input);
        assertTrue(instr.stream().anyMatch(i -> i.toString().contains("push S \"hello\"")));
        assertTrue(instr.stream().anyMatch(i -> i.toString().contains("save msg")));
    }

    @Test
    public void testBoolAssignment() {
        String input = """
            bool m;
            m = true;
            """;
        List<Instruction> instr = generate(input);
        assertTrue(instr.stream().anyMatch(i -> i.toString().contains("push B true")));
        assertTrue(instr.stream().anyMatch(i -> i.toString().contains("save m")));
    }

    @Test
    public void testIdentifierLoad() {
        String input = """
        int x;
        int y;
        x = 5;
        y = x;
        """;
        List<Instruction> instr = generate(input);
        assertTrue(instr.stream().anyMatch(i -> i.toString().equals("load x")));
    }

    @Test
    public void testAddInt() {
        String input = """
        int a;
        int b;
        int result;
        a = 2;
        b = 3;
        result = a + b;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push I 0", "save result",

                "push I 2", "save a", "load a", "pop",
                "push I 3", "save b", "load b", "pop",
                "load a", "load b", "add I", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testSubFloatWithPromotion() {
        String input = """
        int a;
        float b;
        float result;
        a = 10;
        b = 2.5;
        result = a - b;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push F 0.0", "save b",
                "push F 0.0", "save result",

                "push I 10", "save a", "load a", "pop",
                "push F 2.5", "save b", "load b", "pop",
                "load a", "itof", "load b", "sub F", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testConcatString() {
        String input = """
        string s1;
        string s2;
        string result;
        s1 = "A";
        s2 = "B";
        result = s1 . s2;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push S \"\"", "save s1",
                "push S \"\"", "save s2",
                "push S \"\"", "save result",

                "push S \"A\"", "save s1", "load s1", "pop",
                "push S \"B\"", "save s2", "load s2", "pop",
                "load s1", "load s2", "concat", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }


    @Test
    public void testMultiplyInt() {
        String input = """
        int a;
        int b;
        int result;
        a = 3;
        b = 4;
        result = a * b;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push I 0", "save result",

                "push I 3", "save a", "load a", "pop",
                "push I 4", "save b", "load b", "pop",
                "load a", "load b", "mul I", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testModulo() {
        String input = """
        int a;
        int b;
        int result;
        a = 7;
        b = 3;
        result = a % b;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push I 0", "save result",

                "push I 7", "save a", "load a", "pop",
                "push I 3", "save b", "load b", "pop",
                "load a", "load b", "mod", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testEqualFloatPromotion() {
        String input = """
        int a;
        float b;
        bool result;
        a = 3;
        b = 3.0;
        result = a == b;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push F 0.0", "save b",
                "push B false", "save result",

                "push I 3", "save a", "load a", "pop",
                "push F 3.0", "save b", "load b", "pop",

                "load a", "itof", "load b", "eq F", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testEqualStrings() {
        String input = """
        string a;
        string b;
        bool result;
        a = "foo";
        b = "bar";
        result = a == b;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push S \"\"", "save a",
                "push S \"\"", "save b",
                "push B false", "save result",

                "push S \"foo\"", "save a", "load a", "pop",
                "push S \"bar\"", "save b", "load b", "pop",

                "load a", "load b", "eq S", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testEqualBools() {
        String input = """
        bool a;
        bool b;
        bool r;
        r = a == b;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push B false", "save a",
                "push B false", "save b",
                "push B false", "save r",

                "load a", "load b", "eq B", "save r", "load r", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testNotEqualBools() {
        String input = """
        bool a;
        bool r;
        r = a != true;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push B false", "save a",
                "push B false", "save r",

                "load a", "push B true", "eq B", "not", "save r", "load r", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testFileEqualityIsStillRejected() {
        List<String> errors = typeErrors("""
        file f;
        file g;
        write f == g;
        """);

        assertEquals(List.of("3,8: Invalid types for equality: FILE, FILE"), errors);
    }

    @Test
    public void testBoolEqualityRunsEndToEnd() throws IOException {
        Path src = sourceFile("cli-booleq.lang",
                "write true == false;\nwrite true != false;\nbool b;\nwrite b == false;\n");
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals(List.of("false", "true", "true"), r.stdout().lines().toList());
    }

    @Test
    public void testLessThanInt() {
        String input = """
        int a;
        int b;
        bool result;
        a = 1;
        b = 2;
        result = a < b;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push B false", "save result",

                "push I 1", "save a", "load a", "pop",
                "push I 2", "save b", "load b", "pop",

                "load a", "load b", "lt I", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testGreaterThanFloatWithPromotion() {
        String input = """
        int a;
        float b;
        bool result;
        a = 4;
        b = 2.5;
        result = a > b;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push F 0.0", "save b",
                "push B false", "save result",

                "push I 4", "save a", "load a", "pop",
                "push F 2.5", "save b", "load b", "pop",

                "load a", "itof", "load b", "gt F", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testNotBool() {
        String input = """
        bool a;
        bool result;
        a = false;
        result = !a;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push B false", "save a",
                "push B false", "save result",

                "push B false", "save a", "load a", "pop",

                "load a", "not", "save result", "load result", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testAndBool() {
        String input = """
        bool a;
        bool b;
        bool result;
        a = true;
        b = false;
        result = a && b;
        """;
        List<Instruction> instr = generate(input);
        List<String> expected = List.of(
                "push B false", "save a",
                "push B false", "save b",
                "push B false", "save result",

                "push B true", "save a", "load a", "pop",
                "push B false", "save b", "load b", "pop",

                "load a", "load b", "and", "save result", "load result", "pop"
        );
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testOrBool() {
        String input = """
        bool a;
        bool b;
        bool result;
        a = false;
        b = true;
        result = a || b;
        """;
        List<Instruction> instr = generate(input);
        List<String> expected = List.of(
                "push B false", "save a",
                "push B false", "save b",
                "push B false", "save result",

                "push B false", "save a", "load a", "pop",
                "push B true", "save b", "load b", "pop",

                "load a", "load b", "or", "save result", "load result", "pop"
        );
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testWriteStatement() {
        String input = """
        int a;
        float b;
        bool c;
        string d;
        a = 10;
        b = 3.14;
        c = true;
        d = "Hi!";
        write a, b, c, d;
        """;
        List<Instruction> instr = generate(input);
        List<String> expected = List.of(
                "push I 0", "save a",
                "push F 0.0", "save b",
                "push B false", "save c",
                "push S \"\"", "save d",

                "push I 10", "save a", "load a", "pop",
                "push F 3.14", "save b", "load b", "pop",
                "push B true", "save c", "load c", "pop",
                "push S \"Hi!\"", "save d", "load d", "pop",

                "load a", "load b", "load c", "load d",
                "print 4"
        );
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testReadStatement() {
        String input = """
        int a;
        float b;
        bool c;
        string d;
        read a, b, c, d;
        """;
        List<Instruction> instr = generate(input);
        List<String> expected = List.of(
                "push I 0", "save a",
                "push F 0.0", "save b",
                "push B false", "save c",
                "push S \"\"", "save d",

                "read I", "save a",
                "read F", "save b",
                "read B", "save c",
                "read S", "save d"
        );
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testIfStatement() {
        String input = """
        bool cond;
        int a;
        cond = true;
        if (cond) {
            a = 42;
        }
        """;
        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push B false", "save cond",
                "push I 0", "save a",

                "push B true", "save cond", "load cond", "pop",

                "load cond", "fjmp 0",
                "push I 42", "save a", "load a", "pop", "jmp 1",
                "label 0", "label 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testWhileStatement() {
        String input = """
        int a;
        a = 0;
        while (a < 3) {
            a = a + 1;
        }
        """;
        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push I 0", "save a", "load a", "pop",

                "label 0",
                "load a", "push I 3", "lt I", "fjmp 1",
                "load a", "push I 1", "add I", "save a", "load a", "pop",
                "jmp 0",
                "label 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testForStatement() {
        String input = """
        int a;
        a = 0;
        for (a = 0; a < 3; a = a + 1) {
            write(a);
        }
        """;
        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push I 0", "save a", "load a", "pop",
                "push I 0", "save a",

                "label 0",
                "load a", "push I 3", "lt I", "fjmp 1",

                "load a", "print 1",

                "load a", "push I 1", "add I", "save a",
                "jmp 0",
                "label 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testUnaryMinusAsBinaryOperand() {
        String input = """
        write 2 * -3;
        """;
        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 2", "push I 3", "uminus I", "mul I", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testUnaryMinusOperandWithFloatPromotion() {
        String input = """
        write 1.5 + -1;
        """;
        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push F 1.5", "push I 1", "uminus I", "itof", "add F", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testUnaryMinusInAssignment() {
        String input = """
        int a;
        a = 1 + -1;
        """;
        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push I 1", "push I 1", "uminus I", "add I", "save a", "load a", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testUnaryMinusBindsTighterThanAddition() {
        String input = """
        write -2 + 3;
        """;
        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 2", "uminus I", "push I 3", "add I", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testUnaryMinusBindsTighterThanComparison() {
        String input = """
        write -2 < 1;
        """;
        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 2", "uminus I", "push I 1", "lt I", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testNotBindsTighterThanAnd() {
        String input = """
        bool r;
        r = !false && false;
        """;
        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push B false", "save r",
                "push B false", "not", "push B false", "and", "save r", "load r", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testNotOnNonBoolIsReportedAsTypeError() {
        List<String> errors = typeErrors("write !5;\n");

        assertEquals(1, errors.size());
        assertEquals("1,6: Logical '!' requires bool operand. Got: INT", errors.get(0));
    }

    @Test
    public void testUnaryMinusOnStringIsReportedAsTypeError() {
        List<String> errors = typeErrors("write -\"abc\";\n");

        assertEquals(1, errors.size());
        assertEquals("1,6: Unary minus requires int or float. Got: STRING", errors.get(0));
    }

    @Test
    public void testReadIntoFileVariableIsReportedAsTypeError() {
        List<String> errors = typeErrors("""
        file f;
        read f;
        """);

        assertEquals(1, errors.size());
        assertEquals("2,5: Variable 'f' has unsupported type for read: FILE", errors.get(0));
    }

    @Test
    public void testChainAssignment() {
        String input = """
        int i, j, k;
        i = j = k = 55;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save i",
                "push I 0", "save j",
                "push I 0", "save k",
                "push I 55", "save k",
                "load k", "save j",
                "load j", "save i", "load i", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testUndeclaredVariableInForInitIsReportedAsTypeError() {
        List<String> errors = typeErrors("for (q = 0; true; ) ;\n");

        assertEquals(1, errors.size());
        assertEquals("1,5: Variable 'q' is not declared.", errors.get(0));
    }

    @Test
    public void testUndeclaredVariableInForUpdateIsReportedAsTypeError() {
        List<String> errors = typeErrors("""
        int i;
        for (i = 0; true; q = 1) ;
        """);

        assertEquals(1, errors.size());
        assertEquals("2,18: Variable 'q' is not declared.", errors.get(0));
    }

    @Test
    public void testIncompatibleTypeInForInitIsReportedAsTypeError() {
        List<String> errors = typeErrors("""
        int i;
        for (i = "text"; true; ) ;
        """);

        assertEquals(1, errors.size());
        assertEquals("2,5: Incompatible types in for-init: INT and STRING", errors.get(0));
    }

    @Test
    public void testForInitPromotesIntToFloat() {
        String input = """
        float f;
        for (f = 0; f < 2.0; f = f + 1) write f;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push F 0.0", "save f",
                "push I 0", "itof", "save f",
                "label 0",
                "load f", "push F 2.0", "lt F", "fjmp 1",
                "load f", "print 1",
                "load f", "push I 1", "itof", "add F", "save f",
                "jmp 0",
                "label 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testForUpdatePromotesIntToFloat() {
        String input = """
        float f;
        for (f = 0.0; f < 1.0; f = 2) write f;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push F 0.0", "save f",
                "push F 0.0", "save f",
                "label 0",
                "load f", "push F 1.0", "lt F", "fjmp 1",
                "load f", "print 1",
                "push I 2", "itof", "save f",
                "jmp 0",
                "label 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testAssignmentInsideExpressionStoresAndYieldsValue() {
        String input = """
        int a, b;
        b = (a = 5) + 1;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push I 5", "save a", "load a",
                "push I 1", "add I",
                "save b", "load b", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testAssignmentInsideWriteKeepsValueOnStack() {
        String input = """
        int a;
        write (a = 5);
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save a",
                "push I 5", "save a", "load a", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testChainedAssignmentInForInit() {
        String input = """
        int i, j;
        for (i = j = 0; i < 2; i = i + 1) write i;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push I 0", "save i",
                "push I 0", "save j",
                "push I 0", "save j", "load j", "save i",
                "label 0",
                "load i", "push I 2", "lt I", "fjmp 1",
                "load i", "print 1",
                "load i", "push I 1", "add I", "save i",
                "jmp 0",
                "label 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testChainedAssignmentPromotesPerVariable() {
        String input = """
        float f;
        int i;
        f = i = 5;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push F 0.0", "save f",
                "push I 0", "save i",
                "push I 5", "save i", "load i", "itof", "save f", "load f", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testFileAppendExpr() {
        String input = """
        file f;
        f = "file_append_output.txt";
        f << "Hello, ";
        f << "World!";
        f << 1 << "A" << 2;
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push S \"file_append_output.txt\"", "push S \"a\"", "fopen", "save f", "load f", "pop",
                "load f", "push S \"Hello, \"", "fappend 1", "pop",
                "load f", "push S \"World!\"", "fappend 1", "pop",
                "load f", "push I 1", "push S \"A\"", "push I 2", "fappend 3", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testFileOpenAndAppend() {
        String input = """
        file f;
        f = open("output.txt", "a");
        f << "Line 1" << 42;
        f = open("output.txt", "w");
        f << "Overwrite";
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(

                // f = open("output.txt", "a")
                "push S \"output.txt\"", "push S \"a\"", "fopen", "save f", "load f", "pop",

                // f << "Line 1" << 42
                "load f", "push S \"Line 1\"", "push I 42", "fappend 2", "pop",

                // f = open("output.txt", "w")
                "push S \"output.txt\"", "push S \"w\"", "fopen", "save f", "load f", "pop",

                // f << "Overwrite"
                "load f", "push S \"Overwrite\"", "fappend 1", "pop"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }


    @Test
    public void testReadIntToleratesSurroundingWhitespace() throws IOException {
        Path file = scratchFile("read-int.txt");

        runWithInput("""
            int a;
            read a;
            file f;
            f = open("%s", "w");
            f << a;
            """.formatted(file.toString().replace('\\', '/')), " 42 \n");

        assertEquals(List.of("42"), Files.readAllLines(file));
    }

    @Test
    public void testReadInvalidIntReportsTypeAndInput() {
        MachineException e = assertThrows(MachineException.class,
                () -> runWithInput("int a;\nread a;\n", "abc\n"));
        assertEquals("Invalid int input: \"abc\"", e.getMessage());
    }

    @Test
    public void testReadInvalidFloatReportsTypeAndInput() {
        MachineException e = assertThrows(MachineException.class,
                () -> runWithInput("float x;\nread x;\n", "3,14\n"));
        assertEquals("Invalid float input: \"3,14\"", e.getMessage());
    }

    @Test
    public void testReadOnExhaustedInputReportsEndOfInput() {
        MachineException e = assertThrows(MachineException.class,
                () -> runWithInput("int a;\nread a;\n", ""));
        assertEquals("Input ended while reading int", e.getMessage());
    }

    @Test
    public void testReadBoolIsStrict() throws IOException {
        MachineException e = assertThrows(MachineException.class,
                () -> runWithInput("bool b;\nread b;\n", "yes\n"));
        assertEquals("Invalid bool input: \"yes\"", e.getMessage());

        Path file = scratchFile("read-bool.txt");
        runWithInput("""
            bool b;
            read b;
            file f;
            f = open("%s", "w");
            if (b) f << "istrue"; else f << "isfalse";
            """.formatted(file.toString().replace('\\', '/')), "TRUE\n");

        assertEquals(List.of("istrue"), Files.readAllLines(file));
    }

    @Test
    public void testIntDivisionByZeroRaisesMachineError() {
        MachineException e = assertThrows(MachineException.class, () -> run("write 1 / 0;\n"));
        assertEquals("Division by zero", e.getMessage());
    }

    @Test
    public void testModuloByZeroRaisesMachineError() {
        MachineException e = assertThrows(MachineException.class, () -> run("write 5 % 0;\n"));
        assertEquals("Division by zero", e.getMessage());
    }

    @Test
    public void testWriteModeTruncatesAtOpenOnly() throws IOException {
        Path file = scratchFile("write-mode.txt");
        Files.writeString(file, "stale content\n");

        run("""
            file f;
            f = open("%s", "w");
            f << "first";
            f << "second";
            """.formatted(file.toString().replace('\\', '/')));

        assertEquals(List.of("first", "second"), Files.readAllLines(file));
    }

    @Test
    public void testAppendModeKeepsExistingContent() throws IOException {
        Path file = scratchFile("append-mode.txt");
        Files.writeString(file, "existing\n");

        run("""
            file f;
            f = open("%s", "a");
            f << "added";
            """.formatted(file.toString().replace('\\', '/')));

        assertEquals(List.of("existing", "added"), Files.readAllLines(file));
    }

    @Test
    public void testOpeningAnotherFileLeavesTheFirstUntouched() throws IOException {
        Path first = scratchFile("first.txt");
        Path second = scratchFile("second.txt");

        run("""
            file f;
            file g;
            f = open("%s", "a");
            f << "keep";
            g = open("%s", "w");
            f << "also keep";
            """.formatted(first.toString().replace('\\', '/'),
                          second.toString().replace('\\', '/')));

        assertEquals(List.of("keep", "also keep"), Files.readAllLines(first));
    }

    @Test
    public void testOpenCreatesTheFile() throws IOException {
        Path file = scratchFile("created.txt");

        run("""
            file f;
            f = open("%s", "w");
            """.formatted(file.toString().replace('\\', '/')));

        assertTrue(Files.exists(file));
        assertEquals(List.of(), Files.readAllLines(file));
    }

    @Test
    public void testFileNameWithSpacesSurvivesInstructionEncoding() throws IOException {
        Path file = scratchFile("with space.txt");

        run("""
            file f;
            f = open("%s", "w");
            f << "content";
            """.formatted(file.toString().replace('\\', '/')));

        assertEquals(List.of("content"), Files.readAllLines(file));
    }

    @Test
    public void testEmptyFileNameFailsCleanly() {
        // A literal empty name in open() is a compile error; via the string
        // form it is only known at run time and must fail as an open failure,
        // not as a crash while parsing the push instruction.
        MachineException e = assertThrows(MachineException.class, () -> run("""
            file f;
            f = "";
            """));
        assertTrue("unexpected message: " + e.getMessage(),
                e.getMessage().startsWith("Failed to open file:"));
        assertNotNull("I/O failures must keep their cause", e.getCause());
    }

    @Test
    public void testSyntaxErrorIsCountedAndDoesNotKillTheProcess() {
        // The listener used to call System.exit on the first error; getting a
        // count back at all proves reporting no longer terminates the JVM.
        assertTrue(syntaxErrors("int a\n") > 0);
        assertEquals(0, syntaxErrors("int a;\n"));
    }

    @Test
    public void testLexerErrorsAreCounted() {
        // Characters the lexer cannot tokenize must count as syntax errors;
        // they used to slip through and the program compiled and ran anyway.
        assertEquals(2, syntaxErrors("@ $\n"));
    }

    @Test
    public void testMultipleSyntaxErrorsAreReported() {
        assertTrue(syntaxErrors("int a\nint b\n") >= 2);
    }

    @Test
    public void testUndeclaredVariableErrorsCarryPositions() {
        List<String> errors = typeErrors("""
        x = 5;
        write y;
        int x, x;
        """);

        assertEquals(List.of(
                "1,0: Variable 'x' is not declared.",
                "2,6: Variable 'y' is not declared.",
                "3,7: Variable 'x' is already declared."
        ), errors);
    }

    @Test
    public void testOneErrorPerMistakeInAssignment() {
        List<String> errors = typeErrors("int a;\na = \"s\" + 1;\n");

        assertEquals(List.of(
                "2,8: Invalid operands for arithmetic operation: STRING, INT"
        ), errors);
    }

    @Test
    public void testErroredOperandDoesNotCascadeIntoNot() {
        List<String> errors = typeErrors("bool b;\nb = !x;\n");

        assertEquals(List.of("2,5: Variable 'x' is not declared."), errors);
    }

    @Test
    public void testErroredOperandsDoNotCascadeIntoLogicalOperator() {
        List<String> errors = typeErrors("bool b;\nb = x && y;\n");

        assertEquals(List.of(
                "2,4: Variable 'x' is not declared.",
                "2,9: Variable 'y' is not declared."
        ), errors);
    }

    @Test
    public void testUndeclaredVariableReportedOncePerName() {
        List<String> errors = typeErrors("for (q = 0; q < 3; q = q + 1) ;\n");

        assertEquals(List.of("1,5: Variable 'q' is not declared."), errors);
    }

    @Test
    public void testNonBoolForConditionStillReported() {
        List<String> errors = typeErrors("int i;\nfor (i = 0; i + 1; i = i + 1) ;\n");

        assertEquals(List.of("2,12: Condition in for loop must be bool, got INT"), errors);
    }

    @Test
    public void testIntLiteralOutOfRangeIsReportedAsTypeError() {
        List<String> errors = typeErrors("write 2147483648;\n");

        assertEquals(1, errors.size());
        assertEquals("1,6: Integer literal out of range: 2147483648", errors.get(0));
    }

    @Test
    public void testIntLiteralAtRangeBoundaryCompiles() {
        List<Instruction> instr = generate("write 2147483647;\n");

        assertEquals("push I 2147483647", instr.get(0).toString());
    }

    @Test
    public void testMostNegativeIntLiteralIsRejected() {
        // "-2147483648" is unary minus applied to the literal 2147483648, which
        // itself does not fit in an int, exactly as in C and Java source.
        List<String> errors = typeErrors("write -2147483648;\n");

        assertEquals(1, errors.size());
        assertEquals("1,7: Integer literal out of range: 2147483648", errors.get(0));
    }

    @Test
    public void testInvalidOpenModeIsReportedAsTypeError() {
        List<String> errors = typeErrors("""
        file f;
        f = open("x.txt", "r");
        """);

        assertEquals(1, errors.size());
        assertEquals("2,18: File open mode must be \"w\" or \"a\". Got: \"r\"", errors.get(0));
    }

    @Test
    public void testEmptyFileNameInOpenIsReportedAsTypeError() {
        List<String> errors = typeErrors("""
        file f;
        f = open("", "w");
        """);

        assertEquals(1, errors.size());
        assertEquals("2,9: File name in open() must not be empty.", errors.get(0));
    }

    @Test
    public void testUnopenedFileVariableFailsWithClearMessage() {
        MachineException e = assertThrows(MachineException.class, () -> run("""
            file f;
            f << "data";
            """));
        assertEquals("Variable 'f' was never assigned a value", e.getMessage());
    }

    @Test
    public void testFileToFileAssignment() throws IOException {
        Path file = scratchFile("shared.txt");

        run("""
            file f;
            file g;
            f = open("%s", "w");
            f << "via f";
            g = f;
            g << "via g";
            """.formatted(file.toString().replace('\\', '/')));

        assertEquals(List.of("via f", "via g"), Files.readAllLines(file));
    }

    @Test
    public void testChainedFileAssignment() throws IOException {
        Path file = scratchFile("chained.txt");

        run("""
            file f1;
            file f2;
            f1 = f2 = "%s";
            f1 << "one";
            f2 << "two";
            """.formatted(file.toString().replace('\\', '/')));

        assertEquals(List.of("one", "two"), Files.readAllLines(file));
    }

    @Test
    public void testParenthesizedAppendChain() throws IOException {
        Path file = scratchFile("paren-chain.txt");

        run("""
            file f;
            f = open("%s", "w");
            (f << 1) << 2;
            """.formatted(file.toString().replace('\\', '/')));

        assertEquals(List.of("1", "2"), Files.readAllLines(file));
    }

    @Test
    public void testOpenOutsideAssignmentYieldsHandle() {
        String input = """
        write open("target/file-tests/inline.txt", "w");
        """;

        List<Instruction> instr = generate(input);

        List<String> expected = List.of(
                "push S \"target/file-tests/inline.txt\"", "push S \"w\"", "fopen", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testFopenReadsExactlyTwoOperands() throws IOException {
        Path leftover = scratchFile("leftover.txt");
        Files.writeString(leftover, "must survive\n");
        Path opened = scratchFile("opened.txt");

        // A value left on the stack in front of the operands must not be mistaken
        // for the file name, which would truncate a file the program never opened.
        new StackMachine().execute(List.of(
                "push S " + leftover.toString().replace('\\', '/'),
                "push S " + opened.toString().replace('\\', '/'),
                "push S \"w\"",
                "fopen"));

        assertEquals(List.of("must survive"), Files.readAllLines(leftover));
        assertTrue(Files.exists(opened));
    }

    @Test
    public void testFileNameAssignmentAppends() throws IOException {
        Path file = scratchFile("named.txt");
        Files.writeString(file, "existing\n");

        run("""
            file f;
            f = "%s";
            f << "added";
            """.formatted(file.toString().replace('\\', '/')));

        assertEquals(List.of("existing", "added"), Files.readAllLines(file));
    }

    @Test
    public void testEscapeSequencesAreDecoded() throws IOException {
        Path src = sourceFile("cli-escapes.lang", "write \"a\\nb\\t\\\"q\\\"\\\\\";\n");
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals("a\nb\t\"q\"\\", r.stdout().trim());
    }

    @Test
    public void testStringOperandSerializationStaysSingleLine() {
        List<Instruction> instr = generate("write \"a\\nb\";\n");

        assertEquals("push S \"a\\nb\"", instr.get(0).toString());
    }

    @Test
    public void testEscapesReachProgramFiles() throws IOException {
        Path file = scratchFile("escapes.txt");

        run("""
            file f;
            f = open("%s", "w");
            f << "x\\ny";
            """.formatted(file.toString().replace('\\', '/')));

        assertEquals(List.of("x", "y"), Files.readAllLines(file));
    }

    @Test
    public void testUnknownEscapeIsReportedAsTypeError() {
        List<String> errors = typeErrors("write \"a\\qb\";\n");

        assertEquals(List.of("1,6: Unknown escape sequence '\\q' in string literal"), errors);
    }

    @Test
    public void testRawLineBreakInStringIsSyntaxError() {
        assertTrue(syntaxErrors("write \"a\nb\";\n") > 0);
    }

    @Test
    public void testAppCompilesAndRunsAProgram() throws IOException {
        Path src = sourceFile("cli-ok.lang", "write 1 + 1;\n");
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals("2", r.stdout().trim());
        assertEquals("", r.stderr());
    }

    @Test
    public void testAppReadsFromStdin() throws IOException {
        Path src = sourceFile("cli-read.lang", "int a;\nread a;\nwrite a * 2;\n");
        AppResult r = runApp("21\n", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals("42", r.stdout().trim());
    }

    @Test
    public void testAppWithoutArgumentsPrintsUsage() {
        AppResult r = runApp("");

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("Usage"));
    }

    @Test
    public void testAppReportsUnreadableFile() {
        AppResult r = runApp("", "no-such-file.lang");

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("Cannot read file: no-such-file.lang"));
    }

    @Test
    public void testAppExitsWithOneOnSyntaxErrors() throws IOException {
        Path src = sourceFile("cli-syntax.lang", "int a\n");
        AppResult r = runApp("", src.toString());

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("Aborted due to syntax errors."));
        assertEquals("", r.stdout());
    }

    @Test
    public void testAppExitsWithOneOnTypeErrors() throws IOException {
        Path src = sourceFile("cli-type.lang", "write x;\n");
        AppResult r = runApp("", src.toString());

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("Variable 'x' is not declared."));
        assertTrue(r.stderr().contains("Aborted due to type errors."));
        assertEquals("", r.stdout());
    }

    @Test
    public void testAppExitsWithTwoOnRuntimeErrors() throws IOException {
        Path src = sourceFile("cli-runtime.lang", "write 1 / 0;\n");
        AppResult r = runApp("", src.toString());

        assertEquals(2, r.exitCode());
        assertTrue(r.stderr().contains("Runtime error: Division by zero"));
    }

    @Test
    public void testAllInputsAgainstReferenceOutputs() throws IOException {

        for (int testNum = 1; testNum <= 3; testNum++) {

            Path inputPath = Path.of("src/test/resources/PLC_t" + testNum + ".in");
            Path expectedOutputPath = Path.of("src/test/resources/PLC_t" + testNum + ".out");

            String source = Files.readString(inputPath);
            List<Instruction> instructions = generate(source);

            Path outPath = Path.of("output_t" + testNum + ".out");
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outPath))) {
                for (Instruction instr : instructions) {
                    writer.println(instr);
                }
            }

            List<String> actual = Files.readAllLines(outPath);
            List<String> expected = Files.readAllLines(expectedOutputPath);

            if (!actual.equals(expected)) {
                System.err.println("Mismatch for PLC_t" + testNum);
                System.err.println("------ DIFF ------");

                int maxLines = Math.max(actual.size(), expected.size());
                for (int i = 0; i < maxLines; i++) {
                    String act = i < actual.size() ? actual.get(i) : "<missing>";
                    String exp = i < expected.size() ? expected.get(i) : "<missing>";
                    if (!act.equals(exp)) {
                        System.err.printf("Line %d:\n  Expected: %s\n  Actual:   %s\n", i + 1, exp, act);
                    }
                }

                fail("Generated output_t" + testNum + ".out does not match PLC_t" + testNum + ".out");
            }
        }
    }


}
