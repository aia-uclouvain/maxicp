/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

public class Equal extends AbstractCPConstraint {
    private final CPIntVar x, y;


    /**
     * Creates a constraint such
     * that {@code x = y}
     *
     * @param x the left member
     * @param y the right memer
     * @see CPFactory#eq(CPIntVar, CPIntVar)
     */
    public Equal(CPIntVar x, CPIntVar y) { // x == y
        super(x.getSolver());
        this.x = x;
        this.y = y;
    }

    @Override
    public void post() {
        if (y.isFixed())
            x.fix(y.min());
        else if (x.isFixed())
            y.fix(x.min());
        else {
            boundsIntersect();
            int[] domVal = new int[Math.max(x.size(), y.size())];
            pruneEquals(y, x, domVal);
            pruneEquals(x, y, domVal);
            x.whenDomainChange(() -> {
                boundsIntersect();
                pruneEquals(x, y, domVal);
            });
            y.whenDomainChange(() -> {
                boundsIntersect();
                pruneEquals(y, x, domVal);
            });
        }
    }

    // dom consistent filtering in the direction from -> to
    // every value of "to" has a support in "from"
    private void pruneEquals(CPIntVar from, CPIntVar to, int[] domVal) {
        // dump the domain of to into domVal
        int nVal = to.fillArray(domVal);
        for (int k = 0; k < nVal; k++)
            if (!from.contains(domVal[k]))
                to.remove(domVal[k]);
    }

    // make sure bound of variables are the same
    private void boundsIntersect() {
        int newMin = Math.max(x.min(), y.min());
        int newMax = Math.min(x.max(), y.max());
        x.removeBelow(newMin);
        x.removeAbove(newMax);
        y.removeBelow(newMin);
        y.removeAbove(newMax);
    }
}
