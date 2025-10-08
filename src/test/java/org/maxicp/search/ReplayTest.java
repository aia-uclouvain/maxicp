package org.maxicp.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.constraints.*;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.raw.TSPSeqVar;
import org.maxicp.state.StateInt;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

public class ReplayTest extends CPSolverTest {

    public static Supplier<Runnable[]> staticBranching(CPIntVar[] x) {
        CPSolver cp = x[0].getSolver();
        StateInt i = cp.getStateManager().makeStateInt(0);
        return () -> staticSearch(x,i);
    }

    public static Runnable[] staticSearch(CPIntVar[] x, StateInt i) {
        if (i.value() >= x.length) return EMPTY;
        CPSolver cp = x[0].getSolver();
        int v = x[i.value()].min();
        CPIntVar var = x[i.value()];
        Runnable[] res = branch(() -> {
            cp.post(CPFactory.eq(var, v));
            i.increment();
        }, () -> {
            cp.post(CPFactory.neq(var, v));
        });
        return res;
    }




    @ParameterizedTest
    @MethodSource("getSolver")
    public void noChangeTest(CPSolver cp) {
        int n = 8;
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] qL = CPFactory.makeIntVarArray(n, i -> minus(q[i], i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(n, i -> plus(q[i], i));


        cp.post(new AllDifferentFWC(q));
        cp.post(new AllDifferentFWC(qL));
        cp.post(new AllDifferentFWC(qR));


        DFSLinearizer linearizer = new DFSLinearizer();

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFail(q));

        SearchStatistics stats = search.solve(linearizer);


        SearchStatistics stats2 = search.replaySubjectTo(linearizer, q, () -> {
        });
        long branchingCounter = linearizer.branchingActions.stream().filter(a -> a instanceof BranchingAction).count();
        assertEquals(branchingCounter, stats.numberOfNodes());
        assertEquals(stats.numberOfSolutions(), stats2.numberOfSolutions());
        assertEquals(stats.numberOfFailures(), stats2.numberOfFailures());
        assertEquals(stats.numberOfNodes(), stats2.numberOfNodes());
        assertEquals(stats.isCompleted(), stats2.isCompleted());
    }
    

    @ParameterizedTest
    @MethodSource("getSolver")
    public void staticOrderTest(CPSolver cp) {
        int n = 8;
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] qL = CPFactory.makeIntVarArray(n, i -> minus(q[i], i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(n, i -> plus(q[i], i));


        cp.post(new AllDifferentFWC(q));
        cp.post(new AllDifferentFWC(qL));
        cp.post(new AllDifferentFWC(qR));



        DFSLinearizer linearizer = new DFSLinearizer();

        DFSearch search = CPFactory.makeDfs(cp, staticBranching(q));


        search.solve(linearizer);


        SearchStatistics stats1 = search.replay(linearizer, q);

        SearchStatistics stats2 = search.replaySubjectTo(linearizer, q, () -> {
            cp.post(new AllDifferentDC(q));
            cp.post(new AllDifferentDC(qL));
            cp.post(new AllDifferentDC(qR));
        });



        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
        assertTrue(stats1.numberOfNodes() >= stats2.numberOfNodes());
        assertTrue(stats1.numberOfFailures() >= stats2.numberOfFailures());

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void noChangeOptimizeTest(CPSolver cp) {

        TSPSeqVar.TSPInstance instance = new TSPSeqVar.TSPInstance("data/TSP/instance_10_0.xml");
        int n = instance.n;
        int[][] distanceMatrix = instance.distanceMatrix;

        CPIntVar[] succ = makeIntVarArray(cp, n, n);
        CPIntVar[] distSucc = makeIntVarArray(cp, n, 1000);

        cp.post(new Circuit(succ));
        for (int i = 0; i < n; i++) {
            cp.post(new Element1D(distanceMatrix[i], succ[i], distSucc[i]));
        }

        CPIntVar totalDist = sum(distSucc);
        Objective obj = cp.minimize(totalDist);

        DFSearch dfs = makeDfs(cp, staticOrder(succ));

        DFSLinearizer linearizer = new DFSLinearizer();
        SearchStatistics stats = dfs.optimize(obj,linearizer);
        System.out.println(stats);

        obj.relax();
        SearchStatistics stat1 = dfs.replaySubjectTo(linearizer, succ, () -> {
        },obj);
        System.out.println(stat1);
        assertEquals(stats.numberOfSolutions(), stat1.numberOfSolutions());
        assertEquals(stats.numberOfNodes(), stat1.numberOfNodes());
        assertEquals(stats.numberOfFailures(), stat1.numberOfFailures());


        obj.relax();
        SearchStatistics stat2 = dfs.replaySubjectTo(linearizer, succ, () -> {
            // redundant constraint
            cp.post(new CostAllDifferentDC(succ,distanceMatrix,totalDist));
        }, obj);

        assertEquals(stat1.numberOfSolutions(), stat2.numberOfSolutions());
        assertTrue(stat1.numberOfFailures() > stat2.numberOfFailures());



    }

}
