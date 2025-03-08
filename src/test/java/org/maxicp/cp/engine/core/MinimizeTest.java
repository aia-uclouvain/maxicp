/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.search.DFSearch;
import org.maxicp.search.IntObjective;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class MinimizeTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testObjective1(CPSolver cp) {
        CPIntVar x = makeIntVar(cp, 0, 10);

        IntObjective objective = cp.minimize(x);
        objective.setDelta(2);

        DFSearch dfs = makeDfs(cp, () -> {
            if (x.isFixed()) {
                return EMPTY;
            } else {
                final int v = x.max();
                return branch(() -> cp.post(eq(x, v)), () -> cp.post(neq(x, v)));
            }
        });

        AtomicInteger nbSol = new AtomicInteger(0);
        dfs.onSolution(() -> {
            nbSol.incrementAndGet();
        });

        dfs.optimize(objective);
        assertEquals(6, nbSol.get());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testObjective2(CPSolver cp) {
        CPIntVar x = makeIntVar(cp, 0, 10);

        IntObjective objective = cp.minimize(x);
        objective.setDelta(2);

        DFSearch dfs = makeDfs(cp, () -> {
            if (x.isFixed()) {
                return EMPTY;
            } else {
                final int v = x.max();
                return branch(() -> cp.post(eq(x, v)), () -> cp.post(neq(x, v)));
            }
        });

        assertEquals(x.max(),10);

        // 10, 8, 6
        dfs.optimize(objective, stats -> stats.numberOfSolutions() == 3);

        assertEquals(objective.getBound(),4);
        objective.setDelta(0);

        dfs.optimize(objective, stats -> stats.numberOfSolutions() == 3);
        assertEquals(objective.getBound(),2);

    }



}