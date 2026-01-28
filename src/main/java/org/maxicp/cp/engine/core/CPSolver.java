/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.search.DFSearch;
import org.maxicp.search.IntObjective;
import org.maxicp.search.Objective;
import org.maxicp.state.StateManaged;
import org.maxicp.util.exception.InconsistencyException;

public interface CPSolver extends StateManaged {

    /**
     * Posts the constraint, that is call {@link CPConstraint#post()} and
     * computes the fix-point.
     * A {@link InconsistencyException} is thrown
     * if by posting the constraint it is proven that there is no solution.
     *
     * @param c the constraint to be posted
     */
    void post(CPConstraint c);

    /**
     * Schedules the constraint to be propagated by the fix-point.
     *
     * @param c the constraint to be scheduled
     */
    void schedule(CPConstraint c);

    /**
     * Posts the constraint that is call {@link CPConstraint#post()}
     * and optionally computes the fix-point.
     * A {@link InconsistencyException} is thrown
     * if by posting the constraint it is proven that there is no solution.
     * @param c the constraint to be posted
     * @param enforceFixPoint is one wants to computeLowerBound the fix-point after
     */
    void post(CPConstraint c, boolean enforceFixPoint);

    /**
     * Computes the fix-point with all the scheduled constraints.
     */
    void fixPoint();

    /**
     * Adds a listener called whenever the fix-point.
     *
     * @param listener the listener that is called whenever the fix-point is started
     */
    void onFixPoint(Runnable listener);

    /**
     * Creates a minimization objective on the given variable.
     *
     * @param x the variable to minimize
     * @return an objective that can minimize x
     * @see DFSearch#optimize(Objective)
     */
    IntObjective minimize(CPIntVar x);

    /**
     * Creates a maximization objective on the given variable.
     *
     * @param x the variable to maximize
     * @return an objective that can maximize x
     * @see DFSearch#optimize(Objective)
     */
    IntObjective maximize(CPIntVar x);

    /**
     * Forces the boolean variable to be true and then
     * computes the fix-point.
     *
     * @param b the variable that must be set to true
     */
    void post(CPBoolVar b);

    /**
     * Gives the ModelProxy associated with this CPSolver, if any
     */
    ModelProxy getModelProxy();
}

