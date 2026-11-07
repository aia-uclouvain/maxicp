/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp;

import org.maxicp.cp.modeling.ModelProxyInstantiatorWithCP;
import org.maxicp.modeling.*;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.modeling.symbolic.*;
import org.maxicp.search.BestFirstSearch;
import org.maxicp.search.ConcurrentDFSearch;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.blackbox.BlackBoxSearch;
import org.maxicp.util.Ints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.maxicp.Constants.HORIZON;

/**
 * A class that allows to create symbolic models
 */
public class ModelDispatcher implements ModelProxyInstantiator, AutoCloseable, ModelProxyInstantiatorWithCP, ModelProxy {
    private Model initialModel;
    private ThreadLocal<Model> currentModel;
    private final boolean rawSolver;
    private final List<IntVar> registeredIntVars = new ArrayList<>();
    private final List<IntervalVar> registeredIntervalVars = new ArrayList<>();
    private final List<SeqVar> registeredSeqVars = new ArrayList<>();

    public ModelDispatcher() {
        this(false);
    }

    public ModelDispatcher(boolean rawSolver) {
        this.rawSolver = rawSolver;
        initialModel = SymbolicModel.emptyModel(this);
        currentModel = new ThreadLocal<>();
        currentModel.set(initialModel);
    }

    public boolean isRawSolver() {
        return rawSolver;
    }

    /**
     * @return the current model
     */
    public Model getModel() {
        Model m = currentModel != null ? currentModel.get() : null;
        return m != null ? m : initialModel;
    }

