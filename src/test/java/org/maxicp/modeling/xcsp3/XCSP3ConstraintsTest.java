package org.maxicp.modeling.xcsp3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.maxicp.search.blackbox.ModelingBlackBox;
import org.xcsp.parser.callbacks.SolutionChecker;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XCSP3ConstraintsTest {

    @TempDir
    Path tempDir;

    private void solveAndVerifyXml(String name, String xml) throws Exception {
        Path xmlFile = tempDir.resolve(name + ".xml");
        Files.writeString(xmlFile, xml);
        String filename = xmlFile.toString();
        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(filename)) {
            ModelingBlackBox.Result outcome = instance.objective() != null
                    ? ModelingBlackBox.optimize(instance.md(), instance.decisionVars(), instance.objective(), 2)
                    : ModelingBlackBox.solve(instance.md(), instance.decisionVars(), 2);
            assertTrue(outcome.solution().isPresent(), "Expected solution for " + name);
            String solXml = instance.formatSolution(outcome.solution().get());
            PrintStream prevOut = System.out;
            final SolutionChecker sc;
            try {
                System.setOut(new PrintStream(OutputStream.nullOutputStream()));
                sc = new SolutionChecker(false, filename, new ByteArrayInputStream(solXml.getBytes()));
            } finally {
                System.setOut(prevOut);
            }
            assertEquals(0, sc.invalidObjs.size(), "Invalid objectives in " + name + ": " + sc.invalidObjs);
            assertEquals(0, sc.violatedCtrs.size(), "Violated constraints in " + name + ": " + sc.violatedCtrs);
        }
    }

    @Test
    public void testAllDifferentExceptAndAmong() throws Exception {
        String xml = """
                <instance format="XCSP3" type="CSP">
                  <variables>
                    <array id="x" size="[5]"> 0..3 </array>
                  </variables>
                  <constraints>
                    <allDifferent>
                      <list> x[] </list>
                      <except> 0 </except>
                    </allDifferent>
                    <count>
                      <list> x[] </list>
                      <values> 1 2 </values>
                      <condition> (eq,2) </condition>
                    </count>
                    <count>
                      <list> x[] </list>
                      <values> 0 </values>
                      <condition> (eq,2) </condition>
                    </count>
                  </constraints>
                </instance>
                """;
        solveAndVerifyXml("allDiffExcept_among", xml);
    }

    @Test
    public void testNValuesAndPrecedence() throws Exception {
        String xml = """
                <instance format="XCSP3" type="CSP">
                  <variables>
                    <array id="x" size="[5]"> 1..4 </array>
                  </variables>
                  <constraints>
                    <nValues>
                      <list> x[] </list>
                      <condition> (eq,3) </condition>
                    </nValues>
                    <precedence>
                      <list> x[] </list>
                      <values> 1 2 3 </values>
                    </precedence>
                  </constraints>
                </instance>
                """;
        solveAndVerifyXml("nValues_precedence", xml);
    }

    @Test
    public void testMDDAndStretch() throws Exception {
        String xml = """
                <instance format="XCSP3" type="CSP">
                  <variables>
                    <array id="x" size="[4]"> 0 1 </array>
                    <array id="y" size="[5]"> 0 1 </array>
                  </variables>
                  <constraints>
                    <mdd>
                      <list> x[] </list>
                      <transitions>
                        (r,0,n1)(r,1,n2)
                        (n1,1,n3)(n2,0,n3)
                        (n3,1,n4)
                        (n4,0,t)
                      </transitions>
                    </mdd>
                    <stretch>
                      <list> y[] </list>
                      <values> 0 1 </values>
                      <widths> 2..3 2..3 </widths>
                    </stretch>
                  </constraints>
                </instance>
                """;
        solveAndVerifyXml("mdd_stretch", xml);
    }

    @Test
    public void testElement2DAndKnapsack() throws Exception {
        String xml = """
                <instance format="XCSP3" type="COP">
                  <variables>
                    <array id="x" size="[3]"> 0..2 </array>
                    <var id="r"> 0..1 </var>
                    <var id="c"> 0..1 </var>
                    <var id="v"> 1..10 </var>
                  </variables>
                  <constraints>
                    <element>
                      <matrix> (2,5)(7,9) </matrix>
                      <index> r c </index>
                      <value> v </value>
                    </element>
                    <knapsack>
                      <list> x[] </list>
                      <weights> 2 3 4 </weights>
                      <condition> (le,10) </condition>
                      <profits> 5 8 10 </profits>
                      <condition> (ge,15) </condition>
                    </knapsack>
                  </constraints>
                  <objectives>
                    <maximize type="expression"> add(v,x[0]) </maximize>
                  </objectives>
                </instance>
                """;
        solveAndVerifyXml("element2d_knapsack", xml);
    }

    @Test
    public void testCircuitWithSizeAndNoOverlap2D() throws Exception {
        String xml = """
                <instance format="XCSP3" type="CSP">
                  <variables>
                    <array id="succ" size="[4]"> 0..3 </array>
                    <var id="sz"> 3..4 </var>
                    <array id="px" size="[2]"> 0..5 </array>
                    <array id="py" size="[2]"> 0..5 </array>
                  </variables>
                  <constraints>
                    <circuit>
                      <list> succ[] </list>
                      <size> sz </size>
                    </circuit>
                    <noOverlap>
                      <origins> (px[0],py[0])(px[1],py[1]) </origins>
                      <lengths> (3,3)(3,3) </lengths>
                    </noOverlap>
                  </constraints>
                </instance>
                """;
        solveAndVerifyXml("circuit_nooverlap2d", xml);
    }

    @Test
    public void testMaximumArgMinimumArgAndClause() throws Exception {
        String xml = """
                <instance format="XCSP3" type="CSP">
                  <variables>
                    <array id="x" size="[4]"> 1..6 </array>
                    <var id="idxMax"> 0..3 </var>
                    <var id="idxMin"> 0..3 </var>
                    <array id="b" size="[3]"> 0 1 </array>
                  </variables>
                  <constraints>
                    <allDifferent> x[] </allDifferent>
                    <maximumArg rank="first">
                      <list> x[] </list>
                      <condition> (eq,idxMax) </condition>
                    </maximumArg>
                    <minimumArg rank="first">
                      <list> x[] </list>
                      <condition> (eq,idxMin) </condition>
                    </minimumArg>
                    <clause>
                      <list> b[0] not(b[1]) b[2] </list>
                    </clause>
                  </constraints>
                </instance>
                """;
        solveAndVerifyXml("maxArg_minArg_clause", xml);
    }

    @Test
    public void testCumulativeVariableLengthsHeightsAndCapacity() throws Exception {
        String xml = """
                <instance format="XCSP3" type="COP">
                  <variables>
                    <array id="s" size="[3]"> 0..5 </array>
                    <array id="l" size="[3]"> 2..3 </array>
                    <array id="e" size="[3]"> 2..8 </array>
                    <array id="h" size="[3]"> 2..4 </array>
                    <var id="cap"> 3..5 </var>
                  </variables>
                  <constraints>
                    <cumulative>
                      <origins> s[] </origins>
                      <lengths> l[] </lengths>
                      <ends> e[] </ends>
                      <heights> h[] </heights>
                      <condition> (le,cap) </condition>
                    </cumulative>
                  </constraints>
                  <objectives>
                    <minimize type="maximum"> e[] </minimize>
                  </objectives>
                </instance>
                """;
        solveAndVerifyXml("cumulative_var_all", xml);
    }
}
