/*
 * MaxiCP is under MIT License
 * Copyright (c)  2026 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.util.exception.InconsistencyException;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LastConflictTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void doesNotBranchOnAConflictVariableThatIsFixed(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVarArray(cp, 1, 2)[0];
        Supplier<Runnable[]> branching = Searches.lastConflict(Searches.minDomVariableSelector(x), IntExpression::min);

        Runnable[] alternatives = branching.get(); // x = 0, x != 0
        cp.post(CPFactory.neq(x, 0)); // x is now fixed to 1
        // x = 0 fails, as when the search takes that branch: x becomes the last conflict, and the search backtracks
        // before branching again
        cp.getStateManager().saveState();
        assertThrows(InconsistencyException.class, alternatives[0]::run);
        cp.getStateManager().restoreState();

        // x is fixed, so there is nothing left to branch on (and a branching on x would give a child equal to its
        // parent, and a failing one)
        assertEquals(0, branching.get().length);
    }
}
