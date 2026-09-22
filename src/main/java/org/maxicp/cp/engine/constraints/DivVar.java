/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.constraints;


import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Integer Constraint x / y = z (all variables)
 * @author Pierre Schaus
 */
public class DivVar extends AbstractCPConstraint {

    private CPIntVar x, y, z;

    /**
     * x / y == z
     * @param x
     * @param y
     * @param z
     */
    public DivVar(CPIntVar x, CPIntVar y, CPIntVar z) {
        super(x.getSolver());
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void post() {
        x.propagateOnDomainChange(this);
        y.propagateOnDomainChange(this);
        z.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // Reaching a local fixpoint guarantees that every remaining value has support.
        boolean changed;
        do {
            changed = false;
            changed |= filterX();
            changed |= filterY();
            changed |= filterZ();
        } while (changed);
    }

    private boolean filterX() {
        int[] vals = new int[x.size()];
        int n = x.fillArray(vals);
        boolean changed = false;

        for (int i = 0; i < n; i++) {
            int a = vals[i];
            if (!hasSupportForX(a)) {
                x.remove(a);
                changed = true;
            }
        }
        return changed;
    }

    private boolean filterY() {
        int[] vals = new int[y.size()];
        int n = y.fillArray(vals);
        boolean changed = false;

        for (int i = 0; i < n; i++) {
            int b = vals[i];
            if (!hasSupportForY(b)) {
                y.remove(b);
                changed = true;
            }
        }
        return changed;
    }

    private boolean filterZ() {
        int[] vals = new int[z.size()];
        int n = z.fillArray(vals);
        boolean changed = false;

        for (int i = 0; i < n; i++) {
            int c = vals[i];
            if (!hasSupportForZ(c)) {
                z.remove(c);
                changed = true;
            }
        }
        return changed;
    }

    private boolean hasSupportForX(int a) {
        int[] yVals = new int[y.size()];
        int ny = y.fillArray(yVals);

        for (int i = 0; i < ny; i++) {
            int b = yVals[i];
            if (b != 0 && z.contains(a / b)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSupportForY(int b) {
        if (b == 0) {
            return false;
        }

        int[] xVals = new int[x.size()];
        int nx = x.fillArray(xVals);

        for (int i = 0; i < nx; i++) {
            int a = xVals[i];
            if (z.contains(a / b)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSupportForZ(int c) {
        int[] xVals = new int[x.size()];
        int nx = x.fillArray(xVals);
        int[] yVals = new int[y.size()];
        int ny = y.fillArray(yVals);

        for (int i = 0; i < nx; i++) {
            int a = xVals[i];
            for (int j = 0; j < ny; j++) {
                int b = yVals[j];
                if (b != 0 && a / b == c) {
                    return true;
                }
            }
        }
        return false;
    }
}
