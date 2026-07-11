/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseBitSet;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Closed frequent itemset mining as ONE fused global constraint over native
 * Boolean item variables: minimum support and closedness are enforced by a
 * single propagator owning a single reversible cover (port of ClosedFIM,
 * Schaus, Aoga, Guns, CP 2017).
 * <p>
 * Experimental baseline: this is the monolithic native-variable architecture
 * that the pattern variable decomposes into independent MinSupportItemset and
 * ClosedItemset constraints sharing one kernel.
 */
public class ClosedFimNv extends AbstractCPConstraint {

    private final CPBoolVar[] items;
    private final int theta;
    private final StateSparseBitSet coverage;
    private final StateSparseBitSet.SupportBitSet[] columns;
    private final int[] active;
    private final StateInt nActive;

    /**
     * Creates a fused closed frequent itemset constraint.
     *
     * @param items one Boolean variable per item, {@code items[i] = 1} iff item i
     *              belongs to the pattern; {@code items.length == db.nItems()}
     * @param theta the minimum number of covered transactions, {@code theta >= 1}
     * @param db    the transaction database
     */
    public ClosedFimNv(CPBoolVar[] items, int theta, TransactionDb db) {
        super(items[0].getSolver());
        if (items.length != db.nItems())
            throw new IllegalArgumentException("one Boolean variable per item is required");
        if (theta < 1)
            throw new IllegalArgumentException("minimum support must be at least 1, got " + theta);
        this.items = items;
        this.theta = theta;
        this.coverage = new StateSparseBitSet(getSolver().getStateManager(), db.nTrans());
        this.columns = new StateSparseBitSet.SupportBitSet[items.length];
        for (int i = 0; i < items.length; i++) {
            columns[i] = coverage.new SupportBitSet();
        }
        for (int t = 0; t < db.nTrans(); t++) {
            for (int item : db.transaction(t)) {
                columns[item].set(t);
            }
        }
        this.active = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            active[i] = i;
        }
        this.nActive = getSolver().getStateManager().makeStateInt(items.length);
    }

    @Override
    public void post() {
        for (CPBoolVar item : items) {
            if (!item.isFixed()) {
                item.propagateOnFix(this);
            }
        }
        propagate();
    }

    @Override
    public void propagate() {
        // intersect the coverage with the columns of the newly included items
        int nA = nActive.value();
        for (int k = nA - 1; k >= 0; k--) {
            int idx = active[k];
            if (items[idx].isTrue()) {
                coverage.intersect(columns[idx]);
                nA = removeActive(k, nA);
            }
        }
        if (coverage.count() < theta) {
            throw new InconsistencyException();
        }
        for (int k = nA - 1; k >= 0; k--) {
            int idx = active[k];
            if (items[idx].isFalse()) {
                // an excluded item subsuming the cover forbids every closed completion
                if (coverage.isSubsetOf(columns[idx])) {
                    throw new InconsistencyException();
                }
                // an infrequent excluded item can never subsume the cover later
                if (coverage.countIntersection(columns[idx], theta) < theta) {
                    nA = removeActive(k, nA);
                }
            } else if (coverage.countIntersection(columns[idx], theta) < theta) {
                nA = removeActive(k, nA);
                items[idx].fix(false);
            } else if (coverage.isSubsetOf(columns[idx])) {
                nA = removeActive(k, nA);
                items[idx].fix(true); // closure extension: the cover is unchanged
            }
        }
        nActive.setValue(nA);
    }

    private int removeActive(int k, int nA) {
        int last = nA - 1;
        int idx = active[k];
        active[k] = active[last];
        active[last] = idx;
        return last;
    }
}
