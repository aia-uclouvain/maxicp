/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.core;

import org.maxicp.modeling.ModelProxy;

import java.util.function.Consumer;


/**
 * A view on a variable of type {@code -x}
 */
public class CPIntVarViewOpposite implements CPIntVar {

    private final CPIntVar x;

    public CPIntVarViewOpposite(CPIntVar x) {
        this.x = x;
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
        return -x.max();
    }

    @Override
    public int max() {
        return -x.min();
    }

    @Override
    public int size() {
        return x.size();
    }

    @Override
    public int fillArray(int[] dest) {
        int s = x.fillArray(dest);
        for (int i = 0; i < s; i++) {
            dest[i] = -dest[i];
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
    public int fillDeltaArray(int oldMin, int oldMax, int oldSize, int[] arr) {
        int s = x.fillDeltaArray(-oldMax,-oldMin,oldSize,arr);
        for (int i = 0; i < s; i++) {
            arr[i] = - arr[i];
        }
        return s;
    }

    @Override
    public boolean isFixed() {
        return x.isFixed();
    }

    @Override
    public boolean contains(int v) {
        return x.contains(-v);
    }

    @Override
    public void remove(int v) {
        x.remove(-v);
    }

    @Override
    public void fix(int v) {
        x.fix(-v);
    }

    @Override
    public void removeBelow(int v) {
        x.removeAbove(-v);
    }

    @Override
    public void removeAbove(int v) {
        x.removeBelow(-v);
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
