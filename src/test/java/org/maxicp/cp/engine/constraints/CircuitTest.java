/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.cp.CPFactory;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.firstFailBinary;


public class CircuitTest extends CPSolverTest {

    int[] circuit1ok = new int[]{1, 2, 3, 4, 5, 0};
    int[] circuit2ok = new int[]{1, 2, 3, 4, 5, 0};
    int[] circuit1ko = new int[]{1, 2, 3, 4, 5, 2};
    int[] circuit2ko = new int[]{1, 2, 0, 4, 5, 3};
    int[] circuit3ko = new int[]{0, 2, 3, 4, 5, 3};
    int[] circuit4ko = new int[]{1, 2, 0, 4, 5, 3};
    int[] circuit5ko = new int[]{1, 2, 3, 4, 0, 5};

    public static boolean checkHamiltonian(int[] circuit) {
        int[] count = new int[circuit.length];
        for (int v : circuit) {
            count[v]++;
            if (count[v] > 1) return false;
        }
        boolean[] visited = new boolean[circuit.length];
        int c = circuit[0];
        for (int i = 0; i < circuit.length; i++) {
            visited[c] = true;
            c = circuit[c];
        }
        for (int i = 0; i < circuit.length; i++) {
            if (!visited[i]) return false;
        }
        return true;
    }

    public static CPIntVar[] instantiate(CPSolver cp, int[] circuit) {
        CPIntVar[] x = new CPIntVar[circuit.length];
        for (int i = 0; i < circuit.length; i++) {
            x[i] = CPFactory.makeIntVar(cp, circuit[i], circuit[i]);
        }
        return x;
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCircuitOk(CPSolver cp) {
        cp.post(new Circuit(instantiate(cp, circuit1ok)));
        cp.post(new Circuit(instantiate(cp, circuit2ok)));

    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testCircuitKo(Supplier<CPSolver> cpSolverSupplier) {
        final CPSolver cp1 = cpSolverSupplier.get();
        assertThrowsExactly(InconsistencyException.class, () -> cp1.post(new Circuit(instantiate(cp1, circuit1ko))));
        final CPSolver cp2 = cpSolverSupplier.get();
        assertThrowsExactly(InconsistencyException.class, () -> cp2.post(new Circuit(instantiate(cp2, circuit2ko))));
        final CPSolver cp3 = cpSolverSupplier.get();
        assertThrowsExactly(InconsistencyException.class, () -> cp3.post(new Circuit(instantiate(cp3, circuit3ko))));
        final CPSolver cp4 = cpSolverSupplier.get();
        assertThrowsExactly(InconsistencyException.class, () -> cp4.post(new Circuit(instantiate(cp4, circuit4ko))));
        final CPSolver cp5 = cpSolverSupplier.get();
        assertThrowsExactly(InconsistencyException.class, () -> cp5.post(new Circuit(instantiate(cp5, circuit5ko))));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAllSolutions(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 5);
        cp.post(new Circuit(x));
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));

        dfs.onSolution(() -> {
            int[] sol = new int[x.length];
            for (int i = 0; i < x.length; i++) {
                sol[i] = x[i].min();
            }
            assertTrue(checkHamiltonian(sol), "Solution is not an hamiltonian Circuit");
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(24, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCircuit(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 6, 6);
        x[0].fix(2);
        x[1].fix(0);
        x[2].removeAbove(3);
        x[3].removeBelow(3);
        x[4].removeBelow(3);
        x[5].removeBelow(3);
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new Circuit(x)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCircuitDisconnected(CPSolver cp) {
        // two SCCs
        CPIntVar[] x1 = CPFactory.makeIntVarArray(6, i -> i < 3 ? CPFactory.makeIntVar(cp,0,2): CPFactory.makeIntVar(cp,3,5));
        //assertThrowsExactly(InconsistencyException.class, () -> cp.post(new Circuit(x1)));
        // three SCCs
        CPIntVar[] x2 = CPFactory.makeIntVarArray(6, i ->
                switch (i) {
                    case 0 -> CPFactory.makeIntVar(cp,0,1);
                    case 1 -> CPFactory.makeIntVar(cp,0,1);
                    case 2 -> CPFactory.makeIntVar(cp,2,3);
                    case 3 -> CPFactory.makeIntVar(cp,2,3);
                    case 4 -> CPFactory.makeIntVar(cp,4,5);
                    case 5 -> CPFactory.makeIntVar(cp,4,5);
                    default -> throw new IllegalArgumentException("Unexpected value: " + i % 3);
                });
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new Circuit(x2)));
    }

    @Test
    public void bug1() {
        CPSolver cp = CPFactory.makeSolver();
        CPIntVar[] x = new CPIntVar[5];
        x[0] = CPFactory.makeIntVar(cp, Set.of(3, 4));   // -> 3
        x[1] = CPFactory.makeIntVar(cp, Set.of(2, 4));   // -> 2
        x[2] = CPFactory.makeIntVar(cp, Set.of(0,1, 3)); // -> 0
        x[3] = CPFactory.makeIntVar(cp, Set.of(1, 4));   // -> 4
        x[4] = CPFactory.makeIntVar(cp, Set.of(0,1,2));  // -> 1
        Circuit c = new Circuit(x);
        cp.post(c);
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
        SearchStatistics stat = dfs.solve();
        assertEquals(2, stat.numberOfSolutions());
    }



    @Test
    public void testCircuitDebug() {
        for (int iter = 0; iter < 10000; iter++) {
            CPSolver cp = CPFactory.makeSolver();
            int n = 5;
            CPIntVar[] x = CPFactory.makeIntVarArray(n, i -> {
                return CPFactory.makeIntVar(cp, generateRandomSet(n-2,n));
            });
            Circuit c = new Circuit(x);
            c.deactivateSCC = true;
            try {
                cp.post(c);
            } catch (InconsistencyException e) {
                continue;
            }
            DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
            SearchStatistics stat1 = dfs.solve();
            c.deactivateSCC = false;
            SearchStatistics stat2 = dfs.solve(); // with SCC
            if (stat1.numberOfSolutions() != stat2.numberOfSolutions() && stat2.numberOfSolutions() == 0) {
                System.out.println(stat1.numberOfSolutions() + " " + stat2.numberOfSolutions());
            }
            //assertEquals(stat1.numberOfSolutions(), stat2.numberOfSolutions());
        }
    }

    public static Set<Integer> generateRandomSet(int card, int max) {
        Set<Integer> randomSet = new HashSet<>();
        Random random = new Random();
        for (int i = 0; i < card; i++) {
               randomSet.add(random.nextInt(max));
        }
        return randomSet;
    }

}
