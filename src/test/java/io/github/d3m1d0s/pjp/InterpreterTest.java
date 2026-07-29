package io.github.d3m1d0s.pjp;

import io.github.d3m1d0s.pjp.runtime.MachineException;
import io.github.d3m1d0s.pjp.runtime.StackMachine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// how programs behave when they actually run: file semantics, read,
// machine errors and end-to-end language behavior
public class InterpreterTest extends CompilerTestSupport {

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
        // strict about vocabulary but not case: "yes" is rejected, "TRUE" parses
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
        // unlike open(""), f = "" only surfaces at run time, and must fail as an open failure, not a crash
        MachineException e = assertThrows(MachineException.class, () -> run("""
            file f;
            f = "";
            """));
        assertTrue(e.getMessage().startsWith("Failed to open file:"),
                "unexpected message: " + e.getMessage());
        assertNotNull(e.getCause(), "I/O failures must keep their cause");
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
    public void testFopenReadsExactlyTwoOperands() throws IOException {
        Path leftover = scratchFile("leftover.txt");
        Files.writeString(leftover, "must survive\n");
        Path opened = scratchFile("opened.txt");

        // a value left on the stack must not be mistaken for the file name and truncate the wrong file
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
    public void testEscapeSequencesAreDecoded() throws IOException {
        Path src = sourceFile("cli-escapes.lang", "write \"a\\nb\\t\\\"q\\\"\\\\\";\n");
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals("a\nb\t\"q\"\\", r.stdout().trim());
    }

    @Test
    public void testFloatInequalityRunsEndToEnd() throws IOException {
        Path src = sourceFile("cli-floatneq.lang", """
            write 1.5 != 2.5;
            write 1.5 != 1.5;
            write 1 != 2.0;
            write 2.0 != 2;
            """);
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals(List.of("true", "false", "true", "false"), r.stdout().lines().toList());
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
    public void testOrderingOperatorsRunEndToEnd() throws IOException {
        Path src = sourceFile("cli-ordering.lang", """
            write 1 <= 1;
            write 2 <= 1;
            write 1 >= 1;
            write 1 >= 2;
            write 1.5 <= 1.5;
            """);
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals(List.of("true", "false", "true", "false", "true"), r.stdout().lines().toList());
    }

    @Test
    public void testShadowingKeepsValuesSeparate() throws IOException {
        Path src = sourceFile("cli-shadow.lang", """
            int x;
            x = 1;
            {
                int x;
                x = 2;
                write x;
            }
            write x;
            """);
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals(List.of("2", "1"), r.stdout().lines().toList());
    }

    @Test
    public void testLoopBodyDeclarationIsFreshEachIteration() throws IOException {
        Path src = sourceFile("cli-loopvar.lang", """
            int i;
            i = 0;
            while (i < 2) {
                int acc;
                acc = acc + 10;
                write acc;
                i = i + 1;
            }
            """);
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals(List.of("10", "10"), r.stdout().lines().toList());
    }

    @Test
    public void testFloatValuesHaveDoublePrecision() throws IOException {
        Path src = sourceFile("cli-double.lang", """
            write 1.0 / 3.0;
            write 123456789.5;
            write 3.141592;
            float x;
            // 16777217 is 2^24 + 1, the first integer a 32-bit float cannot hold
            x = 16777217.0;
            write x;
            """);
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals(List.of(
                "0.3333333333333333",
                "1.234567895E8",
                "3.141592",
                "1.6777217E7"
        ), r.stdout().lines().toList());
    }
}
