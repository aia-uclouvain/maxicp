/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

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

public class IsIncludedTest extends CPSolverTest {


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInconsistencyTrue(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        set1.exclude(1);
        b.fix(true);

        assertThrows(InconsistencyException.class, () -> cp.post(new IsIncluded(b, set1,1)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInconsistencyFalse(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        set1.include(1);
        b.fix(false);

        assertThrows(InconsistencyException.class, () -> cp.post(new IsIncluded(b, set1,1)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIncludeOnTrue(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        b.fix(true);

        cp.post(new IsIncluded(b,set1, 1));

        assertTrue(set1.isIncluded(1));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testExcludeOnFalse(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        b.fix(false);

        cp.post(new IsIncluded(b, set1,1));

        assertTrue(set1.isExcluded(1));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFalseOnExclude(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        set1.exclude(1);

        cp.post(new IsIncluded(b, set1,1));

        assertTrue(b.isFalse());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTrueOnInclude(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        set1.include(1);

        cp.post(new IsIncluded(b, set1,1));

        assertTrue(b.isTrue());
    }

}
