/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;


import org.maxicp.state.StateManager;
import org.maxicp.state.datastructures.StateLazySparseSet;


/**
 * Implementation of a domain with a sparse-set
 */
public class SparseSetDomain implements IntDomain {
    // private StateSparseSet domain;
    private StateLazySparseSet domain;


    public SparseSetDomain(StateManager sm, int min, int max) {
        // domain = new StateSparseSet(sm, max - min + 1, min);
        domain = new StateLazySparseSet(sm, max - min + 1, min);
    }

    @Override
    public int fillArray(int[] dest) {
        return domain.fillArray(dest);
    }

    @Override
    public int min() {
        return domain.min();
    }

    @Override
    public int max() {
        return domain.max();
    }

    @Override
    public int size() {
        return domain.size();
    }

    @Override
    public boolean contains(int v) {
        return domain.contains(v);
    }

    @Override
    public boolean isSingleton() {
        return domain.size() == 1;
    }

    @Override
    public void remove(int v, IntDomainListener l) {
        if (domain.contains(v)) {
            boolean maxChanged = max() == v;
            boolean minChanged = min() == v;
            domain.remove(v);
            if (domain.size() == 0)
                l.empty();
            l.change();
            if (maxChanged) l.changeMax();
            if (minChanged) l.changeMin();
            if (domain.size() == 1) l.bind();
        }
    }

    @Override
    public void removeAllBut(int v, IntDomainListener l) {
        if (domain.contains(v)) {
            if (domain.size() != 1) {
                boolean maxChanged = max() != v;
                boolean minChanged = min() != v;
                domain.removeAllBut(v);
                if (domain.size() == 0)
                    l.empty();
                l.bind();
                l.change();
                if (maxChanged) l.changeMax();
                if (minChanged) l.changeMin();
            }
        } else {
            domain.removeAll();
            l.empty();
        }
    }

    @Override
    public void removeBelow(int value, IntDomainListener l) {
        if (domain.min() < value) {
            domain.removeBelow(value);
            switch (domain.size()) {
                case 0:
                    l.empty();
                    break;
                case 1:
                    l.bind();
                default:
                    l.changeMin();
                    l.change();
                    break;
            }
        }
    }

    @Override
    public void removeAbove(int value, IntDomainListener l) {
        if (domain.max() > value) {
            domain.removeAbove(value);
            switch (domain.size()) {
                case 0:
                    l.empty();
                    break;
                case 1:
                    l.bind();
                default:
                    l.changeMax();
                    l.change();
                    break;
            }
        }
    }

    @Override
    public String toString() {
        if (size() == 0) return "{}";
        StringBuilder b = new StringBuilder();
        b.append("{");
        for (int i = min(); i < max(); i++)
            if (contains((i)))
                b.append(i).append(',');
        b.append(max());
        b.append("}");
        return b.toString();
    }

    @Override
    public int fillDeltaArray(int oldMin, int oldMax, int oldSize, int [] arr) {
        return domain.fillDeltaArray(oldMin, oldMax, oldSize, arr);
    }


}
