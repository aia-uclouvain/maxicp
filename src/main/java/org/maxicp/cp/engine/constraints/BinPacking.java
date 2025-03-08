/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */
package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;

import java.util.Arrays;

/**
 * The {@code BinPacking} constraint ensures that a set of items with given weights
 * are packed into bins such that each bin's total weight respects a given capacity constraint.
 *
 * <p>This constraint is commonly used in Constraint Programming (CP) models
 * to solve bin-packing problems, where a set of weighted items must be assigned
 * to bins while respecting load capacities.</p>
 * @author pschaus
 */
public class BinPacking extends AbstractCPConstraint {

    final CPIntVar [] x;
    final int [] w;
    final CPIntVar [] load;

    /**
     * Constructs a {@code BinPacking} constraint that ensures a set of weighted items
     * are assigned to bins such that the total weight in each bin matches its specified load.
     *
     * <p>Each item has a predefined weight and must be placed in exactly one bin. The
     * constraint enforces that the sum of item weights assigned to each bin equals the
     * corresponding load variable.</p>
     *
     * @param x an array of {@link CPIntVar} variables where {@code x[i]} represents
     *          the bin index assigned to item {@code i}.
     * @param w an array of integers representing the weights of the items. The weight
     *          of item {@code i} is {@code w[i]}.
     * @param load an array of {@link CPIntVar} variables where {@code load[b]} represents
     *             the total weight of bin {@code b}, which must match the sum of weights
     *             of items assigned to that bin.
     */
    public BinPacking(CPIntVar [] x, final int [] w, CPIntVar [] load) {
        super(x[0].getSolver());
        if (x.length != w.length) {
            throw new IllegalArgumentException("x and load must have the same length");
        }
        for (int i = 0; i < x.length; i++) {
            if (w[i] < 0) {
                throw new IllegalArgumentException("weights must be positive");
            }
        }
        this.x = x;
        this.w = w;
        this.load = load;
    }

    @Override
    public void post() {
        for (int i = 0; i < x.length; i++) {
            x[i].removeAbove(load.length - 1);
            x[i].removeBelow(0);
        }
        for (int j = 0; j < load.length; j++) {
            load[j].removeAbove(Arrays.stream(w).sum());
            load[j].removeBelow(0);
        }
        // bin packing constraint
        for (int j = 0; j < load.length; j++) {
            CPBoolVar [] b = new CPBoolVar[x.length];
            for (int i = 0; i < x.length; i++) {
                b[i] = CPFactory.isEq(x[i], j);
            }
            getSolver().post(new BinaryKnapsack(b, w, load[j]));
        }
        getSolver().post(new Sum(load,Arrays.stream(w).sum()));
    }
}
