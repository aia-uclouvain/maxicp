package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.DeltaCPIntVar;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * InversePerm is a constraint that enforces that one permutation is the inverse of another.
 * This means that if x[i] = j, then y[j] = i for all i and j in the domain of the variables.
 */
public class InversePerm extends AbstractCPConstraint {

    private CPIntVar[] x;
    private CPIntVar[] y;
    private int[] values;
    private DeltaCPIntVar[] deltaX;
    private DeltaCPIntVar[] deltaY;

    /**
     * Creates a constraint that enforces that one permutation is the inverse of another.
     * This means that if x[i] = j, then y[j] = i for all i and j in the domain of the variables.
     *
     * @param x the first array of integer variables representing the permutation
     * @param y the second array of integer variables representing the inverse permutation
     */
    public InversePerm(CPIntVar[] x, CPIntVar[] y) {
        super(x[0].getSolver());
        assertEquals(x.length, y.length);
        this.x = x;
        this.y = y;
        this.values = new int[x.length];
        this.deltaX = new DeltaCPIntVar[x.length];
        this.deltaY = new DeltaCPIntVar[y.length];
        for (int i = 0; i < x.length; i++) {
            deltaX[i] = x[i].delta(this);
            deltaY[i] = y[i].delta(this);
        }
    }

    @Override
    public void post() {
        getSolver().post(new AllDifferentDC(x));
        getSolver().post(new AllDifferentDC(y));
        for (int i = 0; i < x.length; i++) {
            x[i].removeBelow(0);
            x[i].removeAbove(x.length - 1);
            x[i].propagateOnDomainChange(this);
            y[i].propagateOnDomainChange(this);
        }
        // first filtering
        for (int i = 0; i < x.length; i++) {
            if (x[i].isFixed()) {
                y[x[i].min()].fix(i);
            } else {
                int nX = x[i].fillArray(values);
                for (int j = 0; j < nX; j++) {
                    if (!y[values[j]].contains(i)) {
                        x[i].remove(values[j]);
                    }
                }
            }
            if (y[i].isFixed()) {
                x[y[i].min()].fix(i);
            } else {
                int nY = y[i].fillArray(values);
                for (int j = 0; j < nY; j++) {
                    if (!x[values[j]].contains(i)) {
                        y[i].remove(values[j]);
                    }
                }
            }
        }

        propagate();
    }

    @Override
    public void propagate() {
        // filtering based on deltas
        for (int i = 0; i < x.length; i++) {
            if (deltaX[i].changed()) {
                int nX = deltaX[i].fillArray(values);
                for (int j = 0; j < nX; j++) {
                    y[values[j]].remove(i);
                }
            }
            if (deltaY[i].changed()) {
                int nY = deltaY[i].fillArray(values);
                for (int j = 0; j < nY; j++) {
                    x[values[j]].remove(i);
                }
            }
        }
    }
}
