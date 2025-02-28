/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.datastructures.StateSparseSet;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;

public class CardinalityMinFWC extends AbstractCPConstraint {

    private final CPIntVar [] x;
    private final int [] lower;
    private final int nValues ;
    private final int nVars;

    // ----- Reversible  state -----

    // for each value, variables indices with this value in their domain
    StateSparseSet[] possibleVars;

    // Iterators array to be used with fillArray methods
    int [] iterVariables;
    int [] iterValues;

    /**
     * Constraint the minimum number of occurences of a range of values in X.
     * @param x The variables to constraint (at least one)
     * @param lower The upper cardinality bounds,
     *              lower[i] is the maximum number of occurences of value i in X
     *
     * @author Pierre Schaus
     */
    public CardinalityMinFWC(CPIntVar [] x, int [] lower) {
        super(x[0].getSolver());
        this.x = new CPIntVar[x.length];
        for (int i = 0; i < x.length; i++) {
            this.x[i] = x[i];
        }
        this.lower = new int[lower.length];
        for (int i = 0; i < lower.length; i++) {
            if (lower[i] < 0) throw new IllegalArgumentException("lower bounds must be non negative" + lower[i]);
            this.lower[i] = lower[i];
        }
        nValues = lower.length;
        nVars = x.length;
    }

    @Override
    public void post() {
        iterVariables = new int [nVars];
        iterValues = new int [Arrays.stream(x).map(var -> var.size()).max(Integer::compare).get()];
        possibleVars = new StateSparseSet[nValues];
        for (int i = 0; i < nValues; i++) {
            possibleVars[i] = new StateSparseSet(getSolver().getStateManager(), nVars,0);
        }

        // Initial fix-point
        boolean fixed = false;
        while (!fixed) {
            fixed = true;
            for (int i = 0; i < nVars; i++) {
                for (int v = 0; v < nValues; v++) {
                    if (!x[i].contains(v)) {
                        possibleVars[v].remove(i);
                    }
                }
            }
            for (int v = 0; v < nValues; v++) {
                if (possibleVars[v].size() < lower[v]) {
                    throw InconsistencyException.INCONSISTENCY;
                }
                if (possibleVars[v].size() == lower[v]) {
                    fixed = !fixPossibles(possibleVars[v],v);
                }
            }
        }
        // Incremental propagation based on delta
        for (int i = 0; i < nVars; i++) {
            int finalI = i;
            x[i].whenDomainChange(delta -> {
                int deltaSize = delta.fillArray(iterValues);
                for (int iter = 0; iter < deltaSize; iter++) {
                    int v = iterValues[iter];
                    if (0 < v && v < nValues) { // possible that domain of x vars is larger than 0..nValues-1 initially
                        possibleVars[v].remove(finalI);
                        if (possibleVars[v].size() == lower[v]) {
                            fixPossibles(possibleVars[v],v);
                        }
                    }
                }
            });
        }
    }

    // #vars were v appears  = lower[v], thus fix all vars where it appears to v
    private boolean fixPossibles(StateSparseSet possibleUnfixedVars, int v) {
        boolean changed = false;
        int n = possibleUnfixedVars.fillArray(iterVariables);
        for (int iter = 0; iter < n; iter++) {
            int i = iterVariables[iter];
            if (!x[i].isFixed()) changed = true;
            x[i].fix(v);
        }
        return changed;
    }

}
