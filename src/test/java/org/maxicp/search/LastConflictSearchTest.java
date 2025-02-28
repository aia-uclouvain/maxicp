/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.api.Test;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LastConflictSearchTest {

    @Test
    public void testExample1() {
        CPSolver cp = CPFactory.makeSolver();
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 8, 8);
        for(int i = 4; i < 8; i++)
            x[i].removeAbove(2);

        // apply alldifferent on the four last variables.
        // of course, this cannot work!
        CPIntVar[] fourLast = Arrays.stream(x).skip(4).toArray(CPIntVar[]::new);
        cp.post(CPFactory.allDifferent(fourLast));

        DFSearch dfs = new DFSearch(cp.getStateManager(), Searches.lastConflict(
                () -> { //select first unbound variable in x
                    for(IntVar z: x)
                        if(!z.isFixed())
                            return z;
                    return null;
                },
                IntExpression::min //select smallest value
        ));

        SearchStatistics stats = dfs.solve();
        assertEquals(stats.numberOfSolutions(), 0);
        assertEquals(stats.numberOfFailures(), 70);
        assertEquals(stats.numberOfNodes(), 138);
    }


}
