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

public class IsSubsetTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInconsistencyTrue(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set2.exclude(1);
        b.fix(true);

        assertThrows(InconsistencyException.class, () -> cp.post(new IsSubset(b, set1, set2)));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInconsistencyFalse(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set1.excludeAll();
        set2.include(1);
        b.fix(false);

        assertThrows(InconsistencyException.class, () -> cp.post(new IsSubset(b, set1, set2)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectSubSet(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set1.include(2);
        set2.include(1);
        set2.include(2);
        set1.excludeAll();

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(b.isTrue());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectNotSubSet(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set1.include(2);
        set2.include(1);
        set2.exclude(2);

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(b.isFalse());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void removeOnSubset(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        b.fix(true);
        set1.include(1);
        set2.include(1);
        set2.exclude(2);

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(set1.isExcluded(2));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void addOnNotSubset(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        b.fix(false);
        set1.include(1);
        set2.include(1);
        set2.include(0);
        set2.exclude(2);

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(set1.isIncluded(2));
    }



    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCardinalityUpdate(CPSolver cp) {

        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        cp.post(new IsSubset(b, set1, set2));
        cp.post(CPFactory.eq(set1.card(),3));
        cp.post(CPFactory.eq(set2.card(),2));

        assertTrue(b.isFalse());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInclusionWithPossible(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        set1.include(0);
        set1.exclude(2);
        set2.include(0);
        set2.include(1);

        // set 1 = I{0} P{1} E{2}
        // set 2 = I{0,1} P{2}

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(b.isTrue());
    }
}
