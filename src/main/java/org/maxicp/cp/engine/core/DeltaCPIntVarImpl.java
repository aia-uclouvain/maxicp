/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.state.State;

import java.util.ConcurrentModificationException;
import java.util.Iterator;

public class DeltaCPIntVarImpl implements DeltaCPIntVar {

    private State<DeltaState> deltaState;
    private CPIntVar x;

    private int [] iteratorValues;
    private int iteratorSize = -1;
    private int iteratorTimeStamp;

    public DeltaCPIntVarImpl(CPIntVar x) {
        this.deltaState = x.getSolver().getStateManager().makeStateRef(null);
        this.x = x;
        iteratorValues = new int[x.size()];
    }

    @Override
    public CPIntVar variable() {
        return x;
    }

    @Override
    public int oldMin() {
        return deltaState.value().oldMin();
    }

    @Override
    public int oldMax() {
        return deltaState.value().oldMax();
    }

    @Override
    public int oldSize() {
        return deltaState.value().oldSize();
    }

    @Override
    public boolean changed() {
        return oldSize() != x.size();
    }

    @Override
    public boolean minChanged() {
        return oldMin() != x.min();
    }

    @Override
    public boolean maxChanged() {
        return oldMax() != x.max();
    }

    @Override
    public int size() {
        return oldSize()-x.size();
    }

    @Override
    public Iterator<Integer> iterator() {
        iteratorSize = fillArray(iteratorValues);
        return new Iterator<Integer>() {
            final int ts = iteratorTimeStamp;
            int i = 0;
            @Override
            public boolean hasNext() {
                if (ts == iteratorTimeStamp) {
                    return i < iteratorSize;
                } else {
                    throw new ConcurrentModificationException("outdated delta iterator");
                }
            }

            @Override
            public Integer next() {
                return iteratorValues[i++];
            }
        };
    }

    @Override
    public int fillArray(int[] values) {
        DeltaState ds = deltaState.value();
        return x.fillDeltaArray(ds.oldMin(),ds.oldMax(),ds.oldSize(), values);
    }

    @Override
    public void update() {
        iteratorTimeStamp++;
        deltaState.setValue(new DeltaState(x.size(),x.min(),x.max()));
    }
}

record DeltaState (int oldSize, int oldMin, int oldMax){}

