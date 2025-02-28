/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;

/**
 * Maximum Constraint
 */
public class Maximum extends AbstractCPConstraint {

    private final CPIntVar[] x;
    private final StateInt nCandidates; // tracks how many variables are not affected by the filtering
    // x[0..nUnfixed] are the variables still usable, x[nUnfixed..] contains the unusable ones
    private final CPIntVar y;

    /**
     * Creates the maximum constraint y = maximum(x[0],x[1],...,x[n])?
     *
     * @param x the variable on which the maximum is to be found
     * @param y the variable that is equal to the maximum on x
     */
    public Maximum(CPIntVar[] x, CPIntVar y) {
        super(x[0].getSolver());
        assert (x.length > 0);
        nCandidates = getSolver().getStateManager().makeStateInt(x.length);
        this.x = new CPIntVar[x.length];
        System.arraycopy(x, 0, this.x, 0, x.length);
        this.y = y;
    }


    @Override
    public void post() {
        for (CPIntVar xi : x) {
            xi.propagateOnBoundChange(this);
        }
        y.propagateOnBoundChange(this);
        propagate();
    }


    @Override
    public void propagate() {
        int max = Integer.MIN_VALUE;
        int min = Integer.MIN_VALUE;
        int nSupport = 0;
        CPIntVar support = x[0];
        int nU = nCandidates.value();
        for (int i = 0; i < nU; i++) {
            x[i].removeAbove(y.max());
            if (x[i].max() >= y.min()) {
                nSupport += 1;
                support = x[i];
                if (x[i].max() > max) {
                    max = x[i].max();
                }
                if (x[i].min() > min) {
                    min = x[i].min();
                }
            } else {
                // x[i].max() < y.min()
                // variable x[i] cannot be selected anymore as the max
                CPIntVar tmp = x[i];
                x[i] = x[nU - 1];
                x[nU - 1] = tmp;
                i--;
                nU--;
            }
        }
        if (nSupport == 1) {
            support.removeBelow(y.min());
        }
        nCandidates.setValue(nU);
        y.removeAbove(max);
        y.removeBelow(min);
    }
}
