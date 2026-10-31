/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Element Constraint over an array of variables, modeling {@code array[y] = z}.
 */
public class Element1DVar extends AbstractCPConstraint {

    private final CPIntVar[] array;
    private final CPIntVar y;
    private final CPIntVar z;

    private final int[] yValues;
    private CPIntVar supMin;
    private CPIntVar supMax;
    private int zMin;
    private int zMax;

    /**
     * Creates an element constraint {@code array[y] = z} where {@code array} is an array of integer variables.
     *
     * @param array the array of variables to index
     * @param y     the index variable
     * @param z     the result variable
     */
    public Element1DVar(CPIntVar[] array, CPIntVar y, CPIntVar z) {
        super(y.getSolver());
        this.array = array;
        this.y = y;
        this.z = z;

        yValues = new int[y.size()];
    }

    @Override
    public void post() {
        y.removeBelow(0);
        y.removeAbove(array.length - 1);

        for (CPIntVar t : array) {
            t.propagateOnBoundChange(this);
        }
        y.propagateOnDomainChange(this);
        z.propagateOnBoundChange(this);

        propagate();
    }

    @Override
    public void propagate() {
        zMin = z.min();
        zMax = z.max();
        if (y.isFixed()) equalityPropagate();
        else {
            filterY();
            if (y.isFixed())
                equalityPropagate();
            else {
                z.removeBelow(supMin.min());
                z.removeAbove(supMax.max());
            }
        }

    }

    private void equalityPropagate() {
        int id = y.min();
        CPIntVar tVar = array[id];
        tVar.removeBelow(zMin);
        tVar.removeAbove(zMax);
        z.removeBelow(tVar.min());
        z.removeAbove(tVar.max());
    }

    private void filterY() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        int i = y.fillArray(yValues);
        while (i > 0) {
            i -= 1;
            int id = yValues[i];
            CPIntVar tVar = array[id];
            int tMin = tVar.min();
            int tMax = tVar.max();
            if (tMax < zMin || tMin > zMax) {
                y.remove(id);
            } else {
                if (tMin < min) {
                    min = tMin;
                    supMin = tVar;
                }
                if (tMax > max) {
                    max = tMax;
                    supMax = tVar;
                }
            }
        }

    }

}
