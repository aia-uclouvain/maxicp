/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.setvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSetVar;
import org.maxicp.util.exception.InconsistencyException;

public class SetCard extends AbstractCPConstraint {

    CPSetVar set;
    CPIntVar card;

    /**
     * Creates a constraint that links a set variable to the cardinality of the set.
     * @param set  the set variable
     * @param card the integer variable representing the cardinality
     */
    public SetCard(CPSetVar set, CPIntVar card) {
        super(set.getSolver());
        this.set = set;
        this.card = card;
    }

    @Override
    public void post() {
        set.propagateOnDomainChange(this);
        card.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        card.removeBelow(set.nIncluded());
        card.removeAbove(set.nPossible() + set.nIncluded());

        if (card.min() > set.nIncluded() + set.nPossible()) throw new InconsistencyException();
        if (card.max() < set.nIncluded()) throw new InconsistencyException();
        if (card.min() == set.nIncluded() + set.nPossible()) {
            set.includeAll();
            card.removeAbove(card.min());
        } else if (card.max() == set.nIncluded()) {
            set.excludeAll();
            card.removeBelow(card.max());
        }
    }
}