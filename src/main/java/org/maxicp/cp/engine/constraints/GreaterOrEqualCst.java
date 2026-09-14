/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Greater or equal constraint between a variable and a constant
 */
public class GreaterOrEqualCst extends AbstractCPConstraint { // x >= v

    private final CPIntVar x;
    private final int v;

    public GreaterOrEqualCst(CPIntVar x, int v) {
        super(x.getSolver());
        this.x = x;
        this.v = v;
    }

    @Override
    public void post() {
        x.removeBelow(v);
    }

    public CPIntVar getX() {
        return x;
    }

    public int getV() {
        return v;
    }
}
