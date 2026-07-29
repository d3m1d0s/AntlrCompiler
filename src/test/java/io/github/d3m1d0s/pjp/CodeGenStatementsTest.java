package io.github.d3m1d0s.pjp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// instruction listings generated for statements: declarations, read, write,
// control flow and scoping
public class CodeGenStatementsTest extends CompilerTestSupport {

    @Test
    public void testDeclarationsEmitDefaults() {
        String input = """
        int n;
        float f;
        bool b;
        string s;
        """;

        assertEquals(List.of(
                "push I 0", "save n",
                "push F 0.0", "save f",
                "push B false", "save b",
                "push S \"\"", "save s"
        ), lines(generate(input)));
    }

    @Test
    public void testIntDeclarationAndAssignment() {
        String input = """
        int a;
        a = 42;
        """;

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 42", "save a", "load a", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testFloatPromotionOnAssignment() {
        String input = """
        float c;
        c = 10;
        """;

        assertEquals(List.of(
                "push F 0.0", "save c",
                "push I 10", "itof", "save c", "load c", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testStringDeclarationAndAssignment() {
        String input = """
        string msg;
        msg = "hello";
        """;

        assertEquals(List.of(
                "push S \"\"", "save msg",
                "push S \"hello\"", "save msg", "load msg", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testBoolAssignment() {
        String input = """
        bool m;
        m = true;
        """;

        assertEquals(List.of(
                "push B false", "save m",
                "push B true", "save m", "load m", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testIdentifierLoad() {
        String input = """
        int x;
        int y;
        x = 5;
        y = x;
        """;

        assertEquals(List.of(
                "push I 0", "save x",
                "push I 0", "save y",
                "push I 5", "save x", "load x", "pop",
                "load x", "save y", "load y", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
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
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push I 0", "save a",
                "push F 0.0", "save b",
                "push B false", "save c",
                "push S \"\"", "save d",

                "read I", "save a",
                "read F", "save b",
                "read B", "save c",
                "read S", "save d"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push B false", "save cond",
                "push I 0", "save a",

                "push B true", "save cond", "load cond", "pop",

                "load cond", "fjmp 0",
                "push I 42", "save a", "load a", "pop", "jmp 1",
                "label 0", "label 1"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 0", "save a", "load a", "pop",

                "label 0",
                "load a", "push I 3", "lt I", "fjmp 1",
                "load a", "push I 1", "add I", "save a", "load a", "pop",
                "jmp 0",
                "label 1"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 0", "save a", "load a", "pop",
                "push I 0", "save a",

                "label 0",
                "load a", "push I 3", "lt I", "fjmp 1",

                "load a", "print 1",

                "load a", "push I 1", "add I", "save a",
                "jmp 0",
                "label 1"
        ), lines(generate(input)));
    }

    @Test
    public void testForInitPromotesIntToFloat() {
        String input = """
        float f;
        for (f = 0; f < 2.0; f = f + 1) write f;
        """;

        assertEquals(List.of(
                "push F 0.0", "save f",
                "push I 0", "itof", "save f",
                "label 0",
                "load f", "push F 2.0", "lt F", "fjmp 1",
                "load f", "print 1",
                "load f", "push I 1", "itof", "add F", "save f",
                "jmp 0",
                "label 1"
        ), lines(generate(input)));
    }

    @Test
    public void testForUpdatePromotesIntToFloat() {
        String input = """
        float f;
        for (f = 0.0; f < 1.0; f = 2) write f;
        """;

        assertEquals(List.of(
                "push F 0.0", "save f",
                "push F 0.0", "save f",
                "label 0",
                "load f", "push F 1.0", "lt F", "fjmp 1",
                "load f", "print 1",
                "push I 2", "itof", "save f",
                "jmp 0",
                "label 1"
        ), lines(generate(input)));
    }

    @Test
    public void testChainedAssignmentInForInit() {
        String input = """
        int i, j;
        for (i = j = 0; i < 2; i = i + 1) write i;
        """;

        assertEquals(List.of(
                "push I 0", "save i",
                "push I 0", "save j",
                "push I 0", "save j", "load j", "save i",
                "label 0",
                "load i", "push I 2", "lt I", "fjmp 1",
                "load i", "print 1",
                "load i", "push I 1", "add I", "save i",
                "jmp 0",
                "label 1"
        ), lines(generate(input)));
    }

    @Test
    public void testShadowedVariablesGetDistinctRuntimeNames() {
        String input = """
        int x;
        { int x; }
        """;

        assertEquals(List.of(
                "push I 0", "save x",
                "push I 0", "save x.1"
        ), lines(generate(input)));
    }
}
