package org.maxicp.cp.engine.constraints.setvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPSetVar;
import org.maxicp.cp.engine.core.CPSetVarImpl;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NotSubsetTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInconsistencyOnSubset(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set1.excludeAll();
        set2.include(1);
        set2.include(2);

        assertThrows(InconsistencyException.class, () -> cp.post(new NotSubset(set1, set2)));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void addOnlyPossibleValue(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        set1.exclude(1);
        set1.exclude(2);
        set2.exclude(0);

        cp.post(new NotSubset(set1, set2));

        assertTrue(set1.isIncluded(0));

    }
}
