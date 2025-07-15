package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.state.State;
import org.maxicp.util.exception.InconsistencyException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.makeSolver;

class CostCardinalityMaxDCTest extends CPSolverTest {

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
        SCC scc = new SCC();
        int[][] adjacencyMatrix = {
                {0, 1, 0, 0, 0},
                {0, 0, 1, 0, 0},
                {0, 0, 0, 1, 0},
                {1, 0, 0, 0, 1},
                {1, 0, 0, 0, 0}
        };
        scc.findSCC(adjacencyMatrix);
        List<List<Integer>> composantes = scc.getComposantes();
        assertEquals(1, composantes.size());
        assertEquals(5, composantes.getFirst().size());
        assertTrue(composantes.getFirst().contains(0));
        assertTrue(composantes.getFirst().contains(1));
        assertTrue(composantes.getFirst().contains(2));
        assertTrue(composantes.getFirst().contains(3));
        assertTrue(composantes.getFirst().contains(4));

    }

    @Test
    public void scc2() {
        SCC scc = new SCC();
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
        List<List<Integer>> composantes = scc.getComposantes();
        assertEquals(2, composantes.size());
        assertEquals(6, composantes.getFirst().size());
        assertEquals(4, composantes.getLast().size());

        assertTrue(composantes.getFirst().contains(1));
        assertTrue(composantes.getFirst().contains(2));
        assertTrue(composantes.getFirst().contains(3));
        assertTrue(composantes.getFirst().contains(8));
        assertTrue(composantes.getFirst().contains(9));

        assertTrue(composantes.getLast().contains(7));
        assertTrue(composantes.getLast().contains(11));
        assertTrue(composantes.getLast().contains(12));
        assertTrue(composantes.getLast().contains(13));

    }

}