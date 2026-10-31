package org.maxicp.modeling.xcsp3;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.search.blackbox.ModelingBlackBox;
import org.maxicp.search.blackbox.SearchStatus;
import org.xcsp.parser.callbacks.SolutionChecker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class COPTest {

    /**
     * Instances for which optimality is proven rapidly within the unit test budget.
     */
    private static final Map<String, Integer> EXPECTED_OPTIMUM_BY_FILE = Map.of(
            "BinPacking2-n1c1w1a_c24.xml", 25,
            "Drinking-000050_c24.xml", 5,
            "MaximumDensityOscillatingLife-5-2_c24.xml", 28,
            "Pyramid-07-500_c24.xml", 212,
            "SameQueensKnights-b-05_c24.xml", 6,
            "StillLife-wastage-05-05_c24.xml", 16
    );

    public static Stream<String> data() throws IOException {
        Path base = Paths.get("src/test/resources/XCSP3/COP");
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
            String base = Paths.get(filename).getFileName().toString();
            assertNotNull(instance.objective(), "Missing objective in loaded COP instance: " + base);

            ModelingBlackBox.Result outcome = ModelingBlackBox.optimize(
                    instance.md(),
                    instance.objective(),
                    1);
            assertTrue(outcome.solution().isPresent(), "No solution found for " + filename);
            String solXml = instance.formatSolution(outcome);
            assertValidSolution(filename, solXml);

            Integer expectedOpt = EXPECTED_OPTIMUM_BY_FILE.get(base);
            if (expectedOpt != null && outcome.status() == SearchStatus.PROVEN_OPTIMAL) {
                assertEquals(expectedOpt.intValue(), outcome.objectiveValue().orElseThrow(),
                        "Wrong optimal objective for " + base);
            }
        }
    }
}
