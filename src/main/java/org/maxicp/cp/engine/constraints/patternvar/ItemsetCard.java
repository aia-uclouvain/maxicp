/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPItemsetVar;

/**
 * Constraint that links the size (number of items) of an itemset pattern
 * variable to an integer variable.
 */
public class ItemsetCard extends AbstractCPConstraint {

    private final CPItemsetVar pattern;
    private final CPIntVar size;

    /**
     * Creates a constraint linking an itemset pattern variable to its size.
     *
     * @param pattern the itemset pattern variable
     * @param size    the integer variable representing the number of items
     */
    public ItemsetCard(CPItemsetVar pattern, CPIntVar size) {
        super(pattern.getSolver());
        this.pattern = pattern;
        this.size = size;
    }

    @Override
    public void post() {
        pattern.propagateOnDomainChange(this);
        size.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        size.removeBelow(pattern.nRequired());
        size.removeAbove(pattern.nRequired() + pattern.nPossible());
        if (size.min() == pattern.nRequired() + pattern.nPossible()) {
            pattern.requireAll();
            size.removeAbove(size.min());
        } else if (size.max() == pattern.nRequired()) {
            pattern.excludeAll();
            size.removeBelow(size.max());
        }
    }
}
