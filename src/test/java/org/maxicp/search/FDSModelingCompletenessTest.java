/*
 * MaxiCP is under MIT License
 * Copyright (c)  2026 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
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

    @Test
    void rawFdsIsCompleteOn4QueensIntVars() {
        int n = 4;
        CPSolver cp1 = CPFactory.makeSolver();
        CPIntVar[] q1 = CPFactory.makeIntVarArray(cp1, n, n);
        cp1.post(CPFactory.allDifferent(q1));
        cp1.post(CPFactory.allDifferent(CPFactory.makeIntVarArray(n, i -> CPFactory.plus(q1[i], i))));
        cp1.post(CPFactory.allDifferent(CPFactory.makeIntVarArray(n, i -> CPFactory.minus(q1[i], i))));
        int fdsCount = CPFactory.makeDfs(cp1, Searches.fds(q1)).solve().numberOfSolutions();

        CPSolver cp2 = CPFactory.makeSolver();
        CPIntVar[] q2 = CPFactory.makeIntVarArray(cp2, n, n);
        cp2.post(CPFactory.allDifferent(q2));
        cp2.post(CPFactory.allDifferent(CPFactory.makeIntVarArray(n, i -> CPFactory.plus(q2[i], i))));
        cp2.post(CPFactory.allDifferent(CPFactory.makeIntVarArray(n, i -> CPFactory.minus(q2[i], i))));
        int referenceCount = CPFactory.makeDfs(cp2, Searches.firstFailBinary(q2)).solve().numberOfSolutions();

        assertEquals(2, referenceCount);
        assertEquals(referenceCount, fdsCount);
    }

    @Test
    void rawFdsIsCompleteOnTinyIntervalSchedulingAndMixed() {
        CPSolver cp = CPFactory.makeSolver();
        CPIntervalVar[] tasks = new CPIntervalVar[3];
        for (int i = 0; i < 3; i++) {
            tasks[i] = CPFactory.makeIntervalVar(cp, false, 1, 1);
            tasks[i].setStartMin(0);
            tasks[i].setEndMax(3);
        }
        cp.post(CPFactory.noOverlap(tasks));
        int fdsCount = CPFactory.makeDfs(cp, Searches.fds(tasks)).solve().numberOfSolutions();
        assertEquals(6, fdsCount);

        CPSolver cpMixed = CPFactory.makeSolver();
        CPIntervalVar a = CPFactory.makeIntervalVar(cpMixed, false, 1, 1);
        a.setStartMin(0);
        a.setEndMax(3);
        CPIntervalVar b = CPFactory.makeIntervalVar(cpMixed, false, 1, 1);
        b.setStartMin(0);
        b.setEndMax(3);
        CPIntVar x = CPFactory.makeIntVar(cpMixed, 0, 1);
        cpMixed.post(CPFactory.eq(CPFactory.start(a), x));
        cpMixed.post(CPFactory.eq(CPFactory.start(b), CPFactory.plus(x, 1)));
        cpMixed.post(CPFactory.noOverlap(a, b));
        int mixedCount = CPFactory.makeDfs(cpMixed, Searches.fds(new CPIntervalVar[]{a, b}, x)).solve().numberOfSolutions();
        assertEquals(2, mixedCount);
    }

    @Test
    void fds2DIntervalsMatrixWorksInModelingAndRaw() throws Exception {
        try (ModelDispatcher model = makeModelDispatcher()) {
            IntervalVar[][] tasks = new IntervalVar[2][2];
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    tasks[i][j] = model.intervalVar(0, 3, 1, true);
                }
                model.add(endBeforeStart(tasks[i][0], tasks[i][1]));
            }
            model.add(noOverlap(tasks[0][0], tasks[1][0]));
            model.add(noOverlap(tasks[0][1], tasks[1][1]));

            int fdsCount = solveCount(model, () -> Searches.fds(tasks));
            assertEquals(2, fdsCount);
        }

        CPSolver cp = CPFactory.makeSolver();
        CPIntervalVar[][] rawTasks = new CPIntervalVar[2][2];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                rawTasks[i][j] = CPFactory.makeIntervalVar(cp, false, 1, 1);
                rawTasks[i][j].setStartMin(0);
                rawTasks[i][j].setEndMax(3);
            }
            cp.post(CPFactory.endBeforeStart(rawTasks[i][0], rawTasks[i][1]));
        }
        cp.post(CPFactory.noOverlap(new CPIntervalVar[]{rawTasks[0][0], rawTasks[1][0]}));
        cp.post(CPFactory.noOverlap(new CPIntervalVar[]{rawTasks[0][1], rawTasks[1][1]}));

        int rawFdsCount = CPFactory.makeDfs(cp, Searches.fds(rawTasks)).solve().numberOfSolutions();
        assertEquals(2, rawFdsCount);
    }
}

