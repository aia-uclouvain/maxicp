/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling;

import org.junit.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.maxicp.modeling.Factory.*;

public class DecisionVarCollectorTest {

    @Test
    public void allDifferentReturnsLeafVarsOnly() throws Exception {
        try (ModelDispatcher m = makeModelDispatcher()) {
            IntVar x0 = m.intVar(0, 4);
            IntVar x1 = m.intVar(0, 4);
            IntExpression derived = sum(x0, x1);

            m.add(allDifferent(x0, x1, derived));

            List<IntExpression> dvars = m.getDecisionVariables();
            assertEquals(2, dvars.size());
            assertTrue(dvars.contains(x0));
            assertTrue(dvars.contains(x1));
            assertFalse(dvars.contains(derived));
        }
    }

    @Test
    public void element2DIndicesAreDecisionVars() throws Exception {
        try (ModelDispatcher m = makeModelDispatcher()) {
            IntVar row = m.intVar(0, 1);
            IntVar col = m.intVar(0, 1);
            int[][] matrix = {{1, 2}, {3, 4}};
            IntExpression z = get(matrix, row, col);

            m.add(eq(z, m.constant(3)));

            List<IntExpression> dvars = m.getDecisionVariables();
            assertTrue(dvars.contains(row));
            assertTrue(dvars.contains(col));
            assertFalse(dvars.contains(z));
        }
    }

    @Test
    public void qapLikeModelReturnsOnlyAssignmentVars() throws Exception {
        try (ModelDispatcher m = makeModelDispatcher()) {
            int n = 3;
            IntVar[] x = m.intVarArray(n, n);
            m.add(allDifferent(x));

            int[][] dist = {{0, 1, 2}, {1, 0, 1}, {2, 1, 0}};
            int[][] weight = {{0, 3, 1}, {3, 0, 2}, {1, 2, 0}};

            IntExpression[] terms = new IntExpression[n * n];
            int k = 0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    terms[k++] = mul(get(dist, x[i], x[j]), weight[i][j]);
                }
            }
            IntExpression total = sum(terms);
            m.add(le(total, m.constant(100)));

            List<IntExpression> dvars = m.getDecisionVariables();
            assertEquals(n, dvars.size());
            for (IntVar xi : x) {
                assertTrue(dvars.contains(xi));
            }
            assertFalse(dvars.contains(total));
        }
    }

    @Test
    public void noDuplicatesAcrossConstraints() throws Exception {
        try (ModelDispatcher m = makeModelDispatcher()) {
            IntVar x = m.intVar(0, 5);
            IntVar y = m.intVar(0, 5);

            m.add(allDifferent(x, y));
            m.add(circuit(x, y));

            List<IntExpression> dvars = m.getDecisionVariables();
            assertEquals(2, dvars.size());
            assertTrue(dvars.contains(x));
            assertTrue(dvars.contains(y));
        }
    }

    @Test
    public void specializedConstraintsExposeExpectedDecisionVars() throws Exception {
        try (ModelDispatcher m = makeModelDispatcher()) {
            IntVar[] assign = m.intVarArray(3, 2);
            IntVar[] loads = m.intVarArray(2, 10);
            IntVar[] starts = m.intVarArray(3, 20);
            IntVar tx = m.intVar(0, 2);
            IntVar ty = m.intVar(0, 2);

            m.add(binPacking(assign, new int[]{1, 2, 3}, loads));
            m.add(cumulative(starts, new int[]{2, 3, 1}, new int[]{1, 1, 2}, 3));
            m.add(disjunctive(starts, new int[]{2, 3, 1}));
            m.add(table(new IntExpression[]{tx, ty}, new int[][]{{0, 1}, {1, 2}}, Optional.empty()));

            List<IntExpression> dvars = m.getDecisionVariables();

            for (IntVar v : assign) assertTrue(dvars.contains(v));
            for (IntVar v : starts) assertTrue(dvars.contains(v));
            for (IntVar v : loads) assertFalse(dvars.contains(v));
            assertTrue(dvars.contains(tx));
            assertTrue(dvars.contains(ty));
        }
    }
}

