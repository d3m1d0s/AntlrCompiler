package io.github.d3m1d0s.pjp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// instruction listings generated for the file extension: open and <<
public class CodeGenFilesTest extends CompilerTestSupport {

    @Test
    public void testFileNameAssignmentAndAppendChains() {
        String input = """
        file f;
        f = "file_append_output.txt";
        f << "Hello, ";
        f << "World!";
        f << 1 << "A" << 2;
        """;

        assertEquals(List.of(
                "push S \"file_append_output.txt\"", "push S \"a\"", "fopen", "save f", "load f", "pop",
                "load f", "push S \"Hello, \"", "fappend 1", "pop",
                "load f", "push S \"World!\"", "fappend 1", "pop",
                "load f", "push I 1", "push S \"A\"", "push I 2", "fappend 3", "pop"
        ), lines(generate(input)));
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

        assertEquals(List.of(
                "push S \"output.txt\"", "push S \"a\"", "fopen", "save f", "load f", "pop",

                "load f", "push S \"Line 1\"", "push I 42", "fappend 2", "pop",

                "push S \"output.txt\"", "push S \"w\"", "fopen", "save f", "load f", "pop",

                "load f", "push S \"Overwrite\"", "fappend 1", "pop"
        ), lines(generate(input)));
    }

    @Test
    public void testOpenOutsideAssignmentYieldsHandle() {
        assertEquals(List.of(
                "push S \"target/file-tests/inline.txt\"", "push S \"w\"", "fopen", "print 1"
        ), lines(generate("write open(\"target/file-tests/inline.txt\", \"w\");\n")));
    }
}
