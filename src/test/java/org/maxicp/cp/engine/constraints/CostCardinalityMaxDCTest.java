/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;


import static org.junit.jupiter.api.Assertions.*;
import be.uclouvain.solvercheck.core.data.Domain;
import be.uclouvain.solvercheck.core.data.PartialAssignment;
import be.uclouvain.solvercheck.core.task.Checker;
import be.uclouvain.solvercheck.core.task.Filter;
import be.uclouvain.solvercheck.core.task.StatefulFilter;
import be.uclouvain.solvercheck.WithSolverCheck;
import be.uclouvain.solvercheck.generators.GeneratorsDSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.state.State;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Set;
import java.util.*;

import static org.maxicp.cp.CPFactory.makeSolver;

public class CostCardinalityMaxDCTest extends CPSolverTest implements WithSolverCheck {

    public GeneratorsDSL.GenDomainBuilder xDom(int maxDom) {
        return domain().withValuesBetween(0, maxDom);
    }

    @Test
    public void testStateLessCostCardinalityMaxDCWithSolverCheck() {
        int n = 7;
        int maxCard = 4;
        int maxDom = 8;
        int maxCost = 10;
        int maxH = 35;

        assertThat(
                forAll(arrayOf(Integer.class, integer().between(0, maxCard)).ofSize(maxDom)).withExamples(4).assertThat(
                        upper ->
                                forAll(arrayOf(Integer[].class, arrayOf(Integer.class, integer().between(0, maxCost)).ofSize(maxDom)).ofSize(n)).withExamples(4).assertThat(
                                        costs ->
                                                forAll(listOf("x", xDom(maxDom-1)).ofSize(n)).withExamples(10).assertThat(
                                                        x ->
                                                                forAll(integer().between(0, maxH)).withExamples(2).assertThat(
                                                                        h ->
                                                                                an(arcConsistent(costCardinalityMaxChecker(upper, costs, h)))
                                                                                        .isEquivalentTo(stateLessCostCardinalityMaxDC(upper, costs, h))
                                                                                        .forAnyPartialAssignment().with(x))
                                                )
                                )
                )
        );
    }

    @Test
    public void testStateFullCostCardinalityMaxDCWithSolverCheck() {
        int n = 8;
        int maxCard = 4;
        int maxDom = 7;
        int maxCost = 10;
        int maxH = 35;

        assertThat(
                forAll(arrayOf(Integer.class, integer().between(0, maxCard)).ofSize(maxDom)).withExamples(3).assertThat(
                        upper ->
                                forAll(arrayOf(Integer[].class, arrayOf(Integer.class, integer().between(0, maxCost)).ofSize(maxDom)).ofSize(n)).withExamples(3).assertThat(
                                        costs ->
                                                forAll(listOf("x", xDom(maxDom-1)).ofSize(n)).withExamples(5).assertThat(
                                                        x ->
                                                                forAll(integer().between(0, maxH)).withExamples(2).assertThat(
                                                                        h ->
                                                                                a(stateFullCostCardinalityMaxDC(upper,costs,h)).isEquivalentTo(stateful(arcConsistent(costCardinalityMaxChecker(upper, costs, h))))
                                                                                        .forAnyPartialAssignment().with(x))
                                                )
                                )
                )
        );
    }

    public static Checker costCardinalityMaxChecker(
            final Integer[] upper,
            final Integer[][] costs,
            final Integer maxCost) {


        return x -> {
            int totalCost = 0;
            HashMap<Integer, Integer> cardinality = new HashMap<>();
            for (int i = 0; i < x.size(); i++) {
                cardinality.put(x.get(i), cardinality.getOrDefault(x.get(i), 0) + 1);
                if (cardinality.get(x.get(i)) > upper[x.get(i)]) {
                    return false;
                }
                totalCost += costs[i][x.get(i)];
            }
            return totalCost <= maxCost;
        };
    }

    public static Set<Integer> toSet(CPIntVar x) {
        Set<Integer> dom = new HashSet<>();
        for (int i = x.min(); i <= x.max(); i++) {
            if (x.contains(i)) {
                dom.add(i);
            }
        }
        return dom;
    }

