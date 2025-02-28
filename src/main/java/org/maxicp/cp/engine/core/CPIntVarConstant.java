package org.maxicp.cp.engine.core;

import org.maxicp.modeling.ModelProxy;

import java.util.function.Consumer;

import static org.maxicp.util.exception.InconsistencyException.INCONSISTENCY;

public class CPIntVarConstant implements CPIntVar{

    private final CPSolver cp;
    private final int value;

    public CPIntVarConstant(CPSolver cp, int value) {
        this.cp = cp;
        this.value = value;
    }

    @Override
    public CPSolver getSolver() {
        return cp;
    }

    @Override
    public void whenFixed(Runnable f) {

    }

    @Override
    public void whenBoundChange(Runnable f) {

    }

    @Override
    public void whenDomainChange(Runnable f) {

    }

    @Override
    public void whenDomainChange(Consumer<DeltaCPIntVar> f) {

    }

    @Override
    public void propagateOnDomainChange(CPConstraint c) {

    }

    @Override
    public void propagateOnFix(CPConstraint c) {

    }

    @Override
    public void propagateOnBoundChange(CPConstraint c) {

    }

    @Override
    public int min() {
        return value;
    }

    @Override
    public int max() {
        return value;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public int fillArray(int[] dest) {
        dest[0] = value;
        return 1;
    }

    @Override
    public boolean isFixed() {
        return true;
    }

    @Override
    public boolean contains(int v) {
        return v == value;
    }

    @Override
    public void remove(int v) {
        if (v == value)
            throw INCONSISTENCY;
    }

    @Override
    public void fix(int v) {
        if (v != value)
            throw INCONSISTENCY;
    }

    @Override
    public void removeBelow(int v) {
        if (v > value)
            throw INCONSISTENCY;
    }

    @Override
    public void removeAbove(int v) {
        if (v < value)
            throw INCONSISTENCY;
    }

    @Override
    public int fillDeltaArray(int oldMin, int oldMax, int oldSize, int[] dest) {
        return 0;
    }

    @Override
    public DeltaCPIntVar delta(CPConstraint c) {
        return null;
    }

    @Override
    public ModelProxy getModelProxy() {
        return getSolver().getModelProxy();
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
