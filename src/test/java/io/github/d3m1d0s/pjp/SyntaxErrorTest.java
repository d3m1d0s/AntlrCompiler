package io.github.d3m1d0s.pjp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// lexer and parser error counting through the shared listener
public class SyntaxErrorTest extends CompilerTestSupport {

    @Test
    public void testSyntaxErrorIsCountedAndDoesNotKillTheProcess() {
        // the listener must count and report, not System.exit on the first error
        assertTrue(syntaxErrors("int a\n") > 0);
        assertEquals(0, syntaxErrors("int a;\n"));
    }

    @Test
    public void testLexerErrorsAreCounted() {
        // each untokenizable character counts as its own syntax error
        assertEquals(2, syntaxErrors("@ $\n"));
    }

    @Test
    public void testMultipleSyntaxErrorsAreReported() {
        assertTrue(syntaxErrors("int a\nint b\n") >= 2);
    }

    @Test
    public void testRawLineBreakInStringIsSyntaxError() {
        assertTrue(syntaxErrors("write \"a\nb\";\n") > 0);
    }
}
