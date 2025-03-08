/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

import static org.maxicp.cp.CPFactory.le;
import static org.maxicp.cp.CPFactory.plus;

/**
 * Reified is less or equal constraint {@code b <=> x <= y}.
 */
public class IsLessOrEqualVar extends AbstractCPConstraint {

    private final CPBoolVar b;
    private final CPIntVar x;
    private final CPIntVar y;

    private final CPConstraint lEqC;
    private final CPConstraint grC;

    /**
     * Creates a reified is less or equal constraint {@code b <=> x <= y}.
     * @param b the truth value that will be set to true if {@code x <= y}, false otherwise
     * @param x left hand side of less or equal operator
     * @param y right hand side of less or equal operator
     */
    public IsLessOrEqualVar(CPBoolVar b, CPIntVar x, CPIntVar y) {
        super(x.getSolver());
        this.b = b;
        this.x = x;
        this.y = y;
        lEqC = CPFactory.le(x, y);
        grC = CPFactory.le(plus(y, 1), x);
    }

    @Override
    public void post() {
        x.propagateOnBoundChange(this);
        y.propagateOnBoundChange(this);
        b.propagateOnFix(this);
        propagate();
    }

    @Override
    public void propagate() {
        if (b.isTrue()) {
            getSolver().post(lEqC, false);
            setActive(false);
        } else if (b.isFalse()) {
            getSolver().post(grC, false);
            setActive(false);
        } else {
            if (x.max() <= y.min()) {
                b.fix(1);
                setActive(false);
            } else if (x.min() > y.max()) {
                b.fix(0);
                setActive(false);
            }
        }
    }
}
