/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;


/**
 * Reified equality constraint
 * @see CPFactory#isEq(CPIntVar, int)
 */
public class IsEqual extends AbstractCPConstraint { // b <=> x == v

    private final CPBoolVar b;
    private final CPIntVar x;
    private final int v;

    /**
     * Returns a boolean variable representing
     * whether one variable is equal to the given constant.
     * @param x the variable
     * @param v the constant
     * @param b the boolean variable that is set to true
     *          if and only if x takes the value v
     * @see CPFactory#isEq(CPIntVar, int)
     */
    public IsEqual(CPBoolVar b, CPIntVar x, int v) {
        super(b.getSolver());
        this.b = b;
        this.x = x;
        this.v = v;
    }

    @Override
    public void post() {
        propagate();
        if (isActive()) {
            x.propagateOnDomainChange(this);
            b.propagateOnFix(this);
        }
    }

    @Override
    public void propagate() {
        if (b.isTrue()) {
            x.fix(v);
            setActive(false);
        } else if (b.isFalse()) {
            x.remove(v);
            setActive(false);
        } else if (!x.contains(v)) {
            b.fix(false);
            setActive(false);
        } else if (x.isFixed()) {
            b.fix(true);
            setActive(false);
        }
    }
}
