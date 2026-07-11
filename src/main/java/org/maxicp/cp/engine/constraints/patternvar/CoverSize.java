/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseBitSet;

/**
 * CoverSize global constraint over native Boolean item variables:
 * links the support of the itemset {@code {i : items[i] = 1}} to an integer variable
 * (Schaus, Aoga, Guns, CoverSize: a global constraint for frequency-based itemset
 * mining, CP 2017).
 * <p>
 * This is the native-variable (NV-GC) counterpart of the pattern-variable model
 * based on {@link org.maxicp.cp.engine.core.CPItemsetVar}: the reversible cover
 * is owned by this constraint rather than shared through a variable kernel. It
 * serves as experimental baseline; both use the same bitset algorithms.
 */
public class CoverSize extends AbstractCPConstraint {

    private final CPBoolVar[] items;
    private final CPIntVar support;
    private final StateSparseBitSet coverage;
    private final StateSparseBitSet.SupportBitSet[] columns;
    private final int[] unbound;
    private final StateInt nUnbound;

    /**
     * Creates a CoverSize constraint.
     *
     * @param items   one Boolean variable per item of the database, {@code items[i] = 1}
     *                iff item i belongs to the pattern; {@code items.length == db.nItems()}
     * @param support the integer variable representing the support of the pattern
     * @param db      the transaction database
     */
    public CoverSize(CPBoolVar[] items, CPIntVar support, TransactionDb db) {
        super(support.getSolver());
        if (items.length != db.nItems())
            throw new IllegalArgumentException("one Boolean variable per item is required");
        this.items = items;
        this.support = support;
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
        this.unbound = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            unbound[i] = i;
        }
        this.nUnbound = getSolver().getStateManager().makeStateInt(items.length);
    }

    @Override
    public void post() {
        for (CPBoolVar item : items) {
            if (!item.isFixed()) {
                item.propagateOnFix(this);
            }
        }
        support.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // intersect the coverage with the columns of the newly included items
        int nU = nUnbound.value();
        for (int k = nU - 1; k >= 0; k--) {
            int idx = unbound[k];
            if (items[idx].isFixed()) {
                nU = removeUnbound(k, nU);
                if (items[idx].isTrue()) {
                    coverage.intersect(columns[idx]);
                }
            }
        }

        // exclude items whose inclusion drops the coverage below the minimum support
        int minSupport = support.min();
        for (int k = nU - 1; k >= 0; k--) {
            int idx = unbound[k];
            if (coverage.countIntersection(columns[idx], minSupport) < minSupport) {
                nU = removeUnbound(k, nU);
                items[idx].fix(false);
            }
        }

        int supportUB = coverage.count();
        support.removeAbove(supportUB);
        support.removeBelow(coverage.countIntersectionAll(columns, unbound, nU));

        if (supportUB == support.min()) {
            // the support must stay at its current upper bound: every item whose
            // inclusion would reduce the coverage must be excluded
            for (int k = nU - 1; k >= 0; k--) {
                int idx = unbound[k];
                if (!coverage.isSubsetOf(columns[idx])) {
                    nU = removeUnbound(k, nU);
                    items[idx].fix(false);
                }
            }
        }
        nUnbound.setValue(nU);
    }

    private int removeUnbound(int k, int nU) {
        int last = nU - 1;
        int idx = unbound[k];
        unbound[k] = unbound[last];
        unbound[last] = idx;
        return last;
    }
}
