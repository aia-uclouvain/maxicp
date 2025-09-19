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
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.cp.CPFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.engine.constraints.TableTest.assertSameSearch;
import static org.maxicp.cp.engine.constraints.TableTest.randomTuples;

public class ShortTableTest extends CPSolverTest {

    private static class ShortTableDecomp extends AbstractCPConstraint {

        private final CPIntVar[] x;
        private final int[][] table;
        private final int star; // considered as *

        /**
         * Table constraint. Assignment of x_0=v_0, x_1=v_1,... only valid if there exists a
         * row (v_0|*,v_1|*, ...) in the table.
         *
         * @param x     variables to constraint, a non empty array
         * @param table array of valid solutions (second dimension must be of same size as the array x)
         * @param star  the symbol representing "any" setValue in the table
         */
        public ShortTableDecomp(CPIntVar[] x, int[][] table, int star) {
            super(x[0].getSolver());
            this.x = x;
            this.table = table;
            this.star = star;
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
                            if (table[tupleIdx][i] == v || table[tupleIdx][i] == star) {
                                boolean allValueVariableSupported = true;
                                for (int j = 0; j < x.length && allValueVariableSupported; j++) {
                                    if (table[tupleIdx][j] != star && !x[j].contains(table[tupleIdx][j])) {
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


    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest0(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 2, 1);
        int[][] table = new int[][]{{0, 0}};
        assertDoesNotThrow(() -> cp.post(new ShortTableCT(x, table, -1)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest3(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 12);
        int[][] table = new int[][]{{0, 0, 2},
                {3, 5, 7},
                {6, 9, 10},
                {1, 2, 3}};
        cp.post(new ShortTableCT(x, table, 0));

        assertEquals(12, x[0].size());
        assertEquals(12, x[1].size());
        assertEquals(4, x[2].size());

        assertEquals(0, x[0].min());
        assertEquals(11, x[0].max());
        assertEquals(0, x[1].min());
        assertEquals(11, x[1].max());
        assertEquals(2, x[2].min());
        assertEquals(10, x[2].max());
    }

    public static Stream<Arguments> getRandomTables() {
        Random rand = new Random(67292);
        return getRepeatedSolverSuppliers(10)
                .map(supplier -> Arguments.of(supplier,
                        randomTuples(rand, 3, 50, 2, 8),
                        randomTuples(rand, 3, 50, 1, 7),
                        randomTuples(rand, 3, 50, 0, 6)));
    }

    @ParameterizedTest(name = "CPSolver, 3 Sets of Tuples")
    @MethodSource("getRandomTables")
    public void testSameSearchAsDecomp(Supplier<CPSolver> cpSupplier,
                                       int[][] t1, int[][] t2, int[][] t3) {
        Random rand = new Random(42);
        // choose a random star within the tuples
        int[][] tuples = new int[][][] {t1, t2, t3}[rand.nextInt(3)];
        int star = tuples[rand.nextInt(tuples.length)][rand.nextInt(tuples[0].length)];
        assertSameSearch(cpSupplier, (x, t) -> new ShortTableDecomp(x, t, star), (x, t) -> new ShortTableCT(x, t, star), t1, t2, t3);
    }

    /**
     * The table should accept all values of x0 and x1. However, it prunes off
     * some values of x1.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    public void maxicpReplayShortTableCtIsStrongerThanAc(CPSolver cp) {
        final int star = 2147483647;

        // This table should accept all values.
        final int[][] table = {
                {2147483647, 2147483647}
        };

        final CPIntVar x0 = CPFactory.makeIntVar(cp, Set.of(0));
        final CPIntVar x1 = CPFactory.makeIntVar(cp, Set.of(-1, 2));


        cp.post(new ShortTableCT(new CPIntVar[]{x0, x1}, table, star));

        assertEquals(1, x0.size());
        assertEquals(2, x1.size());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void issue13(CPSolver cp) {
        final int star = -2147483648;
        // This table should accept all values.
        final int[][] table = {{0, 0}};

        final CPIntVar x0 = CPFactory.makeIntVar(cp, Set.of(-5));
        final CPIntVar x1 = CPFactory.makeIntVar(cp, Set.of(-5));

        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new ShortTableCT(new CPIntVar[]{x0, x1}, table, star)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void issue14(CPSolver cp) {
        final int star = 2147483647;
        final int[][] table = {
                {2147483647, 2147483647} // means *, *
        };

        CPIntVar x0 = CPFactory.makeIntVar(cp, Set.of(0));
        CPIntVar x1 = CPFactory.makeIntVar(cp, Set.of(-1, 2));

        CPIntVar[] data = new CPIntVar[]{x0, x1};

        cp.post(new ShortTableCT(data, table, star));
        assertEquals(-1, data[1].min());
    }
}
