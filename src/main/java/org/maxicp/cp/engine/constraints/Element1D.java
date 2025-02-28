/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.DeltaCPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;


/**
 *
 * Element Constraint modeling {@code array[y] = z}
 *
 */
public class Element1D extends AbstractCPConstraint {

    private final int[] t;

    private final Integer[] sortedPerm;
    private final StateInt low;
    private final StateInt up;

    private final CPIntVar y;
    private final CPIntVar z;


    /**
     * Creates an element constraint {@code array[y] = z}
     * with a domain consistent filtering
     *
     * @param array the array to index
     * @param y the index variable
     * @param z the result variable
     */
    public Element1D(int[] array, CPIntVar y, CPIntVar z) {
        super(y.getSolver());
        this.t = array;

        sortedPerm = new Integer[t.length];
        for (int i = 0; i < t.length; i++) {
            sortedPerm[i] = i;
        }
        Arrays.sort(sortedPerm, Comparator.comparingInt(i -> t[i]));

        StateManager sm = getSolver().getStateManager();
        low = sm.makeStateInt(0);
        up = sm.makeStateInt(t.length - 1);

        this.y = y;
        this.z = z;
    }

    @Override
    public void post() {

        y.removeBelow(0);
        y.removeAbove(t.length - 1);
        z.removeBelow(t[sortedPerm[0]]);
        z.removeAbove(t[sortedPerm[t.length-1]]);

        y.propagateOnDomainChange(this);
        z.propagateOnBoundChange(this);
        propagate();

    }

    @Override
    public void propagate() {

        int l = low.value(), u = up.value();
        int zMin = z.min(), zMax = z.max();

        while (t[sortedPerm[l]] < zMin || !y.contains(sortedPerm[l])) {
            y.remove(sortedPerm[l]);
            l++;
            if (l > u) {
                throw new InconsistencyException();
            }
        }
        while (t[sortedPerm[u]] > zMax || !y.contains(sortedPerm[u])) {
            y.remove(sortedPerm[u]);
            u--;
            if (l > u) {
                throw new InconsistencyException();
            }
        }
        z.removeBelow(t[sortedPerm[l]]);
        z.removeAbove(t[sortedPerm[u]]);
        low.setValue(l);
        up.setValue(u);

    }
}