    /**
     * Set the current model to m. m should have this base model as origin.
     * @param m
     */
    public <T extends Model> T setModel(T m) {
        if (m != null && !m.getModelProxy().equals(this))
            throw new RuntimeException("Model being assigned to this ModelProxy does not originate from here");
        currentModel.set(m);
        if (rawSolver && m != null) {
            initialModel = m;
        }
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
     * Infers the decision variables from the constraint graph of the current model.
     *
     * <p>A variable is considered a decision variable when it appears as an
     * "index / choice" argument in a constraint or expression that implements
     * {@link DecisionVarsProvider}.  Examples: the index variable of an
     * {@code Element1D/2D} expression, or any variable in the scope of an
     * {@code AllDifferent}, {@code Circuit}, {@code Table}, etc.</p>
     *
     * <p>Only leaf {@link IntVar} instances are returned – derived expressions
     * (sums, element results, views) are always excluded.</p>
     *
     * <p>The returned list is ordered by first-seen occurrence during a depth-first
     * walk of the constraint graph and contains no duplicates (identity-based).</p>
     *
     * @return unmodifiable list of candidate decision variables
     * @see DecisionVarCollector
     */
    public List<IntExpression> getDecisionVariables() {
        return DecisionVarCollector.collect(this);
    }

    /**
     * Infers the interval variables from the constraint graph and variable registrations of the current model.
     *
     * @return unmodifiable list of interval variables
     */
    public List<IntervalVar> getIntervalVariables() {
        return DecisionVarCollector.collectIntervals(this);
    }

    /**
     * Infers the sequence variables from the constraint graph and variable registrations of the current model.
     *
     * @return unmodifiable list of sequence variables
     */
    public List<SeqVar> getSeqVariables() {
        return DecisionVarCollector.collectSequences(this);
    }

    /**
     * Returns all non-constant {@link IntVar} instances created on this model dispatcher.
     */
    public List<IntVar> getRegisteredIntVars() {
        return Collections.unmodifiableList(registeredIntVars);
    }

    /**
     * Returns all {@link IntervalVar} instances created on this model dispatcher.
     */
    public List<IntervalVar> getRegisteredIntervalVars() {
        return Collections.unmodifiableList(registeredIntervalVars);
    }

    /**
     * Returns all {@link SeqVar} instances created on this model dispatcher.
     */
    public List<SeqVar> getRegisteredSeqVars() {
        return Collections.unmodifiableList(registeredSeqVars);
    }

    private <T extends IntVar> T registerIntVar(T v) {
        registeredIntVars.add(v);
        return v;
    }

    private <T extends IntervalVar> T registerIntervalVar(T v) {
        registeredIntervalVars.add(v);
        return v;
    }

    private <T extends SeqVar> T registerSeqVar(T v) {
        registeredSeqVars.add(v);
        return v;
    }

    public <T extends IntVar> T registerRawIntVar(T v) {
        if (rawSolver) {
            registeredIntVars.add(v);
        }
        return v;
    }

    public <T extends IntervalVar> T registerRawIntervalVar(T v) {
        if (rawSolver) {
            registeredIntervalVars.add(v);
        }
        return v;
    }

    public <T extends SeqVar> T registerRawSeqVar(T v) {
        if (rawSolver) {
            registeredSeqVars.add(v);
        }
        return v;
    }

    /**
     * Create an array of n IntVars with domain between 0 and domSize-1, inclusive.
     * @param n size of the array, number of IntVars
     * @param domSize size of the domains. Domains are [0, domsize-1]
     */
    public IntVar[] intVarArray(int n, int domSize) {
        IntVar[] out = new IntVar[n];
        for (int i = 0; i < n; i++)
            out[i] = intVar(0, domSize - 1);
        if (n > 1) {
            addVariableGroup(Arrays.asList(out));
        }
        return out;
    }

    public IntExpression[] intVarArray(int n, Function<Integer, IntExpression> body) {
        IntExpression[] t = new IntExpression[n];
        for (int i = 0; i < n; i++)
            t[i] = body.apply(i);
        if (n > 1) {
            List<IntExpression> group = Arrays.stream(t).filter(Objects::nonNull).toList();
            if (group.size() > 1) {
                addVariableGroup(group);
            }
        }
        return t;
    }

    public IntVar intVar(int min, int max) {
        return registerIntVar(new IntVarRangeImpl(this, min, max));
    }

    public IntVar intVar(String id, int min, int max) {
        return registerIntVar(new IntVarRangeImpl(this, id, min, max));
    }

    public IntVar intVar(int[] values) {
        return registerIntVar(new IntVarSetImpl(this, Set.copyOf(Ints.asList(values))));
    }

    public IntVar intVar(Set<Integer> values) {
        return registerIntVar(new IntVarSetImpl(this, values));
    }

    public IntVar intVar(String id, int[] values) {
        return registerIntVar(new IntVarSetImpl(this, id, Set.copyOf(Ints.asList(values))));
    }

    public IntVar constant(int value) {
        return new IntVarRangeImpl(this, value, value);
    }

    public IntervalVar intervalVar(int startMin, int startMax, int endMin, int endMax, int lengthMin, int lengthMax, boolean isPresent) {
        return registerIntervalVar(new IntervalVarImpl(this, startMin, startMax, endMin, endMax, lengthMin, lengthMax, isPresent));
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
        for (int i = 0; i < n; i++)
            out[i] = boolVar();
        if (n > 1) {
            addVariableGroup(Arrays.asList(out));
        }
        return out;
    }

    public BoolVar boolVar() {
        return registerIntVar(new BoolVarImpl(this));
    }

    public SeqVar seqVar(int nNode, int begin, int end) {
        return registerSeqVar(new SeqVarImpl(this, nNode, begin, end));
    }

    public SeqVar[] seqVarArray(int n, Function<Integer, SeqVar> body) {
        SeqVar[] out = new SeqVar[n];
        for (int i = 0; i < n; i++) {
            out[i] = body.apply(i);
        }
        return out;
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

    /**
     * Creates a black-box search that automatically discovers the model's decision variables
     * (either {@link IntervalVar}s for scheduling models or {@link IntVar}s for combinatorial models).
     */
    public BlackBoxSearch blackBoxSearch() {
        return new BlackBoxSearch(this);
    }

    /**
     * Creates a black-box search over the specified integer decision variables.
     */
    public BlackBoxSearch blackBoxSearch(IntExpression... vars) {
        return new BlackBoxSearch(this, vars);
    }

    /**
     * Creates a black-box search over the specified interval variables.
     */
    public BlackBoxSearch blackBoxSearch(IntervalVar... intervals) {
        return new BlackBoxSearch(this, intervals);
    }

    /**
     * Creates a black-box search over the specified 2D matrix of interval variables.
     */
    public BlackBoxSearch blackBoxSearch(IntervalVar[][] intervals) {
        return new BlackBoxSearch(this, intervals);
    }

    /**
     * Creates a black-box search over the specified sequence variables.
     */
    public BlackBoxSearch blackBoxSearch(SeqVar... sequences) {
        return new BlackBoxSearch(this, sequences);
    }

    /**
     * Instantiates the model and solves it using black-box search.
     */
    public SearchStatistics solve() {
        return runCP(() -> blackBoxSearch().solve());
    }

    /**
     * Instantiates the model and solves it using black-box search within the given time limit (in seconds).
     */
    public SearchStatistics solve(int timeLimitInSeconds) {
        return runCP(() -> blackBoxSearch().solve(timeLimitInSeconds));
    }

    /**
     * Instantiates the model and solves it using black-box search with a stopping predicate.
     */
    public SearchStatistics solve(Predicate<SearchStatistics> limit) {
        return runCP(() -> blackBoxSearch().solve(limit));
    }

    /**
     * Instantiates the model and optimizes the given objective using black-box search.
     */
    public SearchStatistics optimize(Objective objective) {
        return runCP(() -> blackBoxSearch().optimize(objective));
    }

    /**
     * Instantiates the model and optimizes the given objective using black-box search within the given time limit (in seconds).
     */
    public SearchStatistics optimize(Objective objective, int timeLimitInSeconds) {
        return runCP(() -> blackBoxSearch().optimize(objective, timeLimitInSeconds));
    }

    /**
     * Instantiates the model and optimizes the given objective using black-box search with a stopping predicate.
     */
    public SearchStatistics optimize(Objective objective, Predicate<SearchStatistics> limit) {
        return runCP(() -> blackBoxSearch().optimize(objective, limit));
    }

    public void addVariableGroup(List<IntExpression> group) {
        if (getModel() instanceof SymbolicModel sm) {
            setModel(sm.addVariableGroup(group));
        } else if (getModel() instanceof ConcreteModel cm && cm.symbolicCopy() != null) {
            cm.symbolicCopy().addVariableGroup(group);
        }
    }
}
