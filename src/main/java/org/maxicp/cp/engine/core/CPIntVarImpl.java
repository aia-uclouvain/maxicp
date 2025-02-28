/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.state.datastructures.StateStack;
import org.maxicp.util.exception.InconsistencyException;

import java.security.InvalidParameterException;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Implementation of a variable
 * with a {@link SparseSetDomain}.
 */
public class CPIntVarImpl implements CPIntVar {

    private CPSolver cp;
    private IntDomain domain;
    private StateStack<CPConstraint> onDomain;
    private StateStack<CPConstraint> onBind;
    private StateStack<CPConstraint> onBounds;

    private DomainListener domListener = new DomainListener() {
        @Override
        public void empty() {
            throw new InconsistencyException();
            // throw InconsistencyException.INCONSISTENCY; // Integer Vars cannot be empty
        }

        @Override
        public void bind() {
            scheduleAll(onBind);
        }

        @Override
        public void change() {
            scheduleAll(onDomain);
        }

        @Override
        public void changeMin() {
            scheduleAll(onBounds);
        }

        @Override
        public void changeMax() {
            scheduleAll(onBounds);
        }
    };

    /**
     * Creates a variable with the elements {@code {0,...,n-1}}
     * as initial domain.
     *
     * @param cp the solver in which the variable is created
     * @param n  the number of values with {@code n > 0}
     */
    public CPIntVarImpl(CPSolver cp, int n) {
        this(cp, 0, n - 1);
    }

    /**
     * Creates a variable with the elements {@code {min,...,max}}
     * as initial domain.
     *
     * @param cp the solver in which the variable is created
     * @param min the minimum value of the domain
     * @param max the maximum value of the domain with {@code max >= min}
     */
    public CPIntVarImpl(CPSolver cp, int min, int max) {
        if (min == Integer.MIN_VALUE || max == Integer.MAX_VALUE) throw new InvalidParameterException("consider reducing the domains, Integer.MIN _VALUE and Integer.MAX_VALUE not allowed");
        if (min > max) throw new InvalidParameterException("at least one setValue in the domain");
        this.cp = cp;
        domain = new SparseSetDomain(cp.getStateManager(), min, max);
        onDomain = new StateStack<>(cp.getStateManager());
        onBind = new StateStack<>(cp.getStateManager());
        onBounds = new StateStack<>(cp.getStateManager());
    }



    /**
     * Creates a variable with a given set of values as initial domain.
     *
     * @param cp the solver in which the variable is created
     * @param values the initial values in the domain, it must be nonempty
     */
    public CPIntVarImpl(CPSolver cp, Set<Integer> values) {
        this(cp, values.stream().min(Integer::compare).get(), values.stream().max(Integer::compare).get());
        if (values.isEmpty()) throw new InvalidParameterException("at least one setValue in the domain");
        for (int i = min(); i < max(); i++) {
            if (!values.contains(i)) {
                try {
                    this.remove(i);
                } catch (InconsistencyException e) {
                }
            }
        }
    }

    @Override
    public CPSolver getSolver() {
        return cp;
    }

    @Override
    public boolean isFixed() {
        return domain.size() == 1;
    }

    @Override
    public String toString() {
        return show();
    }

    @Override
    public void whenFixed(Runnable f) {
        onBind.push(constraintClosure(f));
    }

    @Override
    public void whenBoundChange(Runnable f) {
        onBounds.push(constraintClosure(f));
    }

    @Override
    public void whenDomainChange(Runnable f) {
        onDomain.push(constraintClosure(f));
    }
    @Override
    public void whenDomainChange(Consumer<DeltaCPIntVar> f) {
        CPConstraint c = new CPConstraintClosureWithDelta(cp,this,f);
        getSolver().post(c, false);
    }

    private CPConstraint constraintClosure(Runnable f) {
        CPConstraint c = new CPConstraintClosure(cp, f);
        getSolver().post(c, false);
        return c;
    }

    @Override
    public void propagateOnDomainChange(CPConstraint c) {
        onDomain.push(c);
    }

    @Override
    public void propagateOnFix(CPConstraint c) {
        onBind.push(c);
    }

    @Override
    public void propagateOnBoundChange(CPConstraint c) {
        onBounds.push(c);
    }


    protected void scheduleAll(StateStack<CPConstraint> constraints) {
        for (int i = 0; i < constraints.size(); i++)
            cp.schedule(constraints.get(i));
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
    public int fillArray(int[] dest) {
        return domain.fillArray(dest);
    }

    @Override
    public boolean contains(int v) {
        return domain.contains(v);
    }

    @Override
    public void remove(int v) {
        domain.remove(v, domListener);
    }

    @Override
    public void fix(int v) {
        domain.removeAllBut(v, domListener);
    }

    @Override
    public void removeBelow(int v) {
        domain.removeBelow(v, domListener);
    }

    @Override
    public void removeAbove(int v) {
        domain.removeAbove(v, domListener);
    }

    @Override
    public int fillDeltaArray(int oldMin, int oldMax, int oldSize, int [] arr) {
        return domain.fillDeltaArray(oldMin,oldMax,oldSize,arr);
    }

    @Override
    public DeltaCPIntVar delta(CPConstraint c) {
        DeltaCPIntVar delta = new DeltaCPIntVarImpl(this);
        c.registerDelta(delta);
        return delta;
    }

    @Override
    public ModelProxy getModelProxy() {
        return getSolver().getModelProxy();
    }
}
