/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Less or equal constraint between two variables
 */
public class LessOrEqual extends AbstractCPConstraint { // x <= y

    private final CPIntVar x;
    private final CPIntVar y;

    /**
     * Creates a constraint enforcing {@code x <= y}.
     *
     * @param x the left-hand side variable
     * @param y the right-hand side variable
     */
    public LessOrEqual(CPIntVar x, CPIntVar y) {
        super(x.getSolver());
        this.x = x;
        this.y = y;
    }

    @Override
    public void post() {
        x.propagateOnBoundChange(this);
        y.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        x.removeAbove(y.max());
        y.removeBelow(x.min());
        if (x.max() <= y.min())
            setActive(false);
    }
}
