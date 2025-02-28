/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.core;


import org.maxicp.modeling.ModelProxy;
import org.maxicp.util.exception.IntOverFlowException;

import java.util.function.Consumer;

/**
 * A view on a variable of type {@code x+o}
 */
public class CPIntVarViewOffset implements CPIntVar {

    private final CPIntVar x;
    private final int o;

    public CPIntVarViewOffset(CPIntVar x, int offset) { // y = x + o
        if (0L + x.min() + offset <= (long) Integer.MIN_VALUE)
            throw new IntOverFlowException("consider applying a smaller offset as the min domain on this view is <= Integer.MIN _VALUE");
        if (0L + x.max() + offset >= (long) Integer.MAX_VALUE)
            throw new IntOverFlowException("consider applying a smaller offset as the max domain on this view is >= Integer.MAX _VALUE");
        this.x = x;
        this.o = offset;

    }

    @Override
    public CPSolver getSolver() {
        return x.getSolver();
    }

    @Override
    public void whenFixed(Runnable f) {
        x.whenFixed(f);
    }

    @Override
    public void whenBoundChange(Runnable f) {
        x.whenBoundChange(f);
    }

    @Override
    public void whenDomainChange(Runnable f) {
        x.whenDomainChange(f);
    }

    @Override
    public void whenDomainChange(Consumer<DeltaCPIntVar> f) {
        CPConstraint c = new CPConstraintClosureWithDelta(getSolver(),this,f);
        getSolver().post(c, false);
    }

    @Override
    public void propagateOnDomainChange(CPConstraint c) {
        x.propagateOnDomainChange(c);
    }

    @Override
    public void propagateOnFix(CPConstraint c) {
        x.propagateOnFix(c);
    }

    @Override
    public void propagateOnBoundChange(CPConstraint c) {
        x.propagateOnBoundChange(c);
    }

    @Override
    public int min() {
        return x.min() + o;
    }

    @Override
    public int max() {
        return x.max() + o;
    }

    @Override
    public int size() {
        return x.size();
    }

    @Override
    public int fillArray(int[] dest) {
        int s = x.fillArray(dest);
        for (int i = 0; i < s; i++) {
            dest[i] += o;
        }
        return s;
    }

    @Override
    public boolean isFixed() {
        return x.isFixed();
    }

    @Override
    public boolean contains(int v) {
        return x.contains(v - o);
    }

    @Override
    public void remove(int v) {
        x.remove(v - o);
    }

    @Override
    public void fix(int v) {
        x.fix(v - o);
    }

    @Override
    public void removeBelow(int v) {
        x.removeBelow(v - o);
    }

    @Override
    public void removeAbove(int v) {
        x.removeAbove(v - o);
    }

    @Override
    public int fillDeltaArray(int oldMin, int oldMax, int oldSize, int[] arr) {
        int s = x.fillDeltaArray(oldMin - o,oldMax - o,oldSize,arr);
        for (int i = 0; i < s; i++) {
            arr[i] += o;
        }
        return s;
    }

    @Override
    public DeltaCPIntVar delta(CPConstraint c) {
        DeltaCPIntVar delta = new DeltaCPIntVarImpl(this);
        c.registerDelta(delta);
        return delta;
    }

    @Override
    public String toString() {
        return show();
    }

    @Override
    public ModelProxy getModelProxy() {
        return getSolver().getModelProxy();
    }
}
