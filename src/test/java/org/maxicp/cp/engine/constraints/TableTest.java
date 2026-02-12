/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.cp.CPFactory;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.firstFailBinary;

public class TableTest extends CPSolverTest {

    private static class TableDecomp extends AbstractCPConstraint {
        private final CPIntVar[] x;
        private final int[][] table;

        /**
         * Decomposition of a table constraint.
         * <p>The table constraint ensures that
         * {@code x} is a row from the given table.
         * More exactly, there exist some row <i>i</i>
         * such that
         * {@code x[0]==table[i][0], x[1]==table[i][1], etc}.
         * <p>This constraint is sometimes called <i>in extension</i> constraint
         * as the user enumerates the set of solutions that can be taken
         * by the variables.
         *
         * @param x  the non empty set of variables to constraint
         * @param table the possible set of solutions for x.
         *              The second dimension must be of the same size as the array x.
         */
        public TableDecomp(CPIntVar[] x, int[][] table) {
            super(x[0].getSolver());
            this.x = x;
            this.table = table;
        }

        @Override
        public void post() {
            for (CPIntVar var : x)
                var.propagateOnDomainChange(this);
            propagate();
        }

        @Override
        public void propagate() {
            for (int i = 0; i < x.length; i++) {
                for (int v = x[i].min(); v <= x[i].max(); v++) {
                    if (x[i].contains(v)) {
                        boolean valueIsSupported = false;
                        for (int tupleIdx = 0; tupleIdx < table.length && !valueIsSupported; tupleIdx++) {
                            if (table[tupleIdx][i] == v) {
                                boolean allValueVariableSupported = true;
                                for (int j = 0; j < x.length && allValueVariableSupported; j++) {
                                    if (!x[j].contains(table[tupleIdx][j])) {
                                        allValueVariableSupported = false;
                                    }
                                }
                                valueIsSupported = allValueVariableSupported;
                            }
                        }
                        if (!valueIsSupported)
                            x[i].remove(v);
                    }
                }
            }
        }
    }


    public static int[][] randomTuples(Random rand, int arity, int nTuples, int minvalue, int maxvalue) {
        return randomTuples(rand, arity, nTuples, minvalue, maxvalue, false);
    }

    public static int[][] randomTuples(Random rand, int arity, int nTuples, int minvalue, int maxvalue, boolean noDuplicates) {
        int[][] r = new int[nTuples][arity];
        for (int i = 0; i < nTuples; i++)
            for (int j = 0; j < arity; j++)
                r[i][j] = rand.nextInt(maxvalue - minvalue) + minvalue;
        return noDuplicates ? removeDuplicates(r) : r;
    }

    public static int[][] removeDuplicates(int[][] table) {
        ArrayList<int[]> tableList = new ArrayList<>();
        boolean[] duplicate = new boolean[table.length];
        for (int i = 0; i < table.length; i++) {
            if (!duplicate[i]) {
                tableList.add(table[i]);
                for (int j = i + 1; j < table.length; j++) {
                    if (i != j & !duplicate[j]) {
                        boolean same = true;
                        for (int k = 0; k < table[i].length; k++) {
                            same &= table[i][k] == table[j][k];
                        }
                        if (same) {
                            duplicate[j] = true;
                        }
                    }
                }
            }
        }
        return tableList.toArray(new int[0][]);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest0(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 2, 1);
        int[][] table = new int[][]{{0, 0}};
        assertDoesNotThrow(() -> cp.post(new TableCT(x, table)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest1(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 12);
        int[][] table = new int[][]{{0, 0, 2},
                {3, 5, 7},
                {6, 9, 10},
                {1, 2, 3}};
        cp.post(new TableCT(x, table));

        assertEquals(4, x[0].size());
        assertEquals(4, x[1].size());
        assertEquals(4, x[2].size());

        assertEquals(0, x[0].min());
        assertEquals(6, x[0].max());
        assertEquals(0, x[1].min());
        assertEquals(9, x[1].max());
        assertEquals(2, x[2].min());
        assertEquals(10, x[2].max());
    }

    public static Stream<Arguments> getRandomTables() {
        Random rand = new Random(67292);
        return getRepeatedSolverSuppliers(50)
                .map(supplier -> Arguments.of(supplier,
                        randomTuples(rand, 3, 50, 2, 8),
                        randomTuples(rand, 3, 50, 2, 8),
                        randomTuples(rand, 3, 50, 2, 8)));
    }

    @ParameterizedTest(name = "CPSolver, 3 Sets of Tuples")
    @MethodSource("getRandomTables")
    public void testSameSearchAsDecomp(Supplier<CPSolver> cpSupplier,
                                        int[][] t1, int[][] t2, int[][] t3) {
        assertSameSearch(cpSupplier, TableDecomp::new, TableCT::new, t1, t2, t3);
    }

    /**
     * Asserts that 2 table constraints are doing the same filtering.
     * This compares the {@link SearchStatistics} from two identical branching on the two table constraints
     *
     * @param cpSupplier Gives a CP solver to work with
     * @param table1     Supplier of Table constraint, given a list of variables and tuples
     * @param table2     Supplier of Table constraint, given a list of variables and tuples
     * @param t1         Sets of tuples used to add a 1st Table constraint
     * @param t2         Sets of tuples used to add a 2nd Table constraint
     * @param t3         Sets of tuples used to add a 3rd Table constraint
     * @throws AssertionFailedError if the searches are not the same
     */
    public static void assertSameSearch(Supplier<CPSolver> cpSupplier,
                                        BiFunction<CPIntVar[], int[][], CPConstraint> table1,
                                        BiFunction<CPIntVar[], int[][], CPConstraint> table2,
                                        int[][] t1, int[][] t2, int[][] t3) {
        SearchStatistics stats1 = null;
        SearchStatistics stats2 = null;
        try {
            CPSolver cp = cpSupplier.get();
            CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 9);
            cp.post(CPFactory.allDifferent(x));
            cp.post(table1.apply(new CPIntVar[]{x[0], x[1], x[2]}, t1));
            cp.post(table1.apply(new CPIntVar[]{x[2], x[3], x[4]}, t2));
            cp.post(table1.apply(new CPIntVar[]{x[0], x[2], x[4]}, t3));
            stats1 = CPFactory.makeDfs(cp, Searches.firstFailBinary(x)).solve();
        } catch (InconsistencyException ignored) {

        }

        try {
            CPSolver cp = cpSupplier.get();
            CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 9);
            cp.post(CPFactory.allDifferent(x));
            cp.post(table2.apply(new CPIntVar[]{x[0], x[1], x[2]}, t1));
            cp.post(table2.apply(new CPIntVar[]{x[2], x[3], x[4]}, t2));
            cp.post(table2.apply(new CPIntVar[]{x[0], x[2], x[4]}, t3));
            stats2 = CPFactory.makeDfs(cp, Searches.firstFailBinary(x)).solve();
        } catch (InconsistencyException ignored) {

        }

        assertTrue((stats1 == null && stats2 == null) || (stats1 != null && stats2 != null));
        if (stats1 != null) {
            assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
            assertEquals(stats1.numberOfFailures(), stats2.numberOfFailures());
            assertEquals(stats1.numberOfNodes(), stats2.numberOfNodes());
        }
    }

}
