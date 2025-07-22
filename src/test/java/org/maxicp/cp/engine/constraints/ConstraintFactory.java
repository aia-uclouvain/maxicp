package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;

/**
 * This interface creates the appropriate constraint to populate tests.
 */
@FunctionalInterface
public interface ConstraintFactory {
    /**
     * Creates the desired constraint using some predefined minicp model
     * and a set of already instantiated variables.
     *
     * @param solver the solver which can be used to instantiate the constraint.
     * @param vars the variables in the scope of the constraint to produce.
     * @return a choco constraint.
     */
    CPConstraint get(CPSolver solver, CPIntVar[] vars);
}

