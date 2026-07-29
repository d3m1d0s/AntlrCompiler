package io.github.d3m1d0s.pjp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// the command line contract: exit codes, stream separation, usage and stdin
public class AppDriverTest extends CompilerTestSupport {

    @Test
    public void testCompilesAndRunsAProgram() throws IOException {
        Path src = sourceFile("cli-ok.lang", "write 1 + 1;\n");
        AppResult r = runApp("", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals("2", r.stdout().trim());
        assertEquals("", r.stderr());
    }

    @Test
    public void testReadsFromStdin() throws IOException {
        Path src = sourceFile("cli-read.lang", "int a;\nread a;\nwrite a * 2;\n");
        AppResult r = runApp("21\n", src.toString());

        assertEquals(0, r.exitCode());
        assertEquals("42", r.stdout().trim());
    }

    @Test
    public void testWithoutArgumentsPrintsUsage() {
        AppResult r = runApp("");

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("Usage"));
    }

    @Test
    public void testReportsUnreadableFile() {
        AppResult r = runApp("", "no-such-file.lang");

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("Cannot read file: no-such-file.lang"));
    }

    @Test
    public void testExitsWithOneOnSyntaxErrors() throws IOException {
        Path src = sourceFile("cli-syntax.lang", "int a\n");
        AppResult r = runApp("", src.toString());

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("Aborted due to syntax errors."));
        assertEquals("", r.stdout());
    }

    @Test
    public void testExitsWithOneOnTypeErrors() throws IOException {
        Path src = sourceFile("cli-type.lang", "write x;\n");
        AppResult r = runApp("", src.toString());

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("Variable 'x' is not declared."));
        assertTrue(r.stderr().contains("Aborted due to type errors."));
        assertEquals("", r.stdout());
    }

    @Test
    public void testExitsWithTwoOnRuntimeErrors() throws IOException {
        Path src = sourceFile("cli-runtime.lang", "write 1 / 0;\n");
        AppResult r = runApp("", src.toString());

        assertEquals(2, r.exitCode());
        assertTrue(r.stderr().contains("Runtime error: Division by zero"));
    }
}
