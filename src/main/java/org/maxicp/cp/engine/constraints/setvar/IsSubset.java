/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.setvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPSetVar;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Constraint that enforces a boolean variable is
 * true if one set is a subset of another set.
 */
public class IsSubset extends AbstractCPConstraint {

    private CPSetVar set1;
    private CPSetVar set2;
    private CPBoolVar b;
    private int[] values; // array to iterate

    /**
     * Creates a constraint that enforces the boolean variable b to be true
     * if and only set1 is a subset (not necessarily strict) of set2 .
     * @param b    the boolean variable
     * @param set1 the first set than can be included in the second set
     * @param set2 the second set
     */
    public IsSubset(CPBoolVar b, CPSetVar set1, CPSetVar set2) {
        super(b.getSolver());
        this.set1 = set1;
        this.set2 = set2;
        this.b = b;
        values = new int[Math.max(set1.size(),set2.size())];
    }

    @Override
    public void post() {
        set1.propagateOnDomainChange(this);
        set2.propagateOnDomainChange(this);
        b.propagateOnFix(this);
        propagate();
    }

    /**
     * Check if set1 can still possibly be a subset of set2.
     */
    private boolean canBeSubset() {
        int nIncluded = set1.fillIncluded(values);
        for (int j = 0; j < nIncluded; j++) {
            if (set2.isExcluded(values[j])) {
                return false;
            }
        }
        return true;
    }

    // BAD name
    private boolean isSubSet() {
        int nIncluded = set1.fillIncluded(values);
        for (int j = 0; j < nIncluded; j++) {
            if (!set2.isIncluded(values[j])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void propagate() {
        if (!canBeSubset()) {
            b.fix(false);
        }
        if (b.isTrue()) {
            // excluded from set2 must also be excluded from set1
            int nExcluded = set2.fillExcluded(values);
            for (int j = 0; j < nExcluded; j++) {
                set1.exclude(values[j]);
            }
        } else if (b.isFalse()) {
            // if inclusion and only one possible not in set2, include possible in set1
            if (isSubSet()) {
                if (set1.isFixed()) {
                    throw new InconsistencyException();
                }
                int notIncludedCounter = 0;
                int notIncluded = -1;
                int nPossible = set1.fillPossible(values);
                for (int j = 0; j < nPossible; j++) {
                    if (!set2.isIncluded(values[j])) {
                        notIncludedCounter++;
                        notIncluded = values[j];
                    }
                }
                if (notIncludedCounter == 1) {
                    set1.include(notIncluded);
                    set2.exclude(notIncluded);
                }
            }

        } else {
            if (set1.isFixed() && isSubSet()) {
                // if set1 is fixed, and set1 included in set2 then true and deactivate
                b.fix(true);
                setActive(false);
            }
        }

    }
}
