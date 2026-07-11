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
 * Closedness constraint over native Boolean item variables, maintaining its OWN
 * reversible cover: no strict superset of the pattern has the same cover.
 * <p>
 * Experimental baseline for the duplicated-state architecture: posted next to
 * {@link CoverSize}, each of the two propagators maintains a private copy of
 * the same cover (two intersections per included item), which is exactly the
 * duplication the pattern-variable kernel removes.
 */
public class ClosedNv extends AbstractCPConstraint {

    private final CPBoolVar[] items;
    private final StateSparseBitSet coverage;
    private final StateSparseBitSet.SupportBitSet[] columns;
    private final int[] unprocessed;
    private final StateInt nUnprocessed;

    /**
     * Creates a closedness constraint owning its cover.
     *
     * @param items one Boolean variable per item, {@code items[i] = 1} iff item i
     *              belongs to the pattern; {@code items.length == db.nItems()}
     * @param db    the transaction database
     */
    public ClosedNv(CPBoolVar[] items, TransactionDb db) {
        super(items[0].getSolver());
        if (items.length != db.nItems())
            throw new IllegalArgumentException("one Boolean variable per item is required");
        this.items = items;
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
        this.unprocessed = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            unprocessed[i] = i;
        }
        this.nUnprocessed = getSolver().getStateManager().makeStateInt(items.length);
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
        // intersect the private coverage with the columns of the newly included items
        int nU = nUnprocessed.value();
        for (int k = nU - 1; k >= 0; k--) {
            int idx = unprocessed[k];
            if (items[idx].isTrue()) {
                coverage.intersect(columns[idx]);
                nU = removeUnprocessed(k, nU);
            }
        }
        nUnprocessed.setValue(nU);
        // excluded items may never subsume the cover; possible subsuming items are forced
        for (int k = nU - 1; k >= 0; k--) {
            int idx = unprocessed[k];
            if (coverage.isSubsetOf(columns[idx])) {
                if (items[idx].isFalse()) {
                    throw new InconsistencyException();
                }
                items[idx].fix(true); // the cover is unchanged
            }
        }
    }

    private int removeUnprocessed(int k, int nU) {
        int last = nU - 1;
        int idx = unprocessed[k];
        unprocessed[k] = unprocessed[last];
        unprocessed[last] = idx;
        return last;
    }
}