    private Filter stateLessCostCardinalityMaxDC(Integer[] upper_, Integer[][] costs_, Integer hMax) {
        return partialAssignment -> {
            CPSolver cp = CPFactory.makeSolver();
            CPIntVar[] x = new CPIntVar[partialAssignment.size()];
            for (int i = 0; i < x.length; i++) {
                x[i] = CPFactory.makeIntVar(cp, partialAssignment.get(i));
            }
            boolean fail = false;
            try {
                int[] upper = Arrays.stream(upper_).mapToInt(Integer::intValue).toArray();
                int[][] costs = new int[costs_.length][];
                for (int i = 0; i < costs_.length; i++) {
                    costs[i] = Arrays.stream(costs_[i]).mapToInt(Integer::intValue).toArray();
                }
                cp.post(new CostCardinalityMaxDC(x, upper, costs, CPFactory.makeIntVar(cp, 0, hMax)));
            } catch (InconsistencyException e) {
                fail = true;
            }

            Domain[] domains = new Domain[x.length];
            for (int i = 0; i < x.length; i++) {
                if (!fail) {
                    domains[i] = Domain.from(toSet(x[i]));
                } else {
                    domains[i] = Domain.emptyDomain();

                }
            }
            PartialAssignment res =  PartialAssignment.from(domains);
            return res;
        };
    }

    private StatefulFilter stateFullCostCardinalityMaxDC(Integer[] upper_, Integer[][] costs_, Integer hMax)  {
        return SolverCheckUtils.statefulFilter(
                (solver, vars) -> {
                    int[] upper = Arrays.stream(upper_).mapToInt(Integer::intValue).toArray();
                    int[][] costs = new int[costs_.length][];
                    for (int i = 0; i < costs_.length; i++) {
                        costs[i] = Arrays.stream(costs_[i]).mapToInt(Integer::intValue).toArray();
                    }
                    return new CostCardinalityMaxDC(vars, upper, costs, CPFactory.makeIntVar(solver, 0, hMax));
                }
        );
    }




    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest(CPSolver cp) {
        int nVars = 5;
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, nVars, 3);
        int[] upper = {3, 2, 2};
        int[][] costs = new int[nVars][3];

        for (int i = 0; i < nVars; i++) {
            costs[i][0] = 1;
            costs[i][1] = 2;
            costs[i][2] = 3;
        }

        CPIntVar H = CPFactory.makeIntVar(cp, 0, 7); // Maximum cost allowed
        CostCardinalityMaxDC constraint = new CostCardinalityMaxDC(x, upper, costs, H);
        cp.post(constraint);

        // check assignments
        State[] assignmentStates = constraint.getAssignment();
        int[] expectedAssignment = new int[]{0, 0, 0, 1, 1};
        for (int i = 0; i < assignmentStates.length; i++) {
            assertEquals(expectedAssignment[i], (int) assignmentStates[i].value());
        }

        // check min costs of assignment
        assertEquals(7, constraint.getMinCostAssignment());

        // check removing inconsistent values
        for (int i = 0; i < nVars; i++) {
            assertTrue(x[i].contains(0));
            assertTrue(x[i].contains(1));
            assertFalse(x[i].contains(2));
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTestInconsistency(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 3);
        int[] upper = {3, 2, 2};
        int[][] costs = new int[5][3];

        for (int i = 0; i < 5; i++) {
            costs[i][0] = 1;
            costs[i][1] = 2;
            costs[i][2] = 3;
        }

        CPIntVar H = CPFactory.makeIntVar(cp, 0, 6); // Maximum cost allowed
        CostCardinalityMaxDC constraint = new CostCardinalityMaxDC(x, upper, costs, H);


        assertThrowsExactly(InconsistencyException.class, () -> cp.post(constraint));
    }

