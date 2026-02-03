/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.modeling;


import org.junit.jupiter.api.Test;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;


public class MinimizeTest extends CPSolverTest {

    @Test
    public void testObjective1() {

        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntVar x = baseModel.intVar(0, 10);

        Objective obj = minimize(x);

        baseModel.runCP((cp) -> {
            AtomicInteger nbSol = new AtomicInteger(0);

            DFSearch search = cp.dfSearch(() -> {
                if (x.isFixed()) {
                    return EMPTY;
                } else {
                    final int v = x.min();
                    return branch(() -> baseModel.add(eq(x, v)), () -> baseModel.add(neq(x, v)));
                }
            });

            search.onSolution(() -> {
                System.out.println("on Solution"+nbSol);
                nbSol.incrementAndGet();
            });
            SearchStatistics stats = search.optimize(obj); // actually solve the problem

            assertEquals(1, nbSol.get());

        });

    }


}
