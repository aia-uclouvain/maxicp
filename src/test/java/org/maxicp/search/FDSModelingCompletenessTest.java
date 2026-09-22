/*
 * MaxiCP is under MIT License
 * Copyright (c)  2026 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Arrays;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.modeling.Factory.*;

class FDSModelingCompletenessTest {

    private static int solveCount(ModelDispatcher model, Supplier<Supplier<Runnable[]>> branchingFactory) {
        return model.runCP(cp -> {
            // Build branching lazily at first call to ensure FDSModeling is created in concrete mode.
            Supplier<Runnable[]> branching = new Supplier<>() {
                private Supplier<Runnable[]> delegate;

                @Override
                public Runnable[] get() {
                    if (delegate == null) {
                        delegate = branchingFactory.get();
                    }
                    return delegate.get();
                }
            };
            DFSearch search = cp.dfSearch(branching);
            return search.solve().numberOfSolutions();
        });
    }

    @Test
    void fdsIsCompleteOn4QueensIntVars() throws Exception {
        try (ModelDispatcher model = makeModelDispatcher()) {
            int n = 4;
            IntVar[] q = model.intVarArray(n, n);
            IntExpression[] diagL = model.intVarArray(n, i -> q[i].plus(i));
            IntExpression[] diagR = model.intVarArray(n, i -> q[i].minus(i));

            model.add(allDifferent(q));
            model.add(allDifferent(diagL));
            model.add(allDifferent(diagR));

            int fdsCount = solveCount(model, () -> Searches.fds(q));
            int referenceCount = solveCount(model, () -> Searches.firstFailBinary(q));

            assertEquals(2, referenceCount);
            assertEquals(referenceCount, fdsCount);
        }
    }

    @Test
    void fdsIsCompleteOnTinyIntervalScheduling() throws Exception {
        try (ModelDispatcher model = makeModelDispatcher()) {
            IntervalVar[] tasks = model.intervalVarArray(3, i -> model.intervalVar(0, 3, 1, true));
            model.add(noOverlap(tasks));

            IntExpression[] starts = Arrays.stream(tasks).map(org.maxicp.modeling.Factory::start).toArray(IntExpression[]::new);

            int fdsCount = solveCount(model, () -> Searches.fds(tasks));
            int referenceCount = solveCount(model, () -> Searches.firstFailBinary(starts));

            // 3 unit tasks on starts {0,1,2} with no overlap => all permutations
            assertEquals(6, referenceCount);
            assertEquals(referenceCount, fdsCount);
        }
    }

    @Test
    void fdsIsCompleteOnMixedIntervalsAndIntVars() throws Exception {
        try (ModelDispatcher model = makeModelDispatcher()) {
            IntervalVar a = model.intervalVar(0, 3, 1, true);
            IntervalVar b = model.intervalVar(0, 3, 1, true);
            IntVar x = model.intVar(0, 1);

            model.add(eq(start(a), x));
            model.add(eq(start(b), plus(x, 1)));
            model.add(noOverlap(a, b));

            int fdsCount = solveCount(model, () -> Searches.fds(new IntervalVar[]{a, b}, x));
            int referenceCount = solveCount(model, () -> Searches.firstFailBinary(x, start(a), start(b)));

            assertEquals(2, referenceCount);
            assertEquals(referenceCount, fdsCount);
        }
    }
}

