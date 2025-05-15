/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.ModelProxy;

import java.util.HashSet;

/**
 * A fixed set variable that contains a specific set of values.
 */
public class CPFixedSetVar implements CPSetVar {

    HashSet<Integer> set;
    CPIntVar card;
    CPSolver cp;

    /**
     * Creates a fixed set variable with the given values.
     *
     * @param cp     the solver in which the variable is created
     * @param values the values of the set
     */
    public CPFixedSetVar(CPSolver cp, int[] values) {
        this.set = new HashSet<>();
        for (int i : values) {
            set.add(i);
        }
        this.card = new CPIntVarConstant(cp, set.size());
        this.cp = cp;
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public CPIntVar card() {
        return card;
    }

    @Override
    public int fillPossible(int[] dest) {
        return 0;
    }

    @Override
    public int fillIncluded(int[] dest) {
        int counter = 0;
        for (Integer i : set) {
            dest[counter++] = i;
        }
        return set.size();
    }

    @Override
    public int fillExcluded(int[] dest) {
        return 0;
    }

    @Override
    public boolean isPossible(int v) {
        return false;
    }

    @Override
    public boolean isIncluded(int v) {
        return set.contains(v);
    }

    @Override
    public boolean isExcluded(int v) {
        return !set.contains(v);
    }

    @Override
    public boolean isFixed() {
        return true;
    }

    @Override
    public int nIncluded() {
        return set.size();
    }

    @Override
    public int nPossible() {
        return 0;
    }

    @Override
    public int nExcluded() {
        return 0;
    }

    @Override
    public CPSolver getSolver() {
        return cp;
    }

    @Override
    public ModelProxy getModelProxy() {
        return getSolver().getModelProxy();
    }

    @Override
    public void exclude(int v) {
    }

    @Override
    public void include(int v) {
    }

    @Override
    public void propagateOnDomainChange(CPConstraint c) {
    }

    @Override
    public void includeAll() {
    }

    @Override
    public void excludeAll() {
    }


}
