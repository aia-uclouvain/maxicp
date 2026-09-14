/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Equal constraint between a variable and a constant
 */
public class EqualCst extends AbstractCPConstraint {

    private final CPIntVar x;
    private final int v;

    /**
     * Creates a constraint such that {@code x = v}
     *
     * @param x the variable
     * @param v the value
     * @see CPFactory#eq(CPIntVar, int)
     */
    public EqualCst(CPIntVar x, int v) {
        super(x.getSolver());
        this.x = x;
        this.v = v;
    }

    @Override
    public void post() {
        x.fix(v);
    }

    public CPIntVar getX() {
        return x;
    }

    public int getV() {
        return v;
    }
}
