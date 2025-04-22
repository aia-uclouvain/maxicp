/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.setvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPSetVarImpl;
import org.maxicp.util.exception.InconsistencyException;

public class IsSubset extends AbstractCPConstraint {
    CPSetVarImpl set1;
    CPSetVarImpl set2;
    CPBoolVar b;
    int[] values;

    /**
     * Creates a constraint that enforces the boolean variable b to be true if set1 is a subset of set2.
     * if set1 == set2, b is true
     * @param b    the boolean variable
     * @param set1 the first set than can be included in the second set
     * @param set2 the second set
     */
    public IsSubset(CPBoolVar b, CPSetVarImpl set1, CPSetVarImpl set2) {
        super(b.getSolver());
        this.set1 = set1;
        this.set2 = set2;
        this.b = b;
        values = new int[Math.max(set1.size(),set2.size())];
    }

    public void post() {
        set1.propagateOnDomainChange(this);
        set2.propagateOnDomainChange(this);
        b.propagateOnFix(this);
        propagate();
    }

    public void canBeSubset() {
        int nIncluded = set1.fillIncluded(values);
        for (int j = 0; j < nIncluded; j++) {
            if (set2.isExcluded(values[j])) {
                b.fix(false);
                return;
            }
        }
    }

    private boolean isSubSet() {
        int nIncluded = set1.fillIncluded(values);
        for (int j = 0; j < nIncluded; j++) {
            if (!set2.isIncluded(values[j])) {
                return false;
            }
        }
        return true;
    }

    public void propagate() {

        canBeSubset();
        if (b.isTrue()) {
            // exclus de set1 tous les exclus de set2
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
