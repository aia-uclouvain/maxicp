/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp;

import org.maxicp.cp.modeling.ModelProxyWithCP;
import org.maxicp.modeling.*;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.modeling.symbolic.*;
import org.maxicp.search.BestFirstSearch;
import org.maxicp.search.ConcurrentDFSearch;
import org.maxicp.search.DFSearch;
import org.maxicp.util.Ints;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.maxicp.Constants.HORIZON;

/**
 * A class that allows to create symbolic models
 */
public class ModelDispatcher implements AutoCloseable, ModelProxyWithCP, ModelProxyInstantiator, ModelProxy {
    private Model initialModel;
    private ThreadLocal<Model> currentModel;

    public ModelDispatcher() {
        initialModel = SymbolicModel.emptyModel(this);
        currentModel = new ThreadLocal<>();
        currentModel.set(initialModel);
    }

    /**
     * @return the current model
     */
    public Model getModel() {
        return currentModel.get();
    }

    /**
     * Set the current model to m. m should have this base model as origin.
     * @param m
     */
    public <T extends Model> T setModel(T m) {
        if (m != null && !m.getModelProxy().equals(this))
            throw new RuntimeException("Model being assigned to this ModelProxy does not originate from here");
        currentModel.set(m);
        return m;
    }

    /**
     * Shortcut for baseModel.getModel().getConstraints();
     * @return an iterable with all the constraints in the current model
     */
    public Iterable<Constraint> getConstraints() {
        return getModel().getConstraints();
    }

    /**
     * Create an array of n IntVars with domain between 0 and domSize-1, inclusive.
     * @param n size of the array, number of IntVars
     * @param domSize size of the domains. Domains are [0, domsize-1]
     */
    public IntVar[] intVarArray(int n, int domSize) {
        IntVar[] out = new IntVar[n];
        for(int i = 0; i < n; i++)
            out[i] = new IntVarRangeImpl(this, 0, domSize-1);
        return out;
    }

    public IntExpression[] intVarArray(int n, Function<Integer, IntExpression> body) {
        IntExpression[] t = new IntExpression[n];
        for (int i = 0; i < n; i++)
            t[i] = body.apply(i);
        return t;
    }

    public IntVar intVar(int min, int max) {
        return new IntVarRangeImpl(this, min, max);
    }

    public IntVar intVar(String id, int min, int max) {
        return new IntVarRangeImpl(this, id, min, max);
    }

    public IntVar intVar(int[] values) {
        return new IntVarSetImpl(this, Set.copyOf(Ints.asList(values)));
    }

    public IntVar intVar(String id, int[] values) {
        return new IntVarSetImpl(this, id, Set.copyOf(Ints.asList(values)));
    }

    public IntVar constant(int value) {
        return intVar(value, value);
    }

    public IntervalVar intervalVar(int startMin, int startMax, int endMin, int endMax, int lengthMin, int lengthMax, boolean isPresent) {
        return new IntervalVarImpl(this, startMin, startMax, endMin, endMax, lengthMin, lengthMax, isPresent);
    }

    public IntervalVar intervalVar(boolean isPresent) {
        return intervalVar(0, HORIZON,0, HORIZON, 0, HORIZON, isPresent);
    }

    public IntervalVar intervalVar(int duration, boolean isPresent) {
        return intervalVar(0, HORIZON,0, HORIZON, duration, duration, isPresent);
    }

    public IntervalVar intervalVar(int startMin, int endMax, int duration, boolean isPresent) {
        return intervalVar(startMin, endMax - duration, startMin + duration, endMax, duration, duration, isPresent);
    }

    public IntervalVar[] intervalVarArray(int n, boolean present) {
        IntervalVar[] out = new IntervalVar[n];
        for (int i = 0 ; i < n ; i++) {
            out[i] = intervalVar(present);
        }
        return out;
    }

    public IntervalVar[] intervalVarArray(int n, Function<Integer, IntervalVar> body) {
        IntervalVar[] out = new IntervalVar[n];
        for (int i = 0 ; i < n ; i++)
            out[i] = body.apply(i);
        return out;
    }

    public BoolVar[] boolVarArray(int n) {
        BoolVar[] out = new BoolVar[n];
        for(int i = 0; i < n; i++)
            out[i] = new BoolVarImpl(this);
        return out;
    }

    public BoolVar boolVar() {
        return new BoolVarImpl(this);
    }

    public SeqVar seqVar(int nNode, int begin, int end) {
        return new SeqVarImpl(this, nNode, begin, end);
    }

    @Override
    public void close() throws Exception {
        currentModel.remove();
        currentModel = null;
        initialModel = null;
    }

    public Objective minimize(Expression v) {
        return switch (getModel()) {
            case SymbolicModel sm -> sm.minimize(v);
            case ConcreteModel ignored -> throw new IllegalStateException("Cannot modify the optimisation method of an instantiated model");
            default -> throw new IllegalStateException("Unexpected value: " + getModel());
        };
    }

    public Objective maximize(Expression v) {
        return switch (getModel()) {
            case SymbolicModel sm -> sm.maximize(v);
            case ConcreteModel ignored -> throw new IllegalStateException("Cannot modify the optimisation method of an instantiated model");
            default -> throw new IllegalStateException("Unexpected value: " + getModel());
        };
    }

    public DFSearch dfSearch(Supplier<Runnable[]> branching) {
        return new DFSearch(this, branching);
    }

    public ConcurrentDFSearch concurrentDFSearch(Supplier<SymbolicModel[]> symbolicBranching) {
        return new ConcurrentDFSearch(this, symbolicBranching);
    }

    public <U extends Comparable<U>> BestFirstSearch<U> bestFirstSearch(Supplier<Runnable[]> branching, Supplier<U> nodeEvaluator) {
        return new BestFirstSearch<U>(this, branching, nodeEvaluator);
    }
}
