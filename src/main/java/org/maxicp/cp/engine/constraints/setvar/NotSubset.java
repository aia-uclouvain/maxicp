package org.maxicp.cp.engine.constraints.setvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSetVar;
import org.maxicp.util.exception.InconsistencyException;

public class NotSubset extends AbstractCPConstraint {

    private CPSetVar set1;
    private CPSetVar set2;
    private int[] values;

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


        int nIncluded = set1.fillIncluded(values);
        for (int i = 0; i < nIncluded; i++) {
            if (set2.isExcluded(values[i])) {
                setActive(false);
                return;
            }
        }

        // if only one value can be added in set 1 to prevent being subset, include it
        int nPossibleExcluded = 0;
        int valueExcluded = -1;
        int nPossible = set1.fillPossible(values);
        for (int i = 0; i < nPossible; i++) {
            if (set2.isExcluded(values[i])) {
                nPossibleExcluded++;
                valueExcluded = values[i];
            }
        }

        if (nPossibleExcluded == 1) {
            set1.include(valueExcluded);
            setActive(false);
        } else if (nPossibleExcluded == 0) {
            throw new InconsistencyException();
        }

    }


}
