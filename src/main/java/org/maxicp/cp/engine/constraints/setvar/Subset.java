package org.maxicp.cp.engine.constraints.setvar;

import org.maxicp.cp.engine.constraints.LessOrEqual;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSetVar;

/**
 * Constraint that enforces that one set variable is a subset of another set variable.
 */
public class Subset extends AbstractCPConstraint {

    private CPSetVar set1;
    private CPSetVar set2;
    private int[] values;

    /**
     * Creates a constraint that enforces that set1 is a subset of set2.
     * @param set1 the first set variable
     * @param set2 the second set variable
     */
    public Subset(CPSetVar set1, CPSetVar set2) {
        super(set1.getSolver());
        this.set1 = set1;
        this.set2 = set2;
        values = new int[Math.max(set1.size(), set2.size())];
    }

    @Override
    public void post() {
        set1.propagateOnDomainChange(this);
        set2.propagateOnDomainChange(this);
        this.getSolver().post(new LessOrEqual(set1.card(), set2.card()));
        propagate();
    }

    @Override
    public void propagate() {
        // remove from set1 all values that are not in set2
        int nExcluded = set2.fillExcluded(values);
        for (int i = 0; i < nExcluded; i++) {
            set1.exclude(values[i]);
        }
        // include all values of set1 in set2
        int nIncluded = set1.fillIncluded(values);
        for (int i = 0; i < nIncluded; i++) {
            set2.include(values[i]);
        }
        if(set1.isFixed() || set2.isFixed()) {
            setActive(false);
        }

    }
}
