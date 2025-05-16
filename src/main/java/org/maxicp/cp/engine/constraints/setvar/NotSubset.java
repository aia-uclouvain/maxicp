package org.maxicp.cp.engine.constraints.setvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSetVar;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Constraint that enforces that the first set variable is not a subset of the second set variable.
 */
public class NotSubset extends AbstractCPConstraint {

    private CPSetVar set1;
    private CPSetVar set2;
    private int[] values;

    /**
     * Creates a constraint that enforces that set1 is not a subset of set2.
     *
     * @param set1 the first set variable
     * @param set2 the second set variable
     */
    public NotSubset(CPSetVar set1, CPSetVar set2) {
        super(set1.getSolver());
        this.set1 = set1;
        this.set2 = set2;
        values = new int[Math.max(set1.size(), set2.size())];
    }

    @Override
    public void post() {
        set1.propagateOnDomainChange(this);
        set2.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // if one value in set1 is excluded in set2, set1 is not a subset of set2
        // if only one value can prevent set1 to be a subset of set2, include it in set1 and exclude it from set2
        // values preventing set1 to be a subset of set2 are in I1 U P1 INTER E2 U P2

        int nPossibleExcluded = 0;
        int value = -1;
        int nIncluded = set1.fillIncluded(values);
        for (int i = 0; i < nIncluded; i++) {
            if (set2.isExcluded(values[i])) {
                setActive(false);
                return;
            }
            if (set2.isPossible(values[i])) {
                nPossibleExcluded++;
                value = values[i];
            }

        }

        int nPossible = set1.fillPossible(values);
        for (int i = 0; i < nPossible; i++) {
            if (!set2.isIncluded(values[i])) {
                nPossibleExcluded++;
                value = values[i];
            }
        }

        if (nPossibleExcluded == 1) {
            set1.include(value);
            set2.exclude(value);
            setActive(false);

        } else if (nPossibleExcluded == 0) { // if nothing can prevent set1 to be a subset of set2
            throw new InconsistencyException();
        }
    }


}
