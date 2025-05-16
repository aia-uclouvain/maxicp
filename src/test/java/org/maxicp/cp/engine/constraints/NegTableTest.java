/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.engine.constraints.TableTest.assertSameSearch;
import static org.maxicp.cp.engine.constraints.TableTest.randomTuples;
import static org.maxicp.search.Searches.firstFail;

public class NegTableTest extends CPSolverTest {

    /**
     * Given a set of invalid tuples, generates a set of valid ones
     * @param xs The 3 variables corresponding to the invalid tuples
     * @param negTable Invalid tuples: triplet of forbidden values for assigning variables
     * @return valid tuples generated form invalid ones
     */
    public static int[][] toPositive(CPIntVar[] xs, int[][] negTable) {
        if (xs.length != 3 || negTable[0].length != 3) {
            throw new IllegalArgumentException("Expecting 3 variables and sets of 3 variables");
        }
        ArrayList<int[]> posTableList = new ArrayList<>();
        CPIntVar x = xs[0];
        CPIntVar y = xs[1];
        CPIntVar z = xs[2];
        for (int i = x.min(); i <= x.max(); i++) {
            if (x.contains(i)) {
                for (int j = y.min(); j <= y.max(); j++) {
                    if (y.contains(j)) {
                        for (int k = z.min(); k <= z.max(); k++) {
                            if (z.contains(k)) {
                                boolean add = true;
                                for (int ind = 0; ind < negTable.length && add; ind++) {
                                    if (negTable[ind][0] == i && negTable[ind][1] == j && negTable[ind][2] == k) {
                                        add = false;
                                    }
                                }
                                if (add) posTableList.add(new int[]{i, j, k});
                            }
                        }
                    }
                }
            }
        }
        return posTableList.toArray(new int[0][]);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest0(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 2);
        int[][] table = new int[][]{
                {0, 0, 0},
                {1, 0, 0},
                {1, 1, 0},
                {0, 1, 0},
                {0, 1, 1},
                {1, 0, 1},
                {0, 0, 1}};
        cp.post(new NegTableCT(x, table));
        assertEquals(1, x[0].min());
        assertEquals(1, x[1].min());
        assertEquals(1, x[2].min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest1(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 2);
        int[][] table = new int[][]{{1, 1, 1}};
        cp.post(new NegTableCT(x, table));
        DFSearch dfs = CPFactory.makeDfs(cp, firstFail(x));
        SearchStatistics stats = dfs.solve();
        assertEquals(7, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest2(CPSolver cp) {
        assertThrowsExactly(AssertionError.class, () -> {
                    CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 2);
                    int[][] table = new int[][]{{1, 1, 1}, {1, 1, 1}, {1, 1, 1}};
                    cp.post(new NegTableCT(x, table));
                    DFSearch dfs = CPFactory.makeDfs(cp, firstFail(x));
                    SearchStatistics stats = dfs.solve();
                });
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInitializeWrongTable(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 2);
        x[0].fix(0);
        x[1].fix(1);
        x[2].fix(1);
        int[][] table = new int[][]{{0, 1, 1}}; // assignment is forbidden
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new NegTableCT(x, table)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTwoVariablesFixedBetweenCalls(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 2);
        int[][] table = new int[][]{
                {0, 0, 0},
                {1, 1, 1}};
        cp.post(new NegTableCT(x, table));
        x[0].fix(0);
        x[1].fix(0);
        x[2].fix(0);
        assertThrowsExactly(InconsistencyException.class, cp::fixPoint);
    }

    public static Stream<Arguments> getRandomTables(boolean noDuplicates) {
        Random rand = new Random(67292);
        return getRepeatedSolverSuppliers(10)
                .map(supplier -> Arguments.of(supplier,
                        randomTuples(rand, 3, 50, 2, 8, noDuplicates),
                        randomTuples(rand, 3, 50, 1, 3, noDuplicates),
                        randomTuples(rand, 3, 80, 0, 6, noDuplicates)));
    }

    public static Stream<Arguments> getRandomTablesWithDuplicates() {
        return getRandomTables(false);
    }

    public static Stream<Arguments> getRandomTablesWithoutDuplicates() {
        return getRandomTables(true);
    }

    @ParameterizedTest(name = "CPSolver, 3 Sets of Tuples, no duplicates")
    @MethodSource({"getRandomTablesWithoutDuplicates"})
    public void testSameSearchAsTableCTnoDup(Supplier<CPSolver> cpSupplier,
                                         int[][] t1, int[][] t2, int[][] t3) {
        assertSameSearch(cpSupplier, (x, t) -> new TableCT(x, toPositive(x, t)), NegTableCT::new, t1, t2, t3);
    }

}
