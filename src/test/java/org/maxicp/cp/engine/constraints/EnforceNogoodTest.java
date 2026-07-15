/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnforceNogoodTest extends CPSolverTest {

    private static Set<String> enumerateSolutions(CPSolver cp, CPIntVar x, CPIntVar y) {
        Set<String> solutions = new HashSet<>();
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x, y));
        dfs.onSolution(() -> solutions.add(x.min() + "," + y.min()));
        dfs.solve();
        return solutions;
    }

    private static CPConstraint[][][] nogoodForTuple(CPIntVar x, int xVal, CPIntVar y, int yVal) {
        return new CPConstraint[][][] {
                { { new EqualCst(x, xVal) } },
                { { new EqualCst(y, yVal) }, { new NotEqualCst(y, yVal) } }
        };
    }

    private static CPConstraint[][][] nogoodForTuple(CPIntVar[] vars, int[] tuple) {
        return new CPConstraint[][][] {
                { { new EqualCst(vars[0], tuple[0]) } },
                { { new EqualCst(vars[1], tuple[1]) } },
                { { new EqualCst(vars[2], tuple[2]) } },
                { { new EqualCst(vars[3], tuple[3]) }, { new NotEqualCst(vars[3], tuple[3]) } }
        };
    }

    private static List<int[]> allPermutationsOf4() {
        List<int[]> permutations = new ArrayList<>();
        int[] base = new int[] { 0, 1, 2, 3 };
        buildPermutations(base, 0, permutations);
        return permutations;
    }

    private static void buildPermutations(int[] a, int pos, List<int[]> out) {
        if (pos == a.length) {
            out.add(a.clone());
            return;
        }
        for (int i = pos; i < a.length; i++) {
            int tmp = a[pos];
            a[pos] = a[i];
            a[i] = tmp;
            buildPermutations(a, pos + 1, out);
            tmp = a[pos];
            a[pos] = a[i];
            a[i] = tmp;
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void singleNogoodRemovesTargetedSolution(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 2);
        CPIntVar y = CPFactory.makeIntVar(cp, 2);

        new EnforceNogood(cp).addNogood(nogoodForTuple(x, 0, y, 1));

        Set<String> solutions = enumerateSolutions(cp, x, y);

        assertEquals(3, solutions.size());
        assertFalse(solutions.contains("0,1"));
        assertTrue(solutions.contains("0,0"));
        assertTrue(solutions.contains("1,0"));
        assertTrue(solutions.contains("1,1"));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void multipleNogoodsAreCombined(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 2);
        CPIntVar y = CPFactory.makeIntVar(cp, 2);
        EnforceNogood enforcer = new EnforceNogood(cp);

        enforcer.addNogood(nogoodForTuple(x, 0, y, 1));
        enforcer.addNogood(nogoodForTuple(x, 1, y, 0));

        Set<String> solutions = enumerateSolutions(cp, x, y);

        assertEquals(2, solutions.size());
        assertTrue(solutions.contains("0,0"));
        assertTrue(solutions.contains("1,1"));
        assertFalse(solutions.contains("0,1"));
        assertFalse(solutions.contains("1,0"));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void nogoodsRespectSaveRestoreState(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 2);
        CPIntVar y = CPFactory.makeIntVar(cp, 2);
        EnforceNogood enforcer = new EnforceNogood(cp);

        cp.getStateManager().saveState();
        enforcer.addNogood(nogoodForTuple(x, 0, y, 0));

        Set<String> level1Solutions = enumerateSolutions(cp, x, y);
        assertEquals(3, level1Solutions.size());
        assertFalse(level1Solutions.contains("0,0"));

        cp.getStateManager().saveState();
        enforcer.addNogood(nogoodForTuple(x, 1, y, 1));

        Set<String> level2Solutions = enumerateSolutions(cp, x, y);
        assertEquals(2, level2Solutions.size());
        assertFalse(level2Solutions.contains("0,0"));
        assertFalse(level2Solutions.contains("1,1"));

        cp.getStateManager().restoreState();
        Set<String> backToLevel1Solutions = enumerateSolutions(cp, x, y);
        assertEquals(3, backToLevel1Solutions.size());
        assertFalse(backToLevel1Solutions.contains("0,0"));
        assertTrue(backToLevel1Solutions.contains("1,1"));

        cp.getStateManager().restoreState();
        Set<String> rootSolutions = enumerateSolutions(cp, x, y);
        assertEquals(4, rootSolutions.size());
        assertTrue(rootSolutions.contains("0,0"));
        assertTrue(rootSolutions.contains("0,1"));
        assertTrue(rootSolutions.contains("1,0"));
        assertTrue(rootSolutions.contains("1,1"));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void fourVarsPropagationForAllAssignmentsAndOrders(CPSolver ignored) {
        List<int[]> permutations = allPermutationsOf4();
        assertEquals(24, permutations.size());
        int[] forbiddenTuple = new int[] { 0, 0, 0, 0 };

        for (int mask = 0; mask < 16; mask++) {
            int[] assignment = new int[] {
                    (mask >> 0) & 1,
                    (mask >> 1) & 1,
                    (mask >> 2) & 1,
                    (mask >> 3) & 1
            };

            for (int[] order : permutations) {
                CPSolver cp = CPFactory.makeSolver();
                CPIntVar[] x = CPFactory.makeIntVarArray(cp, 4, 2);
                new EnforceNogood(cp).addNogood(nogoodForTuple(x, forbiddenTuple));
                boolean shouldFail = mask == 0;
                boolean shouldFixLast = true;

                // fix the first variable
                x[order[0]].fix(assignment[order[0]]);
                shouldFixLast &= assignment[order[0]] == forbiddenTuple[order[0]];
                cp.fixPoint();

                // here, nothing should be fixed yet, because the nogood is not yet active (we
                // need to fix 3 variables to trigger it)
                assertFalse(x[order[1]].isFixed());
                assertFalse(x[order[2]].isFixed());
                assertFalse(x[order[3]].isFixed());

                // fix the second variable
                x[order[1]].fix(assignment[order[1]]);
                shouldFixLast &= assignment[order[1]] == forbiddenTuple[order[1]];
                cp.fixPoint();

                // here, nothing should be fixed yet, because the nogood is not yet active (we
                // need to fix 3 variables to trigger it)
                assertFalse(x[order[2]].isFixed());
                assertFalse(x[order[3]].isFixed());

                // fix the third variable
                cp.getStateManager().saveState();
                x[order[2]].fix(assignment[order[2]]);
                shouldFixLast &= assignment[order[2]] == forbiddenTuple[order[2]];
                cp.fixPoint();

                // here we have two possibilities:
                // - if a previous variable was assigned to 1, then the nogood is not triggered
                // and nothing should be fixed
                // - if all previous variables were assigned to 0, then the nogood is triggered
                if (shouldFixLast) {
                    assertTrue(x[order[3]].isFixed());
                    assertEquals(1, x[order[3]].min());
                } else {
                    assertFalse(x[order[3]].isFixed());
                }

                // restore until two variables were fixed, and then fix the two last variables
                // at the same time, to trigger the InconsistencyException if it should be
                // raised
                cp.getStateManager().restoreState();
                x[order[2]].fix(assignment[order[2]]);
                x[order[3]].fix(assignment[order[3]]);
                if (shouldFail) {
                    assertThrowsExactly(InconsistencyException.class, cp::fixPoint);
                } else {
                    cp.fixPoint();
                }
            }
        }
    }
}
