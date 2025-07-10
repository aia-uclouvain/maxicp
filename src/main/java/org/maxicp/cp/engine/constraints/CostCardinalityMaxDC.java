/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

public class CostCardinalityMaxDC extends AbstractCPConstraint {

    private final CPIntVar [] x;
    private final int [] upper;
    private final int nValues ;
    private final int nVars;

    // ----- Reversible  state -----

    // The number of variables that are fixed
    private StateInt nFixed;

    // Permutation array of indices of variables,
    // the nFixed first ones are the fixed variable indices
    private int[] fixed;

    // for each value, the number of variables that are fixed to the value
    private StateInt[] nValuesFixed;


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
        this.x = new CPIntVar[x.length];
        nFixed = getSolver().getStateManager().makeStateInt(0);
        fixed = new int[nVars];
        for (int i = 0; i < x.length; i++) {
            this.x[i] = x[i];
            fixed[i] = i;
        }

        this.nValues = upper.length;
        nValuesFixed = new StateInt[nValues];
        this.upper = new int[upper.length];
        for (int i = 0; i < upper.length; i++) {
            if (upper[i] < 0) throw new IllegalArgumentException("upper bounds must be non negative" + upper[i]);
            this.upper[i] = upper[i];
            nValuesFixed[i] = getSolver().getStateManager().makeStateInt(0);
        }
    }

    @Override
    public void post() {
        for (CPIntVar var : x) {
            if (!var.isFixed())
                var.propagateOnFix(this);
        }
        propagate();
    }


    @Override
    public void propagate() {
        int nF = nFixed.value();
        // iterate over non fixed variables
        for (int i = nF; i < x.length; i++) {
            int idx = fixed[i];
            if (x[idx].isFixed()) {
                int val = x[fixed[i]].min(); // value to remove from unfixed
                fixed[i] = fixed[nF]; // Swap the variables
                fixed[nF] = idx;
                nF++;
                if (val >= 0 && val < nValues) {
                    nValuesFixed[val].increment();
                    if (nValuesFixed[val].value() > upper[val]) {
                        throw new InconsistencyException();
                    }
                    if (nValuesFixed[val].value() == upper[val]) {
                        // remove the value from the possible values of the unfixed variables
                        for (int j = nF; j < x.length; j++) {
                            x[fixed[j]].remove(val);
                        }
                    }
                }
            }
        }
        nFixed.setValue(nF);
    }

}
