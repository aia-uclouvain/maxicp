/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.state.State;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Sum Constraint
 */
public class Sum extends AbstractCPConstraint {

    private int[] free;
    private StateInt nFrees;
    private State<Long> sumFixed;
    private CPIntVar[] x;
    private int[] min, max;
    private int n;

    /**
     * Creates a sum constraint.
     * <p> This constraint holds iff
     * {@code x[0]+x[1]+...+x[x.length-1] == y}.
     *
     * @param x the non-empty left hand side of the sum
     * @param y the right hand side of the sum
     */
    public Sum(CPIntVar[] x, CPIntVar y) {
        this(x.length + 1, x[0].getSolver());
        System.arraycopy(x, 0, this.x, 0, x.length);
        this.x[x.length] = CPFactory.minus(y);
    }

    /**
     * Creates a sum constraint.
     * <p> This constraint holds iff
     * {@code x[0]+x[1]+...+x[x.length-1] == y}.
     *
     * @param x the non empty left hand side of the sum
     * @param y the right hand side of the sum
     */
    public Sum(CPIntVar[] x, int y) {
        this(x.length + 1, x[0].getSolver());
        System.arraycopy(x, 0, this.x, 0, x.length);
        this.x[x.length] = CPFactory.makeIntVar(getSolver(), -y, -y);
    }

    /**
     * Creates a sum constraint.
     * <p> This constraint holds iff
     * {@code x[0]+x[1]+...+x[x.length-1] == 0}.
     *
     * @param x the non empty set of variables that should sum to zero
     */
    public Sum(CPIntVar[] x) {
        this(x.length, x[0].getSolver());
        this.x = x;
    }

    private Sum(int n, CPSolver solver) {
        super(solver);
        this.x = new CPIntVar[n];
        this.n = n;
        min = new int[n];
        max = new int[n];
        nFrees = getSolver().getStateManager().makeStateInt(n);
        sumFixed = getSolver().getStateManager().makeStateRef(0L);
        free = IntStream.range(0, n).toArray();
    }

    @Override
    public void post() {
        for (CPIntVar var : x)
            var.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // Filter the unfixed vars and update the partial sum
        int nU = nFrees.value();
        long sumMin = sumFixed.value(), sumMax = sumFixed.value();
        for (int i = nU - 1; i >= 0; i--) {
            int idx = free[i];
            min[idx] = x[idx].min();
            max[idx] = x[idx].max();
            sumMin += min[idx]; // Update partial sum
            sumMax += max[idx];
            if (x[idx].isFixed()) {
                sumFixed.setValue(sumFixed.value() + x[idx].min());
                free[i] = free[nU - 1]; // Swap the variables
                free[nU - 1] = idx;
                nU--;
            }
        }
        nFrees.setValue(nU);
        if (sumMin > 0 || sumMax < 0) {
            throw new InconsistencyException();
        }

        for (int i = nU - 1; i >= 0; i--) {
            int idx = free[i];
            x[idx].removeAbove(-((int) (sumMin - min[idx])));
            x[idx].removeBelow(-((int) (sumMax - max[idx])));
        }
    }
}
