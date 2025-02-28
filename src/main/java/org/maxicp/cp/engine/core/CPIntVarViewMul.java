/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.core;


import org.maxicp.modeling.ModelProxy;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.IntOverFlowException;

import java.util.function.Consumer;

/**
 * A view on a variable of type {@code a*x}
 */
public class CPIntVarViewMul implements CPIntVar {

    private final int a;
    private final CPIntVar x;

    public CPIntVarViewMul(CPIntVar x, int a) {
        if ((1L + x.min()) * a <= (long) Integer.MIN_VALUE)
            throw new IntOverFlowException("consider applying a smaller mul cte as the min domain on this view is <= Integer.MIN _VALUE");
        if ((1L + x.max()) * a >= (long) Integer.MAX_VALUE)
            throw new IntOverFlowException("consider applying a smaller mul cte as the max domain on this view is >= Integer.MAX _VALUE");
        assert (a > 0);
        this.a = a;
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
        if (a >= 0)
            return a * x.min();
        else return a * x.max();
    }

    @Override
    public int max() {
        if (a >= 0)
            return a * x.max();
        else return a * x.min();
    }

    @Override
    public int size() {
        return x.size();
    }

    @Override
    public int fillDeltaArray(int oldMin, int oldMax, int oldSize, int[] arr) {
        int s = x.fillDeltaArray(oldMin/a,oldMax/a,oldSize,arr);
        for (int i = 0; i < s; i++) {
            arr[i] *= a;
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
    public int fillArray(int[] dest) {
        int s = x.fillArray(dest);
        for (int i = 0; i < s; i++) {
            dest[i] *= a;
        }
        return s;
    }

    @Override
    public boolean isFixed() {
        return x.isFixed();
    }

    @Override
    public boolean contains(int v) {
        return (v % a != 0) ? false : x.contains(v / a);
    }

    @Override
    public void remove(int v) {
        if (v % a == 0) {
            x.remove(v / a);
        }
    }

    @Override
    public void fix(int v) {
        if (v % a == 0) {
            x.fix(v / a);
        } else {
            throw new InconsistencyException();
        }
    }

    @Override
    public void removeBelow(int v) {
        x.removeBelow(ceilDiv(v, a));
    }

    @Override
    public void removeAbove(int v) {
        x.removeAbove(floorDiv(v, a));
    }

    // Java's division always rounds to the integer closest to zero, but we need flooring/ceiling versions.
    private int floorDiv(int a, int b) {
        int q = a / b;
        return (a < 0 && q * b != a) ? q - 1 : q;
    }

    private int ceilDiv(int a, int b) {
        int q = a / b;
        return (a > 0 && q * b != a) ? q + 1 : q;
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
