/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.state.StateManagerTest;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;


public class DFSearchTest extends StateManagerTest {

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testExample1(StateManager sm) {
        StateInt i = sm.makeStateInt(0);
        int[] values = new int[3];

        DFSearch dfs = new DFSearch(sm, () -> {
            if (i.value() >= values.length)
                return EMPTY;
            else return branch(
                    () -> { // left branch
                        values[i.value()] = 0;
                        i.increment();
                    },
                    () -> { // right branch
                        values[i.value()] = 1;
                        i.increment();
                    }
            );
        });


        SearchStatistics stats = dfs.solve();

        assertEquals(8,stats.numberOfSolutions());
        assertEquals (0,stats.numberOfFailures());
        assertEquals (8 + 4 + 2,stats.numberOfNodes());
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testExample3(StateManager sm) {
        StateInt i = sm.makeStateInt(0);
        int[] values = new int[3];

        DFSearch dfs = new DFSearch(sm, () -> {
            if (i.value() >= values.length)
                return EMPTY;
            else return branch(
                    () -> { // left branch
                        values[i.value()] = 1;
                        i.increment();
                    },
                    () -> { // right branch
                        values[i.value()] = 0;
                        i.increment();
                    }
            );
        });


        dfs.onSolution(() -> {
            assert (Arrays.stream(values).allMatch(v -> v == 1));
        });


        SearchStatistics stats = dfs.solve(stat -> stat.numberOfSolutions() >= 1);

        assertEquals (1,stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testDFS(StateManager sm) {
        StateInt i = sm.makeStateInt(0);
        boolean[] values = new boolean[4];

        AtomicInteger nSols = new AtomicInteger(0);


        DFSearch dfs = new DFSearch(sm, () -> {
            if (i.value() >= values.length)
                return EMPTY;
            else return branch(
                    () -> {
                        // left branch
                        values[i.value()] = false;
                        i.increment();
                    },
                    () -> {
                        // right branch
                        values[i.value()] = true;
                        i.increment();
                    }
            );
        });

        dfs.onSolution(() -> {
            nSols.incrementAndGet();
        });


        SearchStatistics stats = dfs.solve();


        assertEquals(16, nSols.get());
        assertEquals(16, stats.numberOfSolutions());
        assertEquals(0, stats.numberOfFailures());
        assertEquals((16 + 8 + 4 + 2), stats.numberOfNodes());

    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testDFSSearchLimit(StateManager sm) {

        StateInt i = sm.makeStateInt(0);
        boolean[] values = new boolean[4];

        DFSearch dfs = new DFSearch(sm, () -> {
            if (i.value() >= values.length) {
                return branch(() -> {
                    throw new InconsistencyException();
                });
            } else return branch(
                    () -> {
                        // left branch
                        values[i.value()] = false;
                        i.increment();
                    },
                    () -> {
                        // right branch
                        values[i.value()] = true;
                        i.increment();
                    }
            );
        });




        // stop search after 2 solutions
        SearchStatistics stats = dfs.solve(stat -> stat.numberOfFailures() >= 3);

        assertEquals (0,stats.numberOfSolutions());
        assertEquals (3,stats.numberOfFailures());

    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testDeepDFS(StateManager sm) {
        StateInt i = sm.makeStateInt(0);
        boolean[] values = new boolean[10000];

        DFSearch dfs = new DFSearch(sm, () -> {
            if (i.value() >= values.length) {
                return EMPTY;
            } else return branch(
                    () -> {
                        // left branch
                        values[i.value()] = false;
                        i.increment();
                    },
                    () -> {
                        // right branch
                        values[i.value()] = true;
                        i.increment();
                    }
            );
        });
        // stop search after 1 solutions (only left most branch)
        SearchStatistics stats = dfs.solve(stat -> stat.numberOfSolutions() >= 1);
        assertEquals (1,stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void checkInconsistenciesManagedCorrectly(StateManager sm) {
        int[] values = new int[3]; //init to 0

        DFSearch dfs = new DFSearch(sm, () -> {
            if(values[0] >= 100)
                return EMPTY;

            return branch(
                    () -> {
                        values[0] += 1;
                        if(values[0] == 1)
                            throw new InconsistencyException();
                        //this should never happen in a left branch!
                        assertNotEquals(2, values[0]);
                    },
                    () -> {
                        values[0] += 1;
                    });
        });

        dfs.solve();
    }
}
