/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.search.DFSearch;
import org.maxicp.search.IntObjective;
import org.maxicp.search.Objective;
import org.maxicp.search.blackbox.BlackBoxSearch;
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
     * 
     * @param c               the constraint to be posted
     * @param enforceFixPoint is one wants to compute the fix-point after
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
     * Forces the boolean variable to be true and then
     * optionally computes the fix-point.
     *
     * @param b the variable that must be set to true
     */
    void post(CPBoolVar b, boolean enforceFixPoint);

    /**
     * Gives the ModelProxy associated with this CPSolver, if any
     */
    ModelProxy getModelProxy();

    /**
     * Creates a black-box search that automatically discovers the solver's decision variables.
     */
    default BlackBoxSearch blackBoxSearch() {
        if (getModelProxy() instanceof ModelDispatcher md) {
            return md.blackBoxSearch();
        }
        throw new UnsupportedOperationException("BlackBoxSearch requires a ModelDispatcher proxy");
    }

    /**
     * Creates a black-box search over the specified integer variables.
     */
    default BlackBoxSearch blackBoxSearch(CPIntVar... vars) {
        if (getModelProxy() instanceof ModelDispatcher md) {
            return md.blackBoxSearch(vars);
        }
        throw new UnsupportedOperationException("BlackBoxSearch requires a ModelDispatcher proxy");
    }

    /**
     * Creates a black-box search over the specified interval variables.
     */
    default BlackBoxSearch blackBoxSearch(CPIntervalVar... intervals) {
        if (getModelProxy() instanceof ModelDispatcher md) {
            return md.blackBoxSearch(intervals);
        }
        throw new UnsupportedOperationException("BlackBoxSearch requires a ModelDispatcher proxy");
    }

    /**
     * Creates a black-box search over the specified 2D matrix of interval variables.
     */
    default BlackBoxSearch blackBoxSearch(CPIntervalVar[][] intervals) {
        if (getModelProxy() instanceof ModelDispatcher md) {
            return md.blackBoxSearch(intervals);
        }
        throw new UnsupportedOperationException("BlackBoxSearch requires a ModelDispatcher proxy");
    }

    /**
     * Creates a black-box search over the specified sequence variables.
     */
    default BlackBoxSearch blackBoxSearch(CPSeqVar... sequences) {
        if (getModelProxy() instanceof ModelDispatcher md) {
            return md.blackBoxSearch(sequences);
        }
        throw new UnsupportedOperationException("BlackBoxSearch requires a ModelDispatcher proxy");
    }
}
