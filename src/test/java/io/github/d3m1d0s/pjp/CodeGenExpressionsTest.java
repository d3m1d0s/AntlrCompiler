package io.github.d3m1d0s.pjp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// instruction listings generated for expressions: arithmetic, comparison,
// logic, assignment and precedence
public class CodeGenExpressionsTest extends CompilerTestSupport {

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

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push I 0", "save result",

                "push I 2", "save a", "load a", "pop",
                "push I 3", "save b", "load b", "pop",
                "load a", "load b", "add I", "save result", "load result", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push I 0", "save a",
                "push F 0.0", "save b",
                "push F 0.0", "save result",

                "push I 10", "save a", "load a", "pop",
                "push F 2.5", "save b", "load b", "pop",
                "load a", "itof", "load b", "sub F", "save result", "load result", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push I 0", "save result",

                "push I 3", "save a", "load a", "pop",
                "push I 4", "save b", "load b", "pop",
                "load a", "load b", "mul I", "save result", "load result", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push I 0", "save result",

                "push I 7", "save a", "load a", "pop",
                "push I 3", "save b", "load b", "pop",
                "load a", "load b", "mod", "save result", "load result", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push S \"\"", "save s1",
                "push S \"\"", "save s2",
                "push S \"\"", "save result",

                "push S \"A\"", "save s1", "load s1", "pop",
                "push S \"B\"", "save s2", "load s2", "pop",
                "load s1", "load s2", "concat", "save result", "load result", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push I 0", "save a",
                "push F 0.0", "save b",
                "push B false", "save result",

                "push I 3", "save a", "load a", "pop",
                "push F 3.0", "save b", "load b", "pop",

                "load a", "itof", "load b", "eq F", "save result", "load result", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push S \"\"", "save a",
                "push S \"\"", "save b",
                "push B false", "save result",

                "push S \"foo\"", "save a", "load a", "pop",
                "push S \"bar\"", "save b", "load b", "pop",

                "load a", "load b", "eq S", "save result", "load result", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testEqualBools() {
        String input = """
        bool a;
        bool b;
        bool r;
        r = a == b;
        """;

        assertEquals(List.of(
                "push B false", "save a",
                "push B false", "save b",
                "push B false", "save r",

                "load a", "load b", "eq B", "save r", "load r", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testNotEqualBools() {
        String input = """
        bool a;
        bool r;
        r = a != true;
        """;

        assertEquals(List.of(
                "push B false", "save a",
                "push B false", "save r",

                "load a", "push B true", "eq B", "not", "save r", "load r", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testNotEqualFloats() {
        String input = """
        bool r;
        r = 1.5 != 2.5;
        """;

        assertEquals(List.of(
                "push B false", "save r",
                "push F 1.5", "push F 2.5", "eq F", "not", "save r", "load r", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testNotEqualMixedIntAndFloat() {
        assertEquals(List.of(
                "push I 1", "itof", "push F 2.0", "eq F", "not", "print 1"
        ), lines(generate("write 1 != 2.0;\n")));
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

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push B false", "save result",

                "push I 1", "save a", "load a", "pop",
                "push I 2", "save b", "load b", "pop",

                "load a", "load b", "lt I", "save result", "load result", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push I 0", "save a",
                "push F 0.0", "save b",
                "push B false", "save result",

                "push I 4", "save a", "load a", "pop",
                "push F 2.5", "save b", "load b", "pop",

                "load a", "itof", "load b", "gt F", "save result", "load result", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testLessOrEqualInt() {
        assertEquals(List.of(
                "push I 1", "push I 2", "le I", "print 1"
        ), lines(generate("write 1 <= 2;\n")));
    }

    @Test
    public void testGreaterOrEqualWithFloatPromotion() {
        assertEquals(List.of(
                "push I 2", "itof", "push F 1.5", "ge F", "print 1"
        ), lines(generate("write 2 >= 1.5;\n")));
    }

    @Test
    public void testNotBool() {
        String input = """
        bool a;
        bool result;
        a = false;
        result = !a;
        """;

        assertEquals(List.of(
                "push B false", "save a",
                "push B false", "save result",

                "push B false", "save a", "load a", "pop",

                "load a", "not", "save result", "load result", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push B false", "save a",
                "push B false", "save b",
                "push B false", "save result",

                "push B true", "save a", "load a", "pop",
                "push B false", "save b", "load b", "pop",

                "load a", "load b", "and", "save result", "load result", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push B false", "save a",
                "push B false", "save b",
                "push B false", "save result",

                "push B false", "save a", "load a", "pop",
                "push B true", "save b", "load b", "pop",

                "load a", "load b", "or", "save result", "load result", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testUnaryMinusAsBinaryOperand() {
        assertEquals(List.of(
                "push I 2", "push I 3", "uminus I", "mul I", "print 1"
        ), lines(generate("write 2 * -3;\n")));
    }

    @Test
    public void testUnaryMinusOperandWithFloatPromotion() {
        assertEquals(List.of(
                "push F 1.5", "push I 1", "uminus I", "itof", "add F", "print 1"
        ), lines(generate("write 1.5 + -1;\n")));
    }

    @Test
    public void testUnaryMinusInAssignment() {
        String input = """
        int a;
        a = 1 + -1;
        """;

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 1", "push I 1", "uminus I", "add I", "save a", "load a", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testUnaryMinusBindsTighterThanAddition() {
        assertEquals(List.of(
                "push I 2", "uminus I", "push I 3", "add I", "print 1"
        ), lines(generate("write -2 + 3;\n")));
    }

    @Test
    public void testUnaryMinusBindsTighterThanComparison() {
        assertEquals(List.of(
                "push I 2", "uminus I", "push I 1", "lt I", "print 1"
        ), lines(generate("write -2 < 1;\n")));
    }

    @Test
    public void testNotBindsTighterThanAnd() {
        String input = """
        bool r;
        r = !false && false;
        """;

        assertEquals(List.of(
                "push B false", "save r",
                "push B false", "not", "push B false", "and", "save r", "load r", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testAssignmentInsideExpressionStoresAndYieldsValue() {
        String input = """
        int a, b;
        b = (a = 5) + 1;
        """;

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 0", "save b",
                "push I 5", "save a", "load a",
                "push I 1", "add I",
                "save b", "load b", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testAssignmentInsideWriteKeepsValueOnStack() {
        String input = """
        int a;
        write (a = 5);
        """;

        assertEquals(List.of(
                "push I 0", "save a",
                "push I 5", "save a", "load a", "print 1"
        ), lines(generate(input)));
    }

    @Test
    public void testChainAssignment() {
        String input = """
        int i, j, k;
        i = j = k = 55;
        """;

        assertEquals(List.of(
                "push I 0", "save i",
                "push I 0", "save j",
                "push I 0", "save k",
                "push I 55", "save k",
                "load k", "save j",
                "load j", "save i", "load i", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testChainedAssignmentPromotesPerVariable() {
        String input = """
        float f;
        int i;
        f = i = 5;
        """;

        assertEquals(List.of(
                "push F 0.0", "save f",
                "push I 0", "save i",
                "push I 5", "save i", "load i", "itof", "save f", "load f", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testIntLiteralAtRangeBoundaryCompiles() {
        assertEquals(List.of(
                "push I 2147483647", "print 1"
        ), lines(generate("write 2147483647;\n")));
    }

    @Test
    public void testStringOperandSerializationStaysSingleLine() {
        assertEquals(List.of(
                "push S \"a\\nb\"", "print 1"
        ), lines(generate("write \"a\\nb\";\n")));
    }
}
