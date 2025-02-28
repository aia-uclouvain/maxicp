/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

import java.util.Arrays;


/**
 *
 * Sorted constraint, x is sorted in y according to permutation o
 * @author pschaus
 */
public class Sorted extends AbstractCPConstraint {

    // Filtering described in
    // - Zhou, J. (1997). A permutation-based approach for solving the job-shop problem. Constraints, 2(2), 185-213.
    // - Mehlhorn, K., & Thiel, S. (2000) Faster algorithms for bound-consistency of the sortedness and the alldifferent constraint. CP

    int n;
    CPIntVar[] x;
    CPIntVar [] y;
    CPIntVar [] o;

    int [] min;
    int [] max;

    /**
     * x is sorted in y according to permutation o
     * y[0] <= y[1] <= ... <= y[n-1]
     * x[i] = y[o[i]] for all i
     *
     * @param x a set of n variables
     * @param o a permutation of 0..n-1, so that x[i] = y[o[i]]
     * @param y a set of n variables, y[i] is the ith smallest value of x
     */
    public Sorted(CPIntVar[] x, CPIntVar[] o, CPIntVar[] y) {
        super(x[0].getSolver());
        this. n = x.length;
        // check all size are n and throw  illigal argument
        if (o.length != n) throw new IllegalArgumentException("size !=" + n);
        if (y.length != n) throw new IllegalArgumentException("size !=" + n);
        this.x = x;
        this.o = o;
        this.y = y;
        this.min = new int[n];
        this.max = new int[n];
    }


    @Override
    public void post() {
        for (int i = 0; i < n; i++) {
            o[i].removeAbove(n-1);
            o[i].removeBelow(0);
        }

        getSolver().post(new AllDifferentDC(o));


        for (int i = 0; i < n; i++) {
            getSolver().post(new Element1DVar(y, o[i], x[i]));
        }
        for (int i = 0; i < n-1; i++) {
            getSolver().post(new LessOrEqual(y[i], y[i+1]));
        }

        // redundant constraints
        for (int i = 0; i < n; i++) {
            int i_ = i;
            // number of x_k's <= y_i is at least i-1
            CPIntVar [] b1 = CPFactory.makeIntVarArray(n, k-> CPFactory.isLessOrEqual(x[k],y[i_]));
            getSolver().post(CPFactory.largerOrEqual(CPFactory.sum(b1),i-1));
            // number of x_k's >= y_i is at least n-i
            CPIntVar [] b2 = CPFactory.makeIntVarArray(n, k-> CPFactory.isLargerOrEqual(x[k],y[i_]));
            getSolver().post(CPFactory.largerOrEqual(CPFactory.sum(b2),n-i));
        }

        for (int i = 0; i < n; i++) {
            x[i].propagateOnBoundChange(this);
            y[i].propagateOnBoundChange(this);
            o[i].propagateOnBoundChange(this);
        }
        propagate();
    }

    @Override
    public void propagate() {
        for (int i = 0; i < n; i++) {
            min[i] = x[i].min();
            max[i] = x[i].max();
        }
        Arrays.sort(min);
        Arrays.sort(max);
        for (int i = 0; i < n; i++) {
            y[i].removeBelow(min[i]);
            y[i].removeAbove(max[i]);
        }


    }


}
