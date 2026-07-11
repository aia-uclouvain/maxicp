/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPItemsetVar;

/**
 * Constraint that links the support of an itemset pattern variable to an
 * integer variable, following the CoverSize filtering (Schaus, Aoga, Guns, CP 2017).
 * <p>
 * The support of the pattern lies between the cover of all its required and
 * possible items (lower bound) and the cover of its required items (upper bound).
 * Possible items whose inclusion would drop the cover below the minimum support
 * are excluded.
 */
public class ItemsetSupport extends AbstractCPConstraint {

    private final CPItemsetVar pattern;
    private final CPIntVar support;
    private final int[] items;

    /**
     * Creates a constraint linking an itemset pattern variable to its support.
     *
     * @param pattern the itemset pattern variable
     * @param support the integer variable representing the support
     */
    public ItemsetSupport(CPItemsetVar pattern, CPIntVar support) {
        super(pattern.getSolver());
        this.pattern = pattern;
        this.support = support;
        this.items = new int[pattern.nItems()];
    }

    @Override
    public void post() {
        pattern.propagateOnDomainChange(this);
        support.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        int minSupport = support.min();
        int n = pattern.fillPossible(items);
        for (int k = 0; k < n; k++) {
            if (pattern.supportAfter(items[k], minSupport) < minSupport) {
                pattern.exclude(items[k]);
            }
        }
        int coverSize = pattern.coverSize();
        support.removeAbove(coverSize);
        support.removeBelow(pattern.minSupport());
        if (coverSize == support.min()) {
            // the support must stay at its current upper bound: every item whose
            // inclusion would reduce the cover must be excluded
            n = pattern.fillPossible(items);
            for (int k = 0; k < n; k++) {
                if (!pattern.coverSubsetOf(items[k])) {
                    pattern.exclude(items[k]);
                }
            }
        }
    }
}
