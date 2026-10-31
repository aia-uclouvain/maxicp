package org.maxicp.modeling.xcsp3;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.search.blackbox.ModelingBlackBox;
import org.xcsp.parser.callbacks.SolutionChecker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CSPTest {

    public static Stream<String> data() throws IOException {
        Path base = Paths.get("src/test/resources/XCSP3/CSP");
        return Files.walk(base)
                .filter(Files::isRegularFile)
                .filter(x -> x.toString().endsWith(".xml"))
                .map(Path::toString)
                .sorted();
    }

    private static void assertValidSolution(String path, String sol) {
        PrintStream prevOut = System.out;
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            SolutionChecker sc = new SolutionChecker(false, path, new ByteArrayInputStream(sol.getBytes()));
            assertEquals(0, sc.invalidObjs.size(), "Invalid objectives in " + path);
            assertEquals(0, sc.violatedCtrs.size(), "Violated constraints in " + path + ": " + sc.violatedCtrs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(prevOut);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void checkSol(String filename) throws Exception {
        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(filename)) {
            ModelingBlackBox.Result outcome = ModelingBlackBox.solve(
                    instance.md(),
                    5);
            assertTrue(outcome.solution().isPresent(), "No solution found for " + filename);
            String solXml = instance.formatSolution(outcome);
            assertValidSolution(filename, solXml);
        }
    }
}
