/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;


import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.DeltaCPIntVar;
import org.maxicp.state.State;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Element1DDC constraint
 * @author pschaus
 */
public class Element1DDC
        extends AbstractCPConstraint {
    private final int[] t;
    private final CPIntVar y;
    private DeltaCPIntVar yDelta;
    private DeltaCPIntVar zDelta;
    private int[] yValues;
    private int[] zValues;
    private final CPIntVar z;

    // for each value in Dom(z), the number of supports it has in Dom(y)
    int offZ;
    private StateInt[] supportCounter;

    public Element1DDC(int[] array, CPIntVar y, CPIntVar z) {
        super(y.getSolver());
        this.t = array;
        this.y = y;
        this.z = z;
    }

    @Override
    public void post() {

        y.removeBelow(0);
        y.removeAbove(this.t.length - 1);

        z.removeAbove(Arrays.stream(t).max().getAsInt());
        z.removeBelow(Arrays.stream(t).min().getAsInt());

        y.propagateOnDomainChange(this);
        z.propagateOnDomainChange(this);

        yDelta = y.delta(this);
        yValues = new int[y.size()];
        zDelta = z.delta(this);
        zValues = new int[z.size()];

        // if a value t[i] not present in D(z), i must be removed from D(y)
        int sizeY = y.fillArray(yValues);
        for (int i = 0; i < sizeY; i++) {
            int val = yValues[i];
            if (!z.contains(t[val])) {
                y.remove(val);
            }
        }

        // init supports of z values
        this.supportCounter = new StateInt[z.size()];
        offZ = z.min();
        for (int i = 0; i < z.size(); i++) {
            supportCounter[i] = this.getSolver().getStateManager().makeStateInt(0);
        }

        for (int i = 0; i < this.t.length; i++) {
            if (y.contains(i)) {
                StateInt counter = this.supportCounter[t[i] - offZ];
                counter.increment();
            }
        }

        // if a value from D(z) is not present in t, it must be removed from D(z)
        int sizeZ = z.fillArray(zValues);
        for (int i = 0; i < sizeZ; i++) {
            int val = zValues[i];
            if (supportCounter[val - offZ].value() == 0) {
                z.remove(val);
            }
        }

        this.propagate();
    }

    @Override
    public void propagate() {

        int val;
        int i;
        int size;

        if (zDelta.size() > 0) {
            size = zDelta.fillArray(zValues);
            for (i = 0; i < size; i++) {
                // we could optimize this to maintain incrementally
                // a struct with the indices(v) = {i in D(y) | T[i] = v}
                val = this.zValues[i];
                int sizeY = y.fillArray(yValues);
                for (int j = 0; j < sizeY; j++) {
                    int yVal = yValues[j];
                    if (t[yVal] == val ) {
                        y.remove(yVal);
                    }
                }
            }
        }

        if (yDelta.size() > 0) {
            size = yDelta.fillArray(this.yValues);
            for (i = 0; i < size; i++) {
                val = yValues[i];
                StateInt counter = supportCounter[t[val]-offZ];
                if (counter.decrement() == 0) {
                    z.remove(t[val]);
                }
            }
        }
    }

}
