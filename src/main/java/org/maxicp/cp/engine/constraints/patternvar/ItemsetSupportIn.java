/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseBitSet;

/**
 * Constraint that links the support of an itemset pattern variable measured in
 * ANOTHER transaction database (typically a class-specific sub-database) to an
 * integer variable. Used for discriminative mining, where the class supports
 * of one pattern feed a scoring constraint such as {@link ZDC}.
 * <p>
 * The reversible cover over the class database is owned by this constraint
 * (the variable kernel only maintains the cover over its own database); the
 * filtering is the CoverSize one, expressed against the pattern refinements.
 */
public class ItemsetSupportIn extends AbstractCPConstraint {

    private final CPItemsetVar pattern;
    private final CPIntVar support;
    private final StateSparseBitSet cover;
    private final StateSparseBitSet.SupportBitSet[] columns;
    private final int[] active;
    private final StateInt nActive;

    /**
     * Creates a class-support channeling constraint.
     *
     * @param pattern the itemset pattern variable
     * @param classDb the database in which the support is measured; its item
     *                universe must not exceed the one of the pattern variable
     * @param support the integer variable representing the support of the
     *                pattern in classDb
     */
    public ItemsetSupportIn(CPItemsetVar pattern, TransactionDb classDb, CPIntVar support) {
        super(pattern.getSolver());
        if (classDb.nItems() > pattern.nItems())
            throw new IllegalArgumentException("the class database uses items unknown to the pattern variable");
        if (classDb.nTrans() < 1)
            throw new IllegalArgumentException("at least one transaction in the class database");
        this.pattern = pattern;
        this.support = support;
        this.cover = new StateSparseBitSet(getSolver().getStateManager(), classDb.nTrans());
        this.columns = new StateSparseBitSet.SupportBitSet[pattern.nItems()];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = cover.new SupportBitSet();
        }
        for (int t = 0; t < classDb.nTrans(); t++) {
            for (int item : classDb.transaction(t)) {
                columns[item].set(t);
            }
        }
        this.active = new int[pattern.nItems()];
        for (int i = 0; i < active.length; i++) {
            active[i] = i;
        }
        this.nActive = getSolver().getStateManager().makeStateInt(active.length);
    }

    @Override
    public void post() {
        pattern.propagateOnDomainChange(this);
        support.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // intersect the class cover with the columns of the newly required items;
        // after this loop the active items are exactly the possible ones
        int nA = nActive.value();
        for (int k = nA - 1; k >= 0; k--) {
            int idx = active[k];
            if (pattern.isRequired(idx)) {
                cover.intersect(columns[idx]);
                nA = removeActive(k, nA);
            } else if (pattern.isExcluded(idx)) {
                nA = removeActive(k, nA);
            }
        }

        // exclude items whose inclusion drops the class cover below the minimum support
        int minSupport = support.min();
        for (int k = nA - 1; k >= 0; k--) {
            int idx = active[k];
            if (cover.countIntersection(columns[idx], minSupport) < minSupport) {
                nA = removeActive(k, nA);
                pattern.exclude(idx);
            }
        }

        int supportUB = cover.count();
        support.removeAbove(supportUB);
        support.removeBelow(cover.countIntersectionAll(columns, active, nA));

        if (supportUB == support.min()) {
            for (int k = nA - 1; k >= 0; k--) {
                int idx = active[k];
                if (!cover.isSubsetOf(columns[idx])) {
                    nA = removeActive(k, nA);
                    pattern.exclude(idx);
                }
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
