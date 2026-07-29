package io.github.d3m1d0s.pjp;

import io.github.d3m1d0s.pjp.codegen.Instruction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

// the course reference listings pin the instruction text line for line
public class ReferenceOutputsTest extends CompilerTestSupport {

    @Test
    public void testAllInputsAgainstReferenceOutputs() throws IOException {
        for (int testNum = 1; testNum <= 3; testNum++) {
            Path inputPath = Path.of("src/test/resources/PLC_t" + testNum + ".in");
            Path expectedOutputPath = Path.of("src/test/resources/PLC_t" + testNum + ".out");

            String source = Files.readString(inputPath);
            List<Instruction> instructions = generate(source);

            // the listing goes through a file under target/ so failures leave an inspectable artifact
            Path outPath = scratchFile("output_t" + testNum + ".out");
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
                        System.err.printf("Line %d:%n  Expected: %s%n  Actual:   %s%n", i + 1, exp, act);
                    }
                }

                fail("Generated listing does not match PLC_t" + testNum + ".out");
            }
        }
    }
}
