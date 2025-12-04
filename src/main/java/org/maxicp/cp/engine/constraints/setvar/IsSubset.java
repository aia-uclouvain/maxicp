/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.setvar;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.IsLessOrEqual;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPConstraint;
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
    private CPConstraint subsetConstraint;
    private CPConstraint notSubsetConstraint;

    /**
     * Creates a constraint that enforces the boolean variable b to be true
     * if and only set1 is a subset (not necessarily strict) of set2 .
     *
     * @param b    the boolean variable
     * @param set1 the first set than can be included in the second set
     * @param set2 the second set
     */
    public IsSubset(CPBoolVar b, CPSetVar set1, CPSetVar set2) {
        super(b.getSolver());
        this.set1 = set1;
        this.set2 = set2;
        this.b = b;
        values = new int[Math.max(set1.size(), set2.size())];
        subsetConstraint = new Subset(set1, set2);
        notSubsetConstraint = new NotSubset(set1, set2);

    }

    @Override
    public void post() {
        set1.propagateOnDomainChange(this);
        set2.propagateOnDomainChange(this);
        set1.card().propagateOnBoundChange(this);
        set2.card().propagateOnBoundChange(this);
        b.propagateOnFix(this);
        propagate();
    }


    /**
     * Detect if set1 is not a subset of set2 by
     * comparing the cardinalities of the two sets.
     * checking if at least one included value of set1 is excluded in set2
     *
     * @return true if set1 is not a subset of set2
     */
    private boolean detectNotSubset() {
        if (set1.card().min() > set2.card().max()) {
            return true;
        }

        int nIncluded = set1.fillIncluded(values);
        for (int j = 0; j < nIncluded; j++) {
            if (set2.isExcluded(values[j])) {
                return true;
            }
        }
        return false;

    }

    /**
     * Detect if set1 is a subset of set2 by
     * comparing the cardinalities of the two sets.
     * comparing the number of included and possible values of set1 and set2
     * and checking if all included or possible values of set1 are included in set2
     *
     * @return true if set1 is a subset of set2
     */
    private boolean detectSubset() {
        if (set1.card().min() > set2.card().max() || set1.nPossible() + set1.nIncluded() > set2.nIncluded()) {
            return false;
        }
        int nIncluded = set1.fillIncluded(values);
        for (int j = 0; j < nIncluded; j++) {
            if (!set2.isIncluded(values[j])) {
                return false;
            }
        }
        int nPossible = set1.fillPossible(values);
        for (int j = 0; j < nPossible; j++) {
            if (!set2.isIncluded(values[j])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void propagate() {
        if (detectNotSubset()) {
            b.fix(false);
        }
        if (detectSubset()) {
            b.fix(true);
        }

        if (b.isTrue()) {
            setActive(false);
            getSolver().post(subsetConstraint, false);
        } else if (b.isFalse()) {
            setActive(false);
            getSolver().post(notSubsetConstraint, false);
        }
    }
}
