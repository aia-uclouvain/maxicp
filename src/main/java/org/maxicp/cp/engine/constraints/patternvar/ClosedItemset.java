/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Closedness constraint over an itemset pattern variable: no strict superset of
 * the pattern has the same cover.
 * <p>
 * The filtering follows the ClosedFIM/CoverClosure propagation:
 * - a possible item whose tidset is a superset of the current cover belongs to
 *   every closed completion, so it is required (closure extension);
 * - an excluded item whose tidset is a superset of the current cover subsumes
 *   every completion (adding it never changes the cover), so the search node fails.
 * <p>
 * Note that closedness is stated with respect to all supersets in the item
 * universe; combining it with structural constraints that forbid items changes
 * the semantics to closure within the constrained language.
 */
public class ClosedItemset extends AbstractCPConstraint {

    private final CPItemsetVar pattern;
    private final int[] items;

    /**
     * Creates a closedness constraint.
     *
     * @param pattern the itemset pattern variable
     */
    public ClosedItemset(CPItemsetVar pattern) {
        super(pattern.getSolver());
        this.pattern = pattern;
        this.items = new int[pattern.nItems()];
    }

    @Override
    public void post() {
        pattern.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // requiring a closure item leaves the cover unchanged, so a single scan is enough
        int n = pattern.fillPossible(items);
        for (int k = 0; k < n; k++) {
            if (pattern.coverSubsetOf(items[k])) {
                pattern.require(items[k]);
            }
        }
        n = pattern.fillExcluded(items);
        for (int k = 0; k < n; k++) {
            if (pattern.coverSubsetOf(items[k])) {
                throw new InconsistencyException();
            }
        }
    }
}
