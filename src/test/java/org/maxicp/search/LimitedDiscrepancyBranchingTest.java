/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.api.Test;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.state.trail.Trailer;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class LimitedDiscrepancyBranchingTest {


    @Test
    public void testExample1() {
        StateManager sm = new Trailer();
        StateInt i = sm.makeStateInt(0);
        int[] values = new int[4];

        Supplier<Runnable[]> bs = () -> {
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
                    });
        };

        LimitedDiscrepancyBranching bsDiscrepancy =
                new LimitedDiscrepancyBranching(bs, 2);

        DFSearch dfs = new DFSearch(sm, bsDiscrepancy);

        dfs.onSolution(() -> {
            int n1 = 0;
            for (int k = 0; k < values.length; k++) {
                n1 += values[k];
            }
            assertTrue(n1 <= 2);
        });

        SearchStatistics stats = dfs.solve();

        assertEquals(11, stats.numberOfSolutions());
        assertEquals(0, stats.numberOfFailures());
        assertEquals(24, stats.numberOfNodes()); // root node does not count
    }


}
