/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class SubCircuitTest extends CPSolverTest {

    // Valid: Complete Hamiltonian circuit
    int[] subcircuit1ok = new int[]{1, 2, 3, 4, 5, 0};
    // Valid: Sub-circuit of nodes 0, 1, 2. Nodes 3, 4, 5 are self-loops.
    int[] subcircuit2ok = new int[]{1, 2, 0, 3, 4, 5};
    // Valid: Empty sub-circuit (all self-loops)
    int[] subcircuit3ok = new int[]{0, 1, 2, 3, 4, 5};

    // Invalid: Not all-different (node 2 visited twice)
    int[] subcircuit1ko = new int[]{1, 2, 3, 4, 5, 2};
    // Invalid: Two disjoint sub-circuits (0-1-2 and 3-4-5)
    int[] subcircuit2ko = new int[]{1, 2, 0, 4, 5, 3};
    // Invalid: Two disjoint sub-circuits (0-1 and 2-3)
    int[] subcircuit3ko = new int[]{1, 0, 3, 2, 4, 5};

    /**
     * Checks if the given array forms a valid SubCircuit:
     * - All nodes have exactly one incoming edge (AllDifferent)
     * - There is at most ONE cycle of length > 1
     * - All nodes not in the cycle must point to themselves (self-loops)
     */
    public static boolean checkSubCircuit(int[] circuit) {
        int n = circuit.length;
        int[] count = new int[n];
        for (int v : circuit) {
            count[v]++;
            if (count[v] > 1) return false; // Alldiff violation
        }

        boolean[] visited = new boolean[n];
        int nCycles = 0;

        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                if (circuit[i] == i) {
                    // It's a self-loop, mark as visited and move on
                    visited[i] = true;
                } else {
                    // Found a cycle of length > 1
                    nCycles++;
                    int c = i;
                    while (!visited[c]) {
                        visited[c] = true;
                        c = circuit[c];
                    }
                }
            }
        }
        return nCycles <= 1;
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
    public void testSubCircuitOk(CPSolver cp) {
        cp.post(new SubCircuit(instantiate(cp, subcircuit1ok)));
        cp.post(new SubCircuit(instantiate(cp, subcircuit2ok)));
        cp.post(new SubCircuit(instantiate(cp, subcircuit3ok)));
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testSubCircuitKo(Supplier<CPSolver> cpSolverSupplier) {
        final CPSolver cp1 = cpSolverSupplier.get();
        assertThrowsExactly(InconsistencyException.class, () -> cp1.post(new SubCircuit(instantiate(cp1, subcircuit1ko))));
        final CPSolver cp2 = cpSolverSupplier.get();
        assertThrowsExactly(InconsistencyException.class, () -> cp2.post(new SubCircuit(instantiate(cp2, subcircuit2ko))));
        final CPSolver cp3 = cpSolverSupplier.get();
        assertThrowsExactly(InconsistencyException.class, () -> cp3.post(new SubCircuit(instantiate(cp3, subcircuit3ko))));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAllSolutions(CPSolver cp) {
        int n = 5;
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, n, n);
        cp.post(new SubCircuit(x));
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));

        dfs.onSolution(() -> {
            int[] sol = new int[n];
            for (int i = 0; i < n; i++) {
                sol[i] = x[i].min();
            }
            assertTrue(checkSubCircuit(sol), "Solution is not a valid SubCircuit");
        });
        SearchStatistics stats = dfs.solve();

        // For N=5, valid subcircuits are:
        // Size 0: 1
        // Size 2: C(5,2) * 1! = 10
        // Size 3: C(5,3) * 2! = 20
        // Size 4: C(5,4) * 3! = 30
        // Size 5: C(5,5) * 4! = 24
        // Total = 85
        assertEquals(85, stats.numberOfSolutions());
    }



    @Test
    public void testSubCircuitDebugRandom() {
        for (int iter = 0; iter < 1000; iter++) {
            CPSolver cp = CPFactory.makeSolver();
            int n = 5;
            CPIntVar[] x = CPFactory.makeIntVarArray(n, i -> {
                // Generate smaller domains to force tighter subcircuit logic
                return CPFactory.makeIntVar(cp, generateRandomSet(n - 1, n));
            });

            SubCircuit c = new SubCircuit(x);

            try {
                cp.post(c);
            } catch (InconsistencyException e) {
                continue;
            }

            DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
            dfs.onSolution(() -> {
                int[] sol = new int[n];
                for (int i = 0; i < n; i++) {
                    sol[i] = x[i].min();
                }
                assertTrue(checkSubCircuit(sol));
            });
            dfs.solve();
        }
    }

    public static Set<Integer> generateRandomSet(int card, int max) {
        Set<Integer> randomSet = new HashSet<>();
        Random random = new Random();
        for (int i = 0; i < card; i++) {
            randomSet.add(random.nextInt(max));
        }
        if (randomSet.isEmpty()) randomSet.add(0);
        return randomSet;
    }
}