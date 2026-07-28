package cz.university;

import cz.university.codegen.CodeGeneratorVisitor;
import cz.university.codegen.Instruction;
import cz.university.runtime.StackMachine;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.junit.Test;

import java.io.IOException;
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

    /** Returns a fresh path under target/, usable inside a program as a string literal. */
    private Path scratchFile(String name) throws IOException {
        Path dir = Path.of("target", "file-tests");
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        Files.deleteIfExists(file);
        return file;
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
        st.declare("n", SymbolTable.Type.INT, 1);
        st.declare("t", SymbolTable.Type.STRING, 1);
        assertEquals(0, st.getValue("n", 1));
        assertEquals("", st.getValue("t", 1));
    }

    @Test
    public void testBoolAndFloatDefaults() throws TypeException {
        SymbolTable st = new SymbolTable();
        st.declare("b", SymbolTable.Type.BOOL, 1);
        st.declare("f", SymbolTable.Type.FLOAT, 1);
        assertEquals(false, st.getValue("b", 1));
        assertEquals(0.0, st.getValue("f", 1));
    }

    @Test(expected = TypeException.class)
    public void testDoubleDeclarationThrowsException() throws TypeException {
        SymbolTable st = new SymbolTable();
        st.declare("x", SymbolTable.Type.INT, 1);
        st.declare("x", SymbolTable.Type.FLOAT, 2);
    }

    @Test(expected = TypeException.class)
    public void testUndeclaredVariableThrowsException() throws TypeException {
        SymbolTable st = new SymbolTable();
        st.getType("y", 3);
    }

    @Test
    public void testIntDeclarationAndAssignmentCodeGen() {
        System.out.println("---- testIntDeclarationAndAssignmentCodeGen ----");
        String input = """
            int a;
            a = 42;
            """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);
        assertEquals("push I 0", instr.get(0).toString());
        assertEquals("save a", instr.get(1).toString());
        assertEquals("push I 42", instr.get(2).toString());
        assertEquals("save a", instr.get(3).toString());
    }

    @Test
    public void testFloatPromotionAssignmentCodeGen() {
        System.out.println("---- testFloatPromotionAssignmentCodeGen ----");
        String input = """
            float c;
            c = 10;
            """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);
        assertEquals("push F 0.0", instr.get(0).toString());
        assertEquals("save c", instr.get(1).toString());
        assertEquals("push I 10", instr.get(2).toString());
        assertEquals("itof", instr.get(3).toString());
        assertEquals("save c", instr.get(4).toString());
    }

    @Test
    public void testStringDeclarationAndAssignment() {
        System.out.println("---- testStringDeclarationAndAssignment ----");
        String input = """
            string msg;
            msg = "hello";
            """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);
        assertTrue(instr.stream().anyMatch(i -> i.toString().contains("push S \"hello\"")));
        assertTrue(instr.stream().anyMatch(i -> i.toString().contains("save msg")));
    }

    @Test
    public void testBoolAssignment() {
        System.out.println("---- testBoolAssignment ----");
        String input = """
            bool m;
            m = true;
            """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);
        assertTrue(instr.stream().anyMatch(i -> i.toString().contains("push B true")));
        assertTrue(instr.stream().anyMatch(i -> i.toString().contains("save m")));
    }

    @Test
    public void testIdentifierLoad() {
        System.out.println("---- testIdentifierLoad ----");
        String input = """
        int x;
        int y;
        x = 5;
        y = x;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);
        assertTrue(instr.stream().anyMatch(i -> i.toString().equals("load x")));
    }

    @Test
    public void testAddInt() {
        System.out.println("---- testAddInt ----");
        String input = """
        int a;
        int b;
        int result;
        a = 2;
        b = 3;
        result = a + b;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testSubFloatWithPromotion ----");
        String input = """
        int a;
        float b;
        float result;
        a = 10;
        b = 2.5;
        result = a - b;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testConcatString ----");
        String input = """
        string s1;
        string s2;
        string result;
        s1 = "A";
        s2 = "B";
        result = s1 . s2;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testMultiplyInt ----");
        String input = """
        int a;
        int b;
        int result;
        a = 3;
        b = 4;
        result = a * b;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testModulo ----");
        String input = """
        int a;
        int b;
        int result;
        a = 7;
        b = 3;
        result = a % b;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testEqualFloatPromotion ----");
        String input = """
        int a;
        float b;
        bool result;
        a = 3;
        b = 3.0;
        result = a == b;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testEqualStrings ----");
        String input = """
        string a;
        string b;
        bool result;
        a = "foo";
        b = "bar";
        result = a == b;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
    public void testLessThanInt() {
        System.out.println("---- testLessThanInt ----");
        String input = """
        int a;
        int b;
        bool result;
        a = 1;
        b = 2;
        result = a < b;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testGreaterThanFloatWithPromotion ----");
        String input = """
        int a;
        float b;
        bool result;
        a = 4;
        b = 2.5;
        result = a > b;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testNotBool ----");
        String input = """
        bool a;
        bool result;
        a = false;
        result = !a;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testAndBool ----");
        String input = """
        bool a;
        bool b;
        bool result;
        a = true;
        b = false;
        result = a && b;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);
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
        System.out.println("---- testOrBool ----");
        String input = """
        bool a;
        bool b;
        bool result;
        a = false;
        b = true;
        result = a || b;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);
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
        System.out.println("---- testWriteStatement ----");
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
        instr.forEach(System.out::println);
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
        System.out.println("---- testReadStatement ----");
        String input = """
        int a;
        float b;
        bool c;
        string d;
        read a, b, c, d;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);
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
        System.out.println("---- testIfStatement ----");
        String input = """
        bool cond;
        int a;
        cond = true;
        if (cond) {
            a = 42;
        }
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testWhileStatement ----");
        String input = """
        int a;
        a = 0;
        while (a < 3) {
            a = a + 1;
        }
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testForStatement ----");
        String input = """
        int a;
        a = 0;
        for (a = 0; a < 3; a = a + 1) {
            write(a);
        }
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testUnaryMinusAsBinaryOperand ----");
        String input = """
        write 2 * -3;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

        List<String> expected = List.of(
                "push I 2", "push I 3", "uminus I", "mul I", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testUnaryMinusOperandWithFloatPromotion() {
        System.out.println("---- testUnaryMinusOperandWithFloatPromotion ----");
        String input = """
        write 1.5 + -1;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

        List<String> expected = List.of(
                "push F 1.5", "push I 1", "uminus I", "itof", "add F", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testUnaryMinusInAssignment() {
        System.out.println("---- testUnaryMinusInAssignment ----");
        String input = """
        int a;
        a = 1 + -1;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testUnaryMinusBindsTighterThanAddition ----");
        String input = """
        write -2 + 3;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

        List<String> expected = List.of(
                "push I 2", "uminus I", "push I 3", "add I", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testUnaryMinusBindsTighterThanComparison() {
        System.out.println("---- testUnaryMinusBindsTighterThanComparison ----");
        String input = """
        write -2 < 1;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

        List<String> expected = List.of(
                "push I 2", "uminus I", "push I 1", "lt I", "print 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testNotBindsTighterThanAnd() {
        System.out.println("---- testNotBindsTighterThanAnd ----");
        String input = """
        bool r;
        r = !false && false;
        """;
        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testNotOnNonBoolIsReportedAsTypeError ----");
        List<String> errors = typeErrors("write !5;\n");
        errors.forEach(System.out::println);

        assertEquals(1, errors.size());
        assertEquals("1,6: Logical '!' requires bool operand. Got: INT", errors.get(0));
    }

    @Test
    public void testUnaryMinusOnStringIsReportedAsTypeError() {
        System.out.println("---- testUnaryMinusOnStringIsReportedAsTypeError ----");
        List<String> errors = typeErrors("write -\"abc\";\n");
        errors.forEach(System.out::println);

        assertEquals(1, errors.size());
        assertEquals("1,6: Unary minus requires int or float. Got: STRING", errors.get(0));
    }

    @Test
    public void testReadIntoFileVariableIsReportedAsTypeError() {
        System.out.println("---- testReadIntoFileVariableIsReportedAsTypeError ----");
        List<String> errors = typeErrors("""
        file f;
        read f;
        """);
        errors.forEach(System.out::println);

        assertEquals(1, errors.size());
        assertEquals("2,5: Variable 'f' has unsupported type for read: FILE", errors.get(0));
    }

    @Test
    public void testChainAssignment() {
        System.out.println("---- testChainAssignment ----");
        String input = """
        int i, j, k;
        i = j = k = 55;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testUndeclaredVariableInForInitIsReportedAsTypeError ----");
        List<String> errors = typeErrors("for (q = 0; true; ) ;\n");
        errors.forEach(System.out::println);

        assertEquals(1, errors.size());
        assertEquals("1: variable 'q' not declared.", errors.get(0));
    }

    @Test
    public void testUndeclaredVariableInForUpdateIsReportedAsTypeError() {
        System.out.println("---- testUndeclaredVariableInForUpdateIsReportedAsTypeError ----");
        List<String> errors = typeErrors("""
        int i;
        for (i = 0; true; q = 1) ;
        """);
        errors.forEach(System.out::println);

        assertEquals(1, errors.size());
        assertEquals("2: variable 'q' not declared.", errors.get(0));
    }

    @Test
    public void testIncompatibleTypeInForInitIsReportedAsTypeError() {
        System.out.println("---- testIncompatibleTypeInForInitIsReportedAsTypeError ----");
        List<String> errors = typeErrors("""
        int i;
        for (i = "text"; true; ) ;
        """);
        errors.forEach(System.out::println);

        assertEquals(1, errors.size());
        assertEquals("2,5: Incompatible types in for-init: INT and STRING", errors.get(0));
    }

    @Test
    public void testForInitPromotesIntToFloat() {
        System.out.println("---- testForInitPromotesIntToFloat ----");
        String input = """
        float f;
        for (f = 0; f < 2.0; f = f + 1) write f;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testForUpdatePromotesIntToFloat ----");
        String input = """
        float f;
        for (f = 0.0; f < 1.0; f = 2) write f;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testAssignmentInsideExpressionStoresAndYieldsValue ----");
        String input = """
        int a, b;
        b = (a = 5) + 1;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testAssignmentInsideWriteKeepsValueOnStack ----");
        String input = """
        int a;
        write (a = 5);
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testChainedAssignmentInForInit ----");
        String input = """
        int i, j;
        for (i = j = 0; i < 2; i = i + 1) write i;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testChainedAssignmentPromotesPerVariable ----");
        String input = """
        float f;
        int i;
        f = i = 5;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

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
        System.out.println("---- testFileAppendExpr ----");
        String input = """
        file f;
        f = "file_append_output.txt";
        f << "Hello, ";
        f << "World!";
        f << 1 << "A" << 2;
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

        List<String> expected = List.of(
                "push S \"file_append_output.txt\"", "push S a", "fopen", "save f",
                "load f", "push S \"Hello, \"", "fappend 1",
                "load f", "push S \"World!\"", "fappend 1",
                "load f", "push I 1", "push S \"A\"", "push I 2", "fappend 3"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }

    @Test
    public void testFileOpenAndAppend() {
        System.out.println("---- testFileOpenAndAppend ----");
        String input = """
        file f;
        f = open("output.txt", "a");
        f << "Line 1" << 42;
        f = open("output.txt", "w");
        f << "Overwrite";
        """;

        List<Instruction> instr = generate(input);
        instr.forEach(System.out::println);

        List<String> expected = List.of(

                // f = open("output.txt", "a")
                "push S output.txt", "push S a", "fopen", "save f",

                // f << "Line 1" << 42
                "load f", "push S \"Line 1\"", "push I 42", "fappend 2",

                // f = open("output.txt", "w")
                "push S output.txt", "push S w", "fopen", "save f",

                // f << "Overwrite"
                "load f", "push S \"Overwrite\"", "fappend 1"
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), instr.get(i).toString());
        }
    }


    @Test
    public void testWriteModeTruncatesAtOpenOnly() throws IOException {
        System.out.println("---- testWriteModeTruncatesAtOpenOnly ----");
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
        System.out.println("---- testAppendModeKeepsExistingContent ----");
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
        System.out.println("---- testOpeningAnotherFileLeavesTheFirstUntouched ----");
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
        System.out.println("---- testOpenCreatesTheFile ----");
        Path file = scratchFile("created.txt");

        run("""
            file f;
            f = open("%s", "w");
            """.formatted(file.toString().replace('\\', '/')));

        assertTrue(Files.exists(file));
        assertEquals(List.of(), Files.readAllLines(file));
    }

    @Test
    public void testFopenReadsExactlyTwoOperands() throws IOException {
        System.out.println("---- testFopenReadsExactlyTwoOperands ----");
        Path leftover = scratchFile("leftover.txt");
        Files.writeString(leftover, "must survive\n");
        Path opened = scratchFile("opened.txt");

        // A value left on the stack in front of the operands must not be mistaken
        // for the file name, which would truncate a file the program never opened.
        new StackMachine().execute(List.of(
                "push S " + leftover.toString().replace('\\', '/'),
                "push S " + opened.toString().replace('\\', '/'),
                "push S w",
                "fopen"));

        assertEquals(List.of("must survive"), Files.readAllLines(leftover));
        assertTrue(Files.exists(opened));
    }

    @Test
    public void testFileNameAssignmentAppends() throws IOException {
        System.out.println("---- testFileNameAssignmentAppends ----");
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
    public void testAllInputsAgainstReferenceOutputs() throws IOException {
        System.out.println("---- testAllInputsAgainstReferenceOutputs ----");

        for (int testNum = 1; testNum <= 3; testNum++) {
            System.out.println("Running test PLC_t" + testNum);

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

            if (actual.equals(expected)) {
                System.out.println("Output for PLC_t" + testNum + " matches expected output.");
            } else {
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
