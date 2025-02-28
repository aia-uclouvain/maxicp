/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.state.StateInt;

import static org.maxicp.util.exception.InconsistencyException.INCONSISTENCY;

/**
 * Logical or constraint {@code  x1 or x2 or ... xn}
 */
public class Or extends AbstractCPConstraint { // x1 or x2 or ... xn

    private final CPBoolVar[] x;
    private final int n;
    private StateInt wL; // watched literal left
    private StateInt wR; // watched literal right


    /**
     * Creates a logical or constraint: at least one variable is true:
     * {@code  x1 or x2 or ... xn}
     *
     * @param x the variables in the scope of the constraint
     */
    public Or(CPBoolVar[] x) {
        super(x[0].getSolver());
        this.x = x;
        this.n = x.length;
        wL = getSolver().getStateManager().makeStateInt(0);
        wR = getSolver().getStateManager().makeStateInt(n - 1);
    }

    @Override
    public void post() {
        propagate();
    }


    @Override
    public void propagate() {
        // update watched literals
        int i = wL.value();
        while (i < n && x[i].isFixed()) {
            if (x[i].isTrue()) {
                setActive(false);
                return;
            }
            i += 1;
        }
        wL.setValue(i);
        i = wR.value();
        while (i >= 0 && x[i].isFixed() && i >= wL.value()) {
            if (x[i].isTrue()) {
                setActive(false);
                return;
            }
            i -= 1;
        }
        wR.setValue(i);

        if (wL.value() > wR.value()) {
            throw INCONSISTENCY;
        } else if (wL.value() == wR.value()) { // only one unassigned var
            x[wL.value()].fix(true);
            setActive(false);
        } else {
            assert (wL.value() != wR.value());
            assert (!x[wL.value()].isFixed());
            assert (!x[wR.value()].isFixed());
            x[wL.value()].propagateOnFix(this);
            x[wR.value()].propagateOnFix(this);
        }
    }
}
