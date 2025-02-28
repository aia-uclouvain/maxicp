/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

import static org.maxicp.cp.CPFactory.plus;

/**
 * Not Equal constraint between two variables
 */
public class NotEqual extends AbstractCPConstraint {
    private final CPIntVar x, y;

    /**
     * Creates a constraint such
     * that {@code x != y + v}
     *
     * @param x the left member
     * @param y the right memer
     * @param v the offset value on y
     * @see CPFactory#notEqual(CPIntVar, CPIntVar, int)
     */
    public NotEqual(CPIntVar x, CPIntVar y, int v) { // x != y + v
        this(x, v == 0 ? y : plus(y, v));
    }

    /**
     * Creates a constraint such
     * that {@code x != y}
     *
     * @param x the left member
     * @param y the right memer
     * @see CPFactory#notEqual(CPIntVar, CPIntVar)
     */
    public NotEqual(CPIntVar x, CPIntVar y) { // x != y
        super(x.getSolver());
        this.x = x;
        this.y = y;
    }

    @Override
    public void post() {
        if (y.isFixed())
            x.remove(y.min());
        else if (x.isFixed())
            y.remove(x.min());
        else {
            x.propagateOnFix(this);
            y.propagateOnFix(this);
        }
    }

    @Override
    public void propagate() {
        if (y.isFixed())
            x.remove(y.min());
        else
            y.remove(x.min());
        setActive(false);
    }
}
