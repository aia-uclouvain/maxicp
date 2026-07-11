/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Minimum support constraint over an itemset pattern variable:
 * every itemset of the domain must be contained in at least theta transactions.
 * <p>
 * The filtering follows the CoverSize/FIM propagation (Schaus, Aoga, Guns, CP 2017):
 * fail when the cover of the required items is smaller than theta, and exclude
 * every possible item whose extension support is smaller than theta. By
 * anti-monotonicity of the support this establishes extension consistency.
 */
public class MinSupportItemset extends AbstractCPConstraint {

    private final CPItemsetVar pattern;
    private final int theta;
    private final int[] items;

    /**
     * Creates a minimum support constraint.
     *
     * @param pattern the itemset pattern variable
     * @param theta   the minimum number of transactions covered, with {@code theta >= 1}
     */
    public MinSupportItemset(CPItemsetVar pattern, int theta) {
        super(pattern.getSolver());
        if (theta < 1) throw new IllegalArgumentException("minimum support must be at least 1, got " + theta);
        this.pattern = pattern;
        this.theta = theta;
        this.items = new int[pattern.nItems()];
    }

    @Override
    public void post() {
        pattern.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        if (pattern.coverSize() < theta) {
            throw new InconsistencyException();
        }
        int n = pattern.fillPossible(items);
        for (int k = 0; k < n; k++) {
            if (pattern.supportAfter(items[k], theta) < theta) {
                pattern.exclude(items[k]);
            }
        }
    }
}
