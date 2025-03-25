/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Constraint x * b = y where b is a boolean variable
 */
public class Mul extends AbstractCPConstraint {

    private final CPIntVar x;
    private final CPBoolVar b;
    private final CPIntVar y;

    /**
     * Creates the constraint x * b = y
     *
     * @param x the variable on which the maximum is to be found
     * @param y the variable that is equal to the maximum on x
     */
    public Mul(CPIntVar x, CPBoolVar b, CPIntVar y) {
        super(x.getSolver());
        this.x = x;
        this.b = b;
        this.y = y;
    }


    @Override
    public void post() {
        x.propagateOnBoundChange(this);
        b.propagateOnFix(this);
        y.propagateOnBoundChange(this);
        propagate();
    }


    @Override
    public void propagate() {
        if (x.max() > 0) {
            y.removeAbove(x.max());
        }
        if (x.min() < 0) {
            y.removeBelow(x.min());
        }


        if (b.isTrue()) {
            y.removeAbove(x.max());
            y.removeBelow(x.min());
        }
        if (b.isFalse()) {
            y.fix(0);
        }
        if (y.min() > 0 || y.max() < 0) {
            b.fix(1);
            x.removeAbove(y.max());
            x.removeBelow(y.min());
        }
        if (y.isFixed() && y.min() == 0 && !x.contains(0)) {
            b.fix(0);
        }
    }
}