    @ParameterizedTest
//    @MethodSource("getSolver")
    @ValueSource(ints = {7, 11})
    public void costGCCTest(int h) {
        CPSolver cp = makeSolver();
        int nVars = 7;
        CPIntVar[] x = new CPIntVar[]{
                makeIVar(cp, 0, 1),
                makeIVar(cp, 0, 1),
                makeIVar(cp, 0, 1),
                makeIVar(cp, 0, 1),
                makeIVar(cp, 2),
                makeIVar(cp, 3),
                makeIVar(cp, 3, 4)};

        int[] upper = {2, 2, 1, 2, 2};
        int[][] costs = new int[nVars][];

        costs[0] = new int[]{1, 4, 0, 0, 0};
        costs[1] = new int[]{1, 4, 0, 0, 0};
        costs[2] = new int[]{3, 1, 0, 0, 0};
        costs[3] = new int[]{3, 1, 0, 0, 0};
        costs[4] = new int[]{0, 0, 1, 0, 0};
        costs[5] = new int[]{0, 0, 0, 1, 0};
        costs[6] = new int[]{0, 0, 0, 1, 1};

        CPIntVar H = CPFactory.makeIntVar(cp, 0, h); // Maximum cost allowed

        CostCardinalityMaxDC constraint = new CostCardinalityMaxDC(x, upper, costs, H);
        cp.post(constraint);

        // check assignments
        State[] assignmentStates = constraint.getAssignment();
        int[] expectedAssignment = new int[]{0, 0, 1, 1, 2, 3, 3};
        for (int i = 0; i < assignmentStates.length; i++) {
            assertEquals(expectedAssignment[i], (int) assignmentStates[i].value());
        }

        // check min costs of assignment
        assertEquals(7, constraint.getMinCostAssignment());

        assertTrue(x[0].contains(0));
        assertTrue(x[1].contains(0));
        assertTrue(x[2].contains(1));
        assertTrue(x[3].contains(1));
        assertTrue(x[4].contains(2));
        assertTrue(x[5].contains(3));
        assertTrue(x[6].contains(3));
        assertTrue(x[6].contains(4));

        assertFalse(x[0].contains(1));
        assertFalse(x[1].contains(1));
        assertFalse(x[2].contains(0));
        assertFalse(x[3].contains(0));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void costGCCTest2(CPSolver cp) {
        int nVars = 7;
        CPIntVar[] x = new CPIntVar[]{
                makeIVar(cp, 0, 1),
                makeIVar(cp, 0, 1),
                makeIVar(cp, 0, 1),
                makeIVar(cp, 0, 1),
                makeIVar(cp, 2),
                makeIVar(cp, 3),
                makeIVar(cp, 3, 4)};

        int[] upper = {2, 2, 1, 2, 2};

        int[][] costs = new int[nVars][];
        costs[0] = new int[]{1, 4, 0, 0, 0};
        costs[1] = new int[]{1, 4, 0, 0, 0};
        costs[2] = new int[]{3, 1, 0, 0, 0};
        costs[3] = new int[]{3, 1, 0, 0, 0};
        costs[4] = new int[]{0, 0, 1, 0, 0};
        costs[5] = new int[]{0, 0, 0, 1, 0};
        costs[6] = new int[]{0, 0, 0, 1, 1};

        CPIntVar H = CPFactory.makeIntVar(cp, 0, 20); // Maximum cost allowed
        CostCardinalityMaxDC constraint = new CostCardinalityMaxDC(x, upper, costs, H);
        cp.post(constraint);

        // check assignments
        State[] assignmentStates = constraint.getAssignment();
        int[] expectedAssignment = new int[]{0, 0, 1, 1, 2, 3, 3};
        for (int i = 0; i < assignmentStates.length; i++) {
            assertEquals(expectedAssignment[i], (int) assignmentStates[i].value());
        }

        // check min costs of assignment
        assertEquals(7, H.min());

        assertTrue(x[0].contains(0));
        assertTrue(x[0].contains(1));
        assertTrue(x[1].contains(0));
        assertTrue(x[1].contains(1));
        assertTrue(x[2].contains(0));
        assertTrue(x[2].contains(1));
        assertTrue(x[3].contains(0));
        assertTrue(x[3].contains(1));
        assertTrue(x[4].contains(2));
        assertTrue(x[5].contains(3));
        assertTrue(x[6].contains(3));
        assertTrue(x[6].contains(4));

    }

    private static CPIntVar makeIVar(CPSolver cp, Integer... values) {
        return CPFactory.makeIntVar(cp, Set.of(values));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                makeIVar(cp, 3, 4),
                makeIVar(cp, 1),
                makeIVar(cp, 3, 4),
                makeIVar(cp, 0),
                makeIVar(cp, 3, 4, 5),
                makeIVar(cp, 5, 6, 7),
                makeIVar(cp, 2, 9, 10),
                makeIVar(cp, 5, 6, 7, 8),
                makeIVar(cp, 5, 6, 7)};

        int[] upper = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

        int[][] costs = new int[9][];
        for (int i = 0; i < costs.length; i++) {
            costs[i] = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}; // arbitrary costs
        }

