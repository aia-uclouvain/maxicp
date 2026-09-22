package org.maxicp.cp.modeling;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.Element1D;
import org.maxicp.modeling.algebra.integer.Element2D;
import org.maxicp.modeling.algebra.integer.IntExpression;

import org.maxicp.modeling.algebra.VariableNotFixedException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionInstantiationTest {

    /**
     * Verifies that get(d, i, j) (Element2D) returns d[i_val][j_val] exactly
     * when both index variables are fixed, both in symbolic and concrete mode.
     */
    @Test
    void element2DFixedIndicesYieldCorrectValue() throws VariableNotFixedException {
        int[][] d = {
                {10, 20, 30},
                {40, 50, 60},
                {70, 80, 90}
        };

        ModelDispatcher model = new ModelDispatcher();
        IntVar row = model.intVar(0, 2);
        IntVar col = model.intVar(0, 2);

        IntExpression element = new Element2D(d, row, col);

        // Before any constraint: domain covers all 9 values
        assertEquals(10, element.min(), "symbolic min over full domain should be 10");
        assertEquals(90, element.max(), "symbolic max over full domain should be 90");

        // Fix row=1, col=2 -> d[1][2]=60
        model.add(Factory.eq(row, 1));
        model.add(Factory.eq(col, 2));

        // After CP instantiation the concrete propagator takes over
        model.cpInstantiate();
        assertEquals(60, element.min(), "concrete min should be d[1][2]=60");
        assertEquals(60, element.max(), "concrete max should be d[1][2]=60");
        assertEquals(60, element.evaluate(), "concrete evaluate should be d[1][2]=60");
    }

    /**
     * Verifies that get(d, i, i) — same variable used for both row and column —
     * is correctly evaluated when the variable is fixed.
     *
     * Note: in the symbolic (pre-instantiation) layer the expression treats the two
     * index slots independently, so min/max range over ALL (row,col) combinations.
     * Only after CP instantiation with the variable fixed does the expression collapse
     * to d[v][v].
     */
    @Test
    void element2DSameVariableForBothIndices() throws VariableNotFixedException {
        int[][] d = {
                { 1, 99, 99},
                {99,  5, 99},
                {99, 99,  9}
        };

        ModelDispatcher model = new ModelDispatcher();
        IntVar idx = model.intVar(0, 2);

        IntExpression diagonal = new Element2D(d, idx, idx);

        // Symbolic: iterates all (row,col) combinations independently,
        // so off-diagonal 99 appears in the range.
        assertEquals(1,  diagonal.min(), "symbolic min over full idx domain should be 1");
        assertEquals(99, diagonal.max(), "symbolic max includes off-diagonal 99s");

        // Fix idx=1 -> d[1][1]=5
        model.add(Factory.eq(idx, 1));

        model.cpInstantiate();
        assertEquals(5, diagonal.min(), "concrete min should be d[1][1]=5");
        assertEquals(5, diagonal.max(), "concrete max should be d[1][1]=5");
        assertEquals(5, diagonal.evaluate(), "concrete evaluate should be 5");
    }

    @Test
    void expressionInstantiation() {
        ModelDispatcher modelDispatcher = new ModelDispatcher();

        int[] array = new int[]{10, 0, 10};
        IntExpression index = modelDispatcher.intVar(0, 2);

        modelDispatcher.add(Factory.neq(1, index));

        IntExpression element = new Element1D(array, index);
        assertEquals(0, element.min());
        assertEquals(10, element.max());

        modelDispatcher.cpInstantiate();
        assertEquals(10, element.min());
        assertEquals(10, element.max());
    }
}
