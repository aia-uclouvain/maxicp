/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.state.StateInt;


/**
 * Reified logical or constraint
 */
public class IsOr extends AbstractCPConstraint { // b <=> x1 or x2 or ... xn

    private final CPBoolVar b;
    private final CPBoolVar[] x;
    private final int n;

    private int[] notFixed;
    private StateInt nNotFixed;

    private final Or or;

    /**
     * Creates a constraint such that
     * the boolean b is true if and only if
     * at least variable in x is true.
     *
     * @param b the boolean that is true if at least one variable in x is true
     * @param x an non empty array of variables
     */
    public IsOr(CPBoolVar b, CPBoolVar[] x) {
        super(b.getSolver());
        this.b = b;
        this.x = x;
        this.n = x.length;
        or = new Or(x);

        nNotFixed = getSolver().getStateManager().makeStateInt(n);
        notFixed = new int[n];
        for (int i = 0; i < n; i++)
            notFixed[i] = i;
    }

    @Override
    public void post() {
        b.propagateOnFix(this);
        for (CPBoolVar xi : x)
            xi.propagateOnFix(this);
    }

    @Override
    public void propagate() {
        if (b.isTrue()) {
            setActive(false);
            getSolver().post(or, false);
        } else if (b.isFalse()) {
            for (CPBoolVar xi : x)
                xi.fix(false);
            setActive(false);
        } else {
            int nU = nNotFixed.value();
            for (int i = nU - 1; i >= 0; i--) {
                int idx = notFixed[i];
                CPBoolVar y = x[idx];
                if (y.isFixed()) {
                    if (y.isTrue()) {
                        b.fix(true);
                        setActive(false);
                        return;
                    }
                    // Swap the variable
                    notFixed[i] = notFixed[nU - 1];
                    notFixed[nU - 1] = idx;
                    nU--;
                }
            }
            if (nU == 0) {
                b.fix(false);
                setActive(false);
            }
            nNotFixed.setValue(nU);
        }
    }
}
