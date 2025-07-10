/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

public class CostCardinalityMaxDC extends AbstractCPConstraint {

    private final CPIntVar [] x;
    private final int [] upper;
    private final int nValues ;
    private final int nVars;
    private final int [][] costs;

    private final StateInt [] assignment;

    /**
     * Constraint the maximum number of occurrences of a range of values in x.
     * @param x The variables to constraint (at least one)
     * @param upper The upper cardinality bounds,
     *              upper[i] is the maximum number of occurrences of value i in x
     * @param costs The costs associated with each value in x.
     *
     */
    public CostCardinalityMaxDC(CPIntVar [] x, int upper [], int [][] costs) {
        super(x[0].getSolver());
        nVars = x.length;
        this.x = CPFactory.makeIntVarArray(nVars, i -> x[i]);
        this.costs = costs;
        this.nValues = upper.length;
        this.upper = new int[upper.length];
        for (int i = 0; i < upper.length; i++) {
            if (upper[i] < 0) throw new IllegalArgumentException("upper bounds must be non negative" + upper[i]);
            this.upper[i] = upper[i];
        }
        if (costs.length != x.length) {
            throw new IllegalArgumentException("costs must have the same length as upper bounds");
        }
        if (costs[0].length != nValues) {
            throw new IllegalArgumentException("costs must have the same number of columns as upper bounds");
        }
        assignment = new StateInt[nVars];
        for (int i = 0; i < nVars; i++) {
            assignment[i] = getSolver().getStateManager().makeStateInt(-1); // -1 means unassigned
            this.x[i] = x[i];
        }
        // TODO check cost dimensions
    }

    @Override
    public void post() {
        for (CPIntVar var : x) {
            if (!var.isFixed())
                var.propagateOnDomainChange(this);
        }
        propagate();
    }


    @Override
    public void propagate() {
        // TODO
    }

}
