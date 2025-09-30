package org.maxicp.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.constraints.Circuit;
import org.maxicp.cp.engine.constraints.CostAllDifferentDC;
import org.maxicp.cp.engine.constraints.Element1D;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.bool.NotEq;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.state.StateInt;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.allDifferent;
import static org.maxicp.cp.CPFactory.allDifferentDC;
import static org.maxicp.search.Searches.*;

public class ReplayTest extends CPSolverTest {

    public static Supplier<Runnable[]> heuristic(CPIntVar[] x) {
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

        cp.post(allDifferent(q));
        cp.post(allDifferent(qL));
        cp.post(allDifferent(qR));


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
    
    /*
    @ParameterizedTest
    @MethodSource("getSolver")
    public void staticOrderTest(CPSolver cp) {
        int n = 8;
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] qL = CPFactory.makeIntVarArray(n, i -> minus(q[i], i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(n, i -> plus(q[i], i));
        CPConstraint adQ = allDifferent(q);
        CPConstraint adQL = allDifferent(qL);
        CPConstraint adQR = allDifferent(qR);
        cp.post(adQ);
        cp.post(adQL);
        cp.post(adQR);


        DFSLinearizer linearizer = new DFSLinearizer();

        DFSearch search = CPFactory.makeDfs(cp, heuristic(q));
        //DFSearch search = CPFactory.makeDfs(cp, Searches.staticOrder(q));
        search.onSolution(() -> {
            System.out.println("solution:" + Arrays.toString(q));
        });

        SearchStatistics stats = search.solve(linearizer);
        SearchStatistics stats2 = search.replaySubjectTo(linearizer, q, () -> {
            cp.post(allDifferentDC(q));
            cp.post(allDifferentDC(qL));
            cp.post(allDifferentDC(qR));
        });

        cp.post(allDifferentDC(q));
        cp.post(allDifferentDC(qL));
        cp.post(allDifferentDC(qR));

        SearchStatistics stats3 = search.solve();
        assertEquals(stats2.numberOfSolutions(), stats3.numberOfSolutions());
        assertEquals(stats2.numberOfNodes(), stats3.numberOfNodes());
        assertEquals(stats2.numberOfFailures(), stats3.numberOfFailures());

    }*/

    /*
    @ParameterizedTest
    @MethodSource("getSolver")
    public void noChangeOptimizeTest(CPSolver cp) {
        InputReader reader = new InputReader("data/TSP/tsp.txt");

        int n = reader.getInt();

        int[][] distanceMatrix = reader.getIntMatrix(n, n);

        CPIntVar[] succ = makeIntVarArray(cp, n, n);
        CPIntVar[] distSucc = makeIntVarArray(cp, n, 1000);

        cp.post(new Circuit(succ));
        for (int i = 0; i < n; i++) {
            cp.post(new Element1D(distanceMatrix[i], succ[i], distSucc[i]));
        }

        CPIntVar totalDist = sum(distSucc);
        Objective obj = cp.minimize(totalDist);

        // redundant constraint
        cp.post(new CostAllDifferentDC(succ,distanceMatrix,totalDist));

        DFSearch dfs = makeDfs(cp, staticOrder(succ));

        DFSLinearizer linearizer = new DFSLinearizer();
        SearchStatistics stats = dfs.optimize(obj,linearizer);
        System.out.println(stats);
        System.out.println(linearizer.branchingActions.stream().filter(a -> a instanceof BranchingAction).count());
        SearchStatistics statsReplay = dfs.replaySubjectTo(linearizer, succ, () -> {}, obj);
        System.out.println(statsReplay);
        assertEquals(stats.numberOfSolutions(), statsReplay.numberOfSolutions());
        assertEquals(stats.numberOfNodes(), statsReplay.numberOfNodes());
        assertEquals(stats.numberOfFailures(), statsReplay.numberOfFailures());


    }*/

}
