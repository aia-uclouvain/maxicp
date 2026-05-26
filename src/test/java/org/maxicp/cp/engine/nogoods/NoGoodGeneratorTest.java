/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.cp.engine.nogoods;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.constraints.AllDifferentFWC;
import org.maxicp.cp.engine.constraints.EqualCst;
import org.maxicp.cp.engine.constraints.EnforceNogood;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.state.StateInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoGoodGeneratorTest extends CPSolverTest {

    private static DFSearch makeTwoLevelSearch(CPSolver cp, CPIntVar x, CPIntVar y, StateInt i) {
        return CPFactory.makeDfs(cp, () -> {
            if (i.value() >= 2) {
                return Searches.EMPTY;
            }

            if (i.value() == 0) {
                return Searches.branch(
                        () -> {
                            cp.post(CPFactory.eq(x, 0));
                            i.increment();
                        },
                        () -> cp.post(CPFactory.neq(x, 0)));
            }

            return Searches.branch(
                    () -> {
                        cp.post(CPFactory.eq(y, 1));
                        i.increment();
                    },
                    () -> cp.post(CPFactory.neq(y, 1)));
        });
    }

    private static DFSearch makeQueensSearch(CPSolver cp, CPIntVar[] q) {
        CPIntVar[] qL = CPFactory.makeIntVarArray(q.length, i -> CPFactory.minus(q[i], i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(q.length, i -> CPFactory.plus(q[i], i));

        cp.post(new AllDifferentFWC(q));
        cp.post(new AllDifferentFWC(qL));
        cp.post(new AllDifferentFWC(qR));

        return CPFactory.makeDfs(cp, Searches.firstFailBinary(q));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleNogoodAfterOneNode(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 2);
        CPIntVar y = CPFactory.makeIntVar(cp, 2);
        StateInt i = cp.getStateManager().makeStateInt(0);

        DFSearch search = makeTwoLevelSearch(cp, x, y, i);
        NoGoodGenerator maker = new NoGoodGenerator(cp, search);

        SearchStatistics stats = search.solve(s -> s.numberOfNodes() >= 1);

        assertEquals(1, stats.numberOfNodes());

        CPConstraint[][][] nogood = maker.getNoGood();
        assertEquals(1, nogood.length);
        assertEquals(1, nogood[0].length);
        assertEquals(1, nogood[0][0].length);
        assertTrue(nogood[0][0][0] instanceof EqualCst);

        EqualCst eq = (EqualCst) nogood[0][0][0];
        assertSame(x, eq.getX());
        assertEquals(0, eq.getV());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void repeatedNogoodsOnQueensEventuallyExploreAllSolutions(CPSolver cp) {
        int n = 8;
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        DFSearch search = makeQueensSearch(cp, q);
        NoGoodGenerator maker = new NoGoodGenerator(cp, search);
        EnforceNogood enforcer = new EnforceNogood(cp);

        int totalSolutions = 0;
        int iterations = 0;
        int nbNogoodToPost = -1;
        boolean inconsistent = false;

        while (nbNogoodToPost != iterations) {
            maker.clear();
            SearchStatistics stats = search.solve(s -> s.numberOfNodes() >= 100);
            totalSolutions += stats.numberOfSolutions();

            System.out.println("iter=" + iterations
                    + " nodes=" + stats.numberOfNodes()
                    + " fails=" + stats.numberOfFailures()
                    + " sols=" + stats.numberOfSolutions()
                    + " totalSols=" + totalSolutions
                    + " completed=" + stats.isCompleted());

            if (stats.isCompleted()) {
                break;
            }

            CPConstraint[][][] nogood = maker.getNoGood();
            enforcer.addNogood(nogood);
            iterations++;
            assertTrue(iterations < 100, "nogood loop did not terminate");
        }
        if (nbNogoodToPost != -1 && !inconsistent) { // still things to do
            maker.clear();
            SearchStatistics stats = search.solve();
            totalSolutions += stats.numberOfSolutions();
        }

        assertEquals(92, totalSolutions);
    }
}