/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.setvar.SetCard;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.concrete.ConcreteVar;
import org.maxicp.state.datastructures.StateStack;
import org.maxicp.state.datastructures.StateTriPartition;

import java.security.InvalidParameterException;

public class CPSetVarImpl implements CPSetVar {

    private CPSolver cp;
    private StateTriPartition domain;
    private CPIntVar card;
    private StateStack<CPConstraint> onDomain;

    /**
     * Creates a set variable with the elements {@code {0,...,n-1}}
     * as initial possible domain.
     *
     * @param cp the solver in which the variable is created
     * @param n  the number of values with {@code n >= 0}
     */
    public CPSetVarImpl(CPSolver cp, int n) {
        if (n < 1) throw new InvalidParameterException("at least one setValue in the domain");
        this.cp = cp;
        this.domain = new StateTriPartition(cp.getStateManager(), n);
        this.card = CPFactory.makeIntVar(cp, 0, n);
        this.onDomain = new StateStack<>(cp.getStateManager());
        cp.post(new SetCard(this, card));
    }

    public int size() {
        return domain.size();
    }

    public void exclude(int v) {
        if (domain.isPossible(v)) {
            domain.exclude(v);
            scheduleAll(onDomain);
        }
    }

    @Override
    public String toString() {
        return card.toString() + " " + domain.toString();
    }

    public CPIntVar card() {
        return card;
    }

    public void include(int v) {
        if (domain.isPossible(v)) {
            domain.include(v);
            scheduleAll(onDomain);
        }
    }

    public boolean isFixed() {
        return card.isFixed() && domain.nIncluded() == card.min();
    }

    protected void scheduleAll(StateStack<CPConstraint> constraints) {
        for (int i = 0; i < constraints.size(); i++) {
            cp.schedule(constraints.get(i));
        }
    }

    public void propagateOnDomainChange(CPConstraint c) {
        onDomain.push(c);
    }

    public CPSolver getSolver() {
        return cp;
    }

    @Override
    public ModelProxy getModelProxy() {
        return getSolver().getModelProxy();
    }

    public int nIncluded() {
        return domain.nIncluded();
    }

    public int nPossible() {
        return domain.nPossible();
    }

    public int nExcluded() {
        return domain.nExcluded();
    }

    public void includeAll() {
        if (domain.nPossible() > 0) {
            domain.includeAllPossible();
            card.fix(domain.nIncluded());
            scheduleAll(onDomain);
        }
    }

    public void excludeAll() {
        if (domain.nPossible() > 0) {
            domain.excludeAllPossible();
            card.fix(domain.nIncluded());
            scheduleAll(onDomain);
        }
    }

    public int fillPossible(int[] dest) {
        return domain.fillPossible(dest);
    }

    public int fillIncluded(int[] dest) {
        return domain.fillIncluded(dest);
    }

    public int fillExcluded(int[] dest) {
        return domain.fillExcluded(dest);
    }

    public boolean isPossible(int v) {
        return domain.isPossible(v);
    }

    public boolean isIncluded(int v) {
        return domain.isIncluded(v);
    }

    public boolean isExcluded(int v) {
        return domain.isExcluded(v);
    }
}