        CPIntVar H = CPFactory.makeIntVar(cp, 0, 20);

        CostCardinalityMaxDC constraint = new CostCardinalityMaxDC(x, upper, costs, H);
        cp.post(constraint);

        assertFalse(x[4].contains(3));
        assertFalse(x[4].contains(4));
        assertFalse(x[5].contains(5));
        assertFalse(x[7].contains(5));
        assertFalse(x[7].contains(6));
        assertFalse(x[8].contains(5));
    }


    @Test
    public void scc() {
        SCC scc = new SCC(5);
        int[][] adjacencyMatrix = {
                {0, 1, 0, 0, 0},
                {0, 0, 1, 0, 0},
                {0, 0, 0, 1, 0},
                {1, 0, 0, 0, 1},
                {1, 0, 0, 0, 0}
        };
        scc.findSCC(adjacencyMatrix);
        int[] SCCByNode = scc.getSccByNode();
        assertEquals(1, scc.getNumSCC());
        assertEquals(0, SCCByNode[0]);
        assertEquals(0, SCCByNode[1]);
        assertEquals(0, SCCByNode[2]);
        assertEquals(0, SCCByNode[3]);
        assertEquals(0, SCCByNode[4]);

    }

    @Test
    public void scc2() {
        SCC scc = new SCC(14);
        int[][] adjacencyMatrix = {
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
                {1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
                {1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0},
                {0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
                {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0}
        };
        scc.findSCC(adjacencyMatrix);
        int[] SCCByNode = scc.getSccByNode();
        assertEquals(2, scc.getNumSCC());

        assertEquals(0, SCCByNode[1]);
        assertEquals(0, SCCByNode[2]);
        assertEquals(0, SCCByNode[3]);
        assertEquals(0, SCCByNode[8]);
        assertEquals(0, SCCByNode[9]);

        assertEquals(1, SCCByNode[7]);
        assertEquals(1, SCCByNode[11]);
        assertEquals(1, SCCByNode[12]);
        assertEquals(1, SCCByNode[13]);

    }

    // check no solution is removed compared to a decomposition of the constraint
    @Test
    public void testDecomp() {
        for (int i = 0; i < 100; i++) {
            CPSolver cp = makeSolver();
            int n = 7;
            int maxCard = 4;
            int maxDom = 5;
            int maxCost = 5;
            int maxH = 30;
            Random random = new Random(i);

            int[] upper = new int[maxDom];
            for (int j = 0; j < maxDom; j++) {
                upper[j] = random.nextInt(maxCard);
            }

            int[][] costs = new int[n][maxDom];
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < maxDom; k++) {
                    costs[j][k] = random.nextInt(maxCost);
                }
            }

            // create variables with random domains
            CPIntVar[] x = new CPIntVar[n];
            for (int j = 0; j < n; j++) {
                Set<Integer> dom = new HashSet<>();
                dom.add(random.nextInt(maxDom)); // ensure at least one value is always present
                for (int k = 0; k < maxDom; k++) {
                    if (random.nextBoolean()) {
                        dom.add(k);
                    }
                }
                x[j] = CPFactory.makeIntVar(cp, dom);
            }

            CPIntVar H = CPFactory.makeIntVar(cp, 0, maxH); // Maximum cost allowed

            DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));

            SearchStatistics stats1 = dfs.solveSubjectTo(
                    s -> false,
                    () -> {
                        CostCardinalityMaxDC c = new CostCardinalityMaxDC(x, upper, costs, H, CostCardinalityMaxDC.Algorithm.SCHMIED_REGIN_2024);
                        cp.post(c);
                    });
            assertEquals(0, stats1.numberOfFailures()); // because it is domain consistent

            SearchStatistics stats2 = dfs.solveSubjectTo(
                    s -> false,
                    () -> {
                        CardinalityMaxFWC c = new CardinalityMaxFWC(x, upper);
                        cp.post(c);
                        CPIntVar[] costsVars = CPFactory.makeIntVarArray(n, j -> CPFactory.element(costs[j], x[j]));
                        cp.post(new Sum(costsVars, H));
                    });
            assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
        }
    }


}