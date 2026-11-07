package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.Maximize;
import org.maxicp.cp.engine.core.Minimize;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.DecisionVarCollector;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.modeling.symbolic.Maximization;
import org.maxicp.modeling.symbolic.Minimization;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.modeling.symbolic.SharedMaximization;
import org.maxicp.modeling.symbolic.SharedMinimization;
import org.maxicp.search.DFSearch;
import org.maxicp.search.FDSModeling;
import org.maxicp.search.FDSSeqVar;
import org.maxicp.search.PrimalSeqVar;
import org.maxicp.search.SearchMethod;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.search.SetTimesModeling;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Commercial-solver-style black-box search orchestrator for modeling-level CP and scheduling problems.
 *
 * <p>Implements {@link SearchMethod} so it can be used as a drop-in replacement for {@link DFSearch}
 * inside {@code model.runCP(cp -> ...)} via {@code cp.blackBoxSearch()}, or directly on a
 * {@link ModelDispatcher} via {@code model.blackBoxSearch()}, {@code model.solve(...)}, and
 * {@code model.optimize(...)}.</p>
 *
 * <p>Automatically discovers both integer/boolean variables ({@link IntExpression}) and scheduling
 * interval variables ({@link IntervalVar}) from the model when not explicitly provided.</p>
 */
public class BlackBoxSearch implements SearchMethod {

    /** Snapshot of an {@link IntervalVar} in an incumbent solution. */
    public record IntervalSnapshot(boolean present, int start, int length, int end) {
    }

    /** Snapshot of a {@link SeqVar} in an incumbent solution. */
    public record SeqSnapshot(int start, int end, List<Integer> members) {
    }

    /**
     * Strategy used to choose which decision variables are frozen in each LNS
     * restart.
     */
    public enum FragmentSelectionStrategy {
        /** Legacy behavior: independent random freeze decisions. */
        RANDOM_UNIFORM,
        /** Impact-guided behavior inspired by PGLNS ideas. */
        IMPACT_GUIDED,
        /** Group-coherent behavior relaxing variables from the same array. */
        GROUP_COHERENT
    }

    /** Logging granularity for phase execution. */
    public enum Verbosity {
        /** No logs. */
        QUIET,
        /** Phase start/end and high-level orchestration logs. */
        PHASE,
        /**
         * Phase logs plus progress events (solutions, improvements, restart
         * milestones).
         */
        PROGRESS,
        /** Full trace including per-iteration/per-restart fine-grained details. */
        TRACE
    }

    /** Configuration for restart-based feasibility phase. */
    public record RestartPhaseOptions(int baseFailureLimit, double randomSwapProbability, long randomSeed) {
        public RestartPhaseOptions {
            if (baseFailureLimit <= 0)
                throw new IllegalArgumentException("baseFailureLimit must be > 0");
            if (randomSwapProbability < 0.0 || randomSwapProbability > 1.0)
                throw new IllegalArgumentException("randomSwapProbability must be in [0,1]");
        }

        public static RestartPhaseOptions defaults() {
            return new RestartPhaseOptions(32, 0.50, 42L);
        }
    }

    /** Configuration for LNS improvement phase. */
    public record LnsPhaseOptions(int failureLimitPerRestart, int freezeRatePercent, double randomSwapProbability,
            long randomSeed, FragmentSelectionStrategy fragmentSelectionStrategy) {
        public LnsPhaseOptions {
            if (failureLimitPerRestart <= 0)
                throw new IllegalArgumentException("failureLimitPerRestart must be > 0");
            if (freezeRatePercent < 0 || freezeRatePercent > 100)
                throw new IllegalArgumentException("freezeRatePercent must be in [0,100]");
            if (randomSwapProbability < 0.0 || randomSwapProbability > 1.0)
                throw new IllegalArgumentException("randomSwapProbability must be in [0,1]");
            if (fragmentSelectionStrategy == null)
                throw new IllegalArgumentException("fragmentSelectionStrategy must not be null");
        }

        public LnsPhaseOptions(int failureLimitPerRestart, int freezeRatePercent, double randomSwapProbability,
                long randomSeed) {
            this(failureLimitPerRestart, freezeRatePercent, randomSwapProbability,
                    randomSeed, FragmentSelectionStrategy.IMPACT_GUIDED);
        }

        public static LnsPhaseOptions defaults() {
            return new LnsPhaseOptions(100, 95, 0.20, 43L, FragmentSelectionStrategy.GROUP_COHERENT);
        }
    }

    /** Configuration for exhaustive DFS/FDS phase. */
    public record ExhaustivePhaseOptions(double randomSwapProbability, long randomSeed) {
        public ExhaustivePhaseOptions {
            if (randomSwapProbability < 0.0 || randomSwapProbability > 1.0)
                throw new IllegalArgumentException("randomSwapProbability must be in [0,1]");
        }

        public static ExhaustivePhaseOptions defaults() {
            return new ExhaustivePhaseOptions(0, 44L);
        }
    }

    private record Phase(String name, RunnableSearch search, double timeShare, boolean requiresFeasible) {
        private Phase {
            if (timeShare < 0.0 || timeShare > 1.0)
                throw new IllegalArgumentException("timeShare must be in [0,1]");
        }
    }

    private final ModelDispatcher model;
    private final boolean autoDiscoveredVars;
    private List<IntExpression> vars;
    private final List<IntervalVar> intervals;
    private final List<SeqVar> sequences;
    private Objective objective;
    private final List<Phase> phases = new ArrayList<>();
    private boolean userConfiguredPhases = false;
    private final List<String> executedPhaseNames = new ArrayList<>();
    private Verbosity verbosity = Verbosity.QUIET;

    private Optional<List<Integer>> bestSolution = Optional.empty();
    private Optional<List<IntervalSnapshot>> bestIntervalSolution = Optional.empty();
    private Optional<List<SeqSnapshot>> bestSeqSolution = Optional.empty();
    private final IdentityHashMap<IntExpression, Integer> bestVarValues = new IdentityHashMap<>();
    private Optional<Integer> bestObjectiveValue = Optional.empty();
    private SearchStatus lastStatus = SearchStatus.UNKNOWN;
    private long solutionCount = 0;

    private BiConsumer<List<Integer>, Integer> incumbentListener;
    private final List<Consumer<SearchStatistics>> solutionListeners = new ArrayList<>();
    private final List<Consumer<SearchStatistics>> failureListeners = new ArrayList<>();

    private SearchStatistics liveStats = new SearchStatistics();
    private int accumulatedNodes = 0;
    private int accumulatedFailures = 0;
    private long searchStartMillis = 0L;
    private Predicate<SearchStatistics> activeLimit = null;
    private boolean limitReached = false;

    private ModelStructureAnalyzer cachedStructure;
    private ConflictDrivenBranching sharedConflictBranching;
    private FDSModeling sharedPrimaryFds;
    private FDSModeling sharedSchedulingFds;
    private FDSSeqVar sharedSequenceFds;
    private PrimalSeqVar sharedSequencePrimal;
    private ConcreteModel cachedConcreteModel;
    private CPIntVar[] cachedCpVars;
    private CPIntervalVar[] cachedCpIntervals;
    private CPSeqVar[] cachedCpSequences;
    private CPIntVar cachedObjCpVar;

    /**
     * Creates a black-box search on explicit integer decision variables with an objective.
     */
    public BlackBoxSearch(ModelDispatcher model, List<IntExpression> vars, Objective objective) {
        this(
                model,
                vars,
                DecisionVarCollector.collectIntervals(model),
                DecisionVarCollector.collectSequences(model),
                objective,
                false);
    }

    private BlackBoxSearch(
            ModelDispatcher model,
            List<IntExpression> vars,
            List<IntervalVar> intervals,
            List<SeqVar> sequences,
            Objective objective,
            boolean autoDiscoveredVars) {
        this.model = model;
        this.vars = List.copyOf(vars);
        this.intervals = List.copyOf(intervals);
        this.sequences = List.copyOf(sequences);
        this.objective = objective;
        this.autoDiscoveredVars = autoDiscoveredVars;
    }

    /**
     * Creates a black-box search with automatic decision-variable, interval-variable, and sequence-variable inference.
     */
    public BlackBoxSearch(ModelDispatcher model, Objective objective) {
        this(
                model,
                DecisionVarCollector.collectAllIntVars(model, objective),
                DecisionVarCollector.collectIntervals(model),
                DecisionVarCollector.collectSequences(model),
                objective,
                true);
    }

    /**
     * Creates a black-box search on an array of integer decision variables with an objective.
     */
    public BlackBoxSearch(ModelDispatcher model, IntExpression[] vars, Objective objective) {
        this(model, Arrays.asList(vars), List.of(), List.of(), objective, false);
    }

    /**
     * Creates a black-box search on an array of integer decision variables without objective (feasibility).
     */
    public BlackBoxSearch(ModelDispatcher model, IntExpression[] vars) {
        this(model, Arrays.asList(vars), List.of(), List.of(), null, false);
    }

    /**
     * Creates a black-box search on an array of scheduling interval variables.
     */
    public BlackBoxSearch(ModelDispatcher model, IntervalVar... intervals) {
        this(model, DecisionVarCollector.collectAllIntVars(model, null), Arrays.asList(intervals), List.of(), null, true);
    }

    /**
     * Creates a black-box search on a 2D matrix of scheduling interval variables.
     */
    public BlackBoxSearch(ModelDispatcher model, IntervalVar[][] intervals) {
        this(
                model,
                DecisionVarCollector.collectAllIntVars(model, null),
                Arrays.stream(intervals).flatMap(Arrays::stream).toList(),
                List.of(),
                null,
                true);
    }

    /**
     * Creates a black-box search on an array of sequence variables.
     */
    public BlackBoxSearch(ModelDispatcher model, SeqVar... sequences) {
        this(
                model,
                DecisionVarCollector.collectAllIntVars(model, null),
                List.of(),
                Arrays.asList(sequences),
                null,
                true);
    }

    /**
     * Creates a black-box search with automatic decision-variable, interval-variable, and sequence-variable inference.
     */
    public BlackBoxSearch(ModelDispatcher model) {
        this(model, (Objective) null);
    }

    /** Sets logging verbosity for this search. */
    public BlackBoxSearch withVerbosity(Verbosity verbosity) {
        this.verbosity = verbosity == null ? Verbosity.QUIET : verbosity;
        return this;
    }

    /**
     * Registers a callback invoked whenever a new feasible or improving incumbent is recorded.
     */
    public BlackBoxSearch onIncumbent(BiConsumer<List<Integer>, Integer> listener) {
        this.incumbentListener = listener;
        return this;
    }

    @Override
    public void onSolution(Consumer<SearchStatistics> listener) {
        if (listener != null) {
            solutionListeners.add(listener);
        }
    }

    @Override
    public void onFailure(Consumer<SearchStatistics> listener) {
        if (listener != null) {
            failureListeners.add(listener);
        }
    }

    List<IntervalVar> intervals() {
        return intervals;
    }

    List<SeqVar> sequences() {
        return sequences;
    }

    CPSeqVar[] cpSequences() {
        ensureCpCache();
        return cachedCpSequences;
    }

    List<IntExpression> vars() {
        return vars;
    }

    ModelStructureAnalyzer structureAnalyzer() {
        if (cachedStructure == null) {
            cachedStructure = ModelStructureAnalyzer.analyze(model, vars, objective);
        }
        return cachedStructure;
    }

    ConflictDrivenBranching conflictDrivenBranching(Random fallbackRandom) {
        if (sharedConflictBranching == null) {
            sharedConflictBranching = new ConflictDrivenBranching(
                    model, vars, structureAnalyzer(), fallbackRandom != null ? fallbackRandom : new Random(42L));
            bestSolution.ifPresent(sharedConflictBranching::updateIncumbent);
        }
        return sharedConflictBranching;
    }

    private IntExpression[] computePrimaryIntVars() {
        ModelStructureAnalyzer structure = structureAnalyzer();
        int[] primaryIdx = structure.primaryIndices();
        IntExpression[] primaryVars = new IntExpression[primaryIdx.length];
        for (int i = 0; i < primaryIdx.length; i++) {
            primaryVars[i] = vars.get(primaryIdx[i]);
        }
        if (primaryVars.length == 0 && !vars.isEmpty()) {
            primaryVars = vars.toArray(IntExpression[]::new);
        }
        return primaryVars;
    }

    FDSModeling primaryFdsBranching() {
        if (sharedPrimaryFds == null) {
            IntExpression[] primaryVars = computePrimaryIntVars();
            if (primaryVars.length > 0) {
                sharedPrimaryFds = new FDSModeling(primaryVars);
            }
        } else {
            sharedPrimaryFds.resetLevel();
        }
        return sharedPrimaryFds;
    }

    Supplier<Runnable[]> tieredFdsBranching(Random fallbackRandom) {
        if (!sequences.isEmpty()) {
            return sequenceFdsBranching(fallbackRandom);
        }
        if (!intervals.isEmpty()) {
            return schedulingFdsBranching(fallbackRandom);
        }
        FDSModeling fds = primaryFdsBranching();
        if (fds == null) {
            return () -> Searches.EMPTY;
        }
        ModelStructureAnalyzer structure = structureAnalyzer();
        if (structure.secondaryIndices().length == 0) {
            return fds;
        }
        ConflictDrivenBranching cd = conflictDrivenBranching(fallbackRandom);
        return Searches.and(fds, cd);
    }

    Supplier<Runnable[]> sequencePrimalBranching(Random fallbackRandom, double noise) {
        if (sequences.isEmpty()) {
            return tieredFdsBranching(fallbackRandom);
        }
        if (sharedSequencePrimal == null) {
            SeqVar[] seqArr = sequences.toArray(SeqVar[]::new);
            sharedSequencePrimal = new PrimalSeqVar(seqArr);
        }
        sharedSequencePrimal.withRandomNoise(fallbackRandom != null ? fallbackRandom : new Random(42L), noise);
        if (vars.isEmpty()) {
            return sharedSequencePrimal;
        }
        return Searches.and(sharedSequencePrimal, nonAuxSequenceIntBranching(fallbackRandom));
    }

    Supplier<Runnable[]> sequenceFdsBranching(Random fallbackRandom) {
        if (sequences.isEmpty()) {
            return tieredFdsBranching(fallbackRandom);
        }
        if (sharedSequenceFds == null) {
            SeqVar[] seqArr = sequences.toArray(SeqVar[]::new);
            sharedSequenceFds = new FDSSeqVar(seqArr);
        } else {
            sharedSequenceFds.resetLevel();
        }
        if (vars.isEmpty()) {
            return sharedSequenceFds;
        }
        return Searches.and(sharedSequenceFds, nonAuxSequenceIntBranching(fallbackRandom));
    }

    private Supplier<Runnable[]> nonAuxSequenceIntBranching(Random fallbackRandom) {
        ConflictDrivenBranching cd = conflictDrivenBranching(fallbackRandom);
        return () -> {
            ensureCpCache();
            if (cachedCpVars != null && cachedCpSequences != null) {
                boolean hasUnfixedNonTimeVar = false;
                for (CPIntVar v : cachedCpVars) {
                    if (v != null && !v.isFixed()) {
                        boolean isTimeVar = false;
                        for (CPSeqVar seq : cachedCpSequences) {
                            if (seq != null && seq.isTransitionTimeVar(v)) {
                                isTimeVar = true;
                                break;
                            }
                        }
                        if (!isTimeVar) {
                            hasUnfixedNonTimeVar = true;
                            break;
                        }
                    }
                }
                if (!hasUnfixedNonTimeVar) {
                    if (cachedObjCpVar != null && !cachedObjCpVar.isFixed()) {
                        int target = isMinimization(objective) ? cachedObjCpVar.min() : cachedObjCpVar.max();
                        return new Runnable[]{
                                () -> {
                                    cachedObjCpVar.fix(target);
                                    cachedObjCpVar.getSolver().fixPoint();
                                }
                        };
                    }
                    return Searches.EMPTY;
                }
            }
            return cd.get();
        };
    }

    Supplier<Runnable[]> schedulingFdsBranching(Random fallbackRandom) {
        if (intervals.isEmpty()) {
            return tieredFdsBranching(fallbackRandom);
        }
        if (sharedSchedulingFds == null) {
            IntervalVar[] ivArr = intervals.toArray(IntervalVar[]::new);
            IntExpression[] primaryVars = computePrimaryIntVars();
            sharedSchedulingFds = primaryVars.length > 0
                    ? new FDSModeling(ivArr, primaryVars)
                    : new FDSModeling(ivArr);
        } else {
            sharedSchedulingFds.resetLevel();
        }
        if (vars.isEmpty() || structureAnalyzer().secondaryIndices().length == 0) {
            return sharedSchedulingFds;
        }
        ConflictDrivenBranching cd = conflictDrivenBranching(fallbackRandom);
        return Searches.and(sharedSchedulingFds, cd);
    }

    Supplier<Runnable[]> schedulingSetTimesBranching(Random fallbackRandom) {
        IntervalVar[] ivArr = intervals.toArray(IntervalVar[]::new);
        Supplier<Runnable[]> setTimes = new SetTimesModeling(ivArr);
        if (vars.isEmpty()) {
            return setTimes;
        }
        ConflictDrivenBranching cd = conflictDrivenBranching(fallbackRandom);
        return Searches.and(setTimes, cd);
    }

    DFSearch createSubSearch(Supplier<Runnable[]> branching) {
        DFSearch dfs = model.dfSearch(branching);
        if (!failureListeners.isEmpty()) {
            dfs.onFailure(subStats -> {
                syncLiveStats(subStats);
                for (Consumer<SearchStatistics> fl : failureListeners) {
                    fl.accept(liveStats);
                }
            });
        }
        return dfs;
    }

    boolean shouldAbortSubSearch(
            SearchStatistics subStats,
            long phaseStartMillis,
            long phaseBudgetMillis,
            int failureCutoff) {
        syncLiveStats(subStats);
        if (activeLimit != null && activeLimit.test(liveStats)) {
            limitReached = true;
            return true;
        }
        if ((System.currentTimeMillis() - phaseStartMillis) >= phaseBudgetMillis) {
            return true;
        }
        return failureCutoff < Integer.MAX_VALUE && subStats.numberOfFailures() >= failureCutoff;
    }

    void accumulateSubSearchStats(SearchStatistics subStats) {
        if (subStats == null) {
            return;
        }
        accumulatedNodes += subStats.numberOfNodes();
        accumulatedFailures += subStats.numberOfFailures();
        syncLiveStats(null);
    }

    private void syncLiveStats(SearchStatistics currentSubStats) {
        int subNodes = currentSubStats != null ? currentSubStats.numberOfNodes() : 0;
        int subFails = currentSubStats != null ? currentSubStats.numberOfFailures() : 0;
        int targetNodes = accumulatedNodes + subNodes;
        int targetFails = accumulatedFailures + subFails;
        while (liveStats.numberOfNodes() < targetNodes) {
            liveStats.incrNodes();
        }
        while (liveStats.numberOfFailures() < targetFails) {
            liveStats.incrFailures();
        }
        if (searchStartMillis > 0L) {
            liveStats.setTimeInMillis(Math.max(0L, System.currentTimeMillis() - searchStartMillis));
        }
    }

    boolean isLimitReached() {
        if (limitReached) {
            return true;
        }
        if (activeLimit != null) {
            syncLiveStats(null);
            if (activeLimit.test(liveStats)) {
                limitReached = true;
                return true;
            }
        }
        return false;
    }

    void ensureCpCache() {
        if (!model.isConcrete()) {
            return;
        }
        ConcreteModel cm = model.getConcreteModel();
        if (cm != cachedConcreteModel) {
            cachedConcreteModel = cm;
            if (cm instanceof ConcreteCPModel cpm) {
                cachedCpVars = new CPIntVar[vars.size()];
                for (int i = 0; i < vars.size(); i++) {
                    cachedCpVars[i] = cpm.getCPVar(vars.get(i));
                }
                cachedCpIntervals = new CPIntervalVar[intervals.size()];
                for (int i = 0; i < intervals.size(); i++) {
                    cachedCpIntervals[i] = cpm.getCPVar(intervals.get(i));
                }
                cachedCpSequences = new CPSeqVar[sequences.size()];
                for (int i = 0; i < sequences.size(); i++) {
                    cachedCpSequences[i] = cpm.getCPVar(sequences.get(i));
                }
                IntExpression objExpr = extractObjectiveExpr(objective);
                if (objExpr != null) {
                    cachedObjCpVar = cpm.getCPVar(objExpr);
                    cpm.solver.fixPoint();
                } else {
                    cachedObjCpVar = null;
                }
            } else {
                cachedCpVars = null;
                cachedCpIntervals = null;
                cachedCpSequences = null;
                cachedObjCpVar = null;
            }
        }
    }

    private CPIntVar currentObjCpVar() {
        ensureCpCache();
        if (cachedConcreteModel instanceof ConcreteCPModel cpm && objective != null) {
            IntExpression objExpr = extractObjectiveExpr(objective);
            return cpm.getCPVar(objExpr);
        }
        return null;
    }

    void recordSolutionFromCurrentState() {
        ensureCpCache();
        Integer objVal = evaluateCurrentObjective();
        if (objective != null && objVal != null && bestObjectiveValue.isPresent()) {
            int currentBest = bestObjectiveValue.get();
            boolean strictlyBetter = isMinimization(objective) ? (objVal < currentBest) : (objVal > currentBest);
            if (!strictlyBetter) {
                return;
            }
        }

        List<Integer> solution = new ArrayList<>(vars.size());
        if (cachedCpVars != null) {
            for (int i = 0; i < vars.size(); i++) {
                int val = cachedCpVars[i].min();
                solution.add(val);
                bestVarValues.put(vars.get(i), val);
            }
        } else {
            for (IntExpression var : vars) {
                int val = var.min();
                solution.add(val);
                bestVarValues.put(var, val);
            }
        }

        if (!intervals.isEmpty()) {
            List<IntervalSnapshot> ivSnaps = new ArrayList<>(intervals.size());
            if (cachedCpIntervals != null) {
                for (CPIntervalVar cpIv : cachedCpIntervals) {
                    ivSnaps.add(new IntervalSnapshot(
                            cpIv.isPresent(),
                            cpIv.startMin(),
                            cpIv.lengthMin(),
                            cpIv.endMin()));
                }
            } else {
                for (IntervalVar iv : intervals) {
                    ivSnaps.add(new IntervalSnapshot(
                            iv.isPresent(),
                            iv.startMin(),
                            iv.lengthMin(),
                            iv.endMin()));
                }
            }
            bestIntervalSolution = Optional.of(Collections.unmodifiableList(ivSnaps));
        }

        if (!sequences.isEmpty()) {
            List<SeqSnapshot> seqSnaps = new ArrayList<>(sequences.size());
            if (cachedCpSequences != null) {
                for (CPSeqVar cpSeq : cachedCpSequences) {
                    int[] buf = new int[cpSeq.nNode()];
                    int m = cpSeq.fillNode(buf, SeqStatus.MEMBER_ORDERED);
                    List<Integer> members = new ArrayList<>(m);
                    for (int i = 0; i < m; i++) {
                        members.add(buf[i]);
                    }
                    seqSnaps.add(new SeqSnapshot(cpSeq.start(), cpSeq.end(), Collections.unmodifiableList(members)));
                }
            } else {
                for (SeqVar seq : sequences) {
                    int[] buf = new int[seq.nNode()];
                    int m = seq.fillNode(buf, SeqStatus.MEMBER_ORDERED);
                    List<Integer> members = new ArrayList<>(m);
                    for (int i = 0; i < m; i++) {
                        members.add(buf[i]);
                    }
                    seqSnaps.add(new SeqSnapshot(seq.start(), seq.end(), Collections.unmodifiableList(members)));
                }
            }
            bestSeqSolution = Optional.of(Collections.unmodifiableList(seqSnaps));
        }

        updateObjective(objVal);
        updateSolution(solution);

        liveStats.incrSolutions();
        syncLiveStats(null);
        for (Consumer<SearchStatistics> listener : solutionListeners) {
            listener.accept(liveStats);
        }
    }

    private static boolean isMinimization(Objective objective) {
        return objective instanceof Minimization || objective instanceof SharedMinimization;
    }

    /**
     * Registers one executable phase in the black-box plan.
     */
    public BlackBoxSearch addPhase(String name, RunnableSearch search, double timeShare, boolean requiresFeasible) {
        if (timeShare <= 0.0) {
            return this;
        }
        phases.add(new Phase(name, search, timeShare, requiresFeasible));
        return this;
    }

    /**
     * Builds the built-in default plan.
     */
    public BlackBoxSearch withDefaultPhasePlan() {
        phases.clear();

        if (!sequences.isEmpty()) {
            RunnableSearch initialSeq = new SequenceRunnableSearch(this, model, sequences, vars, objective, 42L);
            addPhase("initial-exhaustive", initialSeq, objective == null ? 0.25 : 0.20, false);

            if (objective == null) {
                RunnableSearch restSeq = new SequenceRunnableSearch(this, model, sequences, vars, null, 43L);
                addPhase("feasibility-restarts", restSeq, 0.75, false);
                return this;
            }

            RunnableSearch seqLns = new SequenceLNSRunnableSearch(this, model, sequences, vars, objective, 80, 75, 43L);
            addPhase("lns-improvement", seqLns, 0.60, true);

            RunnableSearch finalSeq = new SequenceRunnableSearch(this, model, sequences, vars, objective, 44L);
            addPhase("exhaustive-fds", finalSeq, 0.20, false);
            return this;
        }

        if (!intervals.isEmpty()) {
            RunnableSearch initialSched = new SchedulingRunnableSearch(this, model, intervals, vars, objective, 42L);
            addPhase("initial-exhaustive", initialSched, objective == null ? 0.25 : 0.25, false);

            if (objective == null) {
                RunnableSearch restSched = new SchedulingRunnableSearch(this, model, intervals, vars, null, 43L);
                addPhase("feasibility-restarts", restSched, 0.75, false);
                return this;
            }

            RunnableSearch schedLns = new SchedulingLNSRunnableSearch(this, model, intervals, vars, objective, 80, 65, 43L);
            addPhase("lns-improvement", schedLns, 0.50, true);

            RunnableSearch finalSched = new SchedulingRunnableSearch(this, model, intervals, vars, objective, 44L);
            addPhase("exhaustive-fds", finalSched, 0.25, false);
            return this;
        }

        ExhaustivePhaseOptions exhaustiveOptions = ExhaustivePhaseOptions.defaults();
        RunnableSearch initialExhaustive = new DFSRunnableSearch(this, model, vars, objective,
                exhaustiveOptions.randomSwapProbability(), exhaustiveOptions.randomSeed());
        addPhase("initial-exhaustive", initialExhaustive, objective == null ? 0.10 : 0.25, false);

        RestartPhaseOptions restartOptions = RestartPhaseOptions.defaults();
        RunnableSearch restartSearch = new RestartRunnableSearch(this, model, vars,
                restartOptions.baseFailureLimit(), restartOptions.randomSwapProbability(), restartOptions.randomSeed());
        addPhase("feasibility-restarts", restartSearch, objective == null ? 0.90 : 0.20, false);

        if (objective == null) {
            return this;
        }

        LnsPhaseOptions lnsOptions = LnsPhaseOptions.defaults();
        RunnableSearch lns = new LNSRunnableSearch(this, model, vars, objective,
                lnsOptions.failureLimitPerRestart(), lnsOptions.freezeRatePercent(),
                lnsOptions.randomSwapProbability(), lnsOptions.randomSeed(),
                lnsOptions.fragmentSelectionStrategy());
        addPhase("lns-improvement", lns, 1.0 / 3.0, true);

        RunnableSearch exhaustive = new DFSRunnableSearch(this, model, vars, objective,
                exhaustiveOptions.randomSwapProbability(), exhaustiveOptions.randomSeed());
        addPhase("exhaustive-fds", exhaustive, (1.0 - 0.25 - 0.20 - 1.0 / 3.0), false);

        return this;
    }

    /**
     * Configures the optimization phase plan explicitly.
     */
    public BlackBoxSearch withOptimizationPlan(RestartPhaseOptions restart,
            LnsPhaseOptions lns,
            ExhaustivePhaseOptions exhaustive,
            double initialExhaustiveShare,
            double restartShare,
            double lnsShare,
            double exhaustiveShare) {
        if (objective == null) {
            throw new IllegalStateException("Optimization plan requires an objective");
        }
        validateShares(initialExhaustiveShare, restartShare, lnsShare, exhaustiveShare);
        phases.clear();
        userConfiguredPhases = true;

        RunnableSearch initialExhaustiveSearch = new DFSRunnableSearch(this, model, vars, objective,
                exhaustive.randomSwapProbability(), exhaustive.randomSeed());

        RunnableSearch restartSearch = new RestartRunnableSearch(this, model, vars,
                restart.baseFailureLimit(), restart.randomSwapProbability(), restart.randomSeed());
        RunnableSearch lnsSearch = new LNSRunnableSearch(this, model, vars, objective,
                lns.failureLimitPerRestart(), lns.freezeRatePercent(), lns.randomSwapProbability(), lns.randomSeed(),
                lns.fragmentSelectionStrategy());
        RunnableSearch exhaustiveSearch = new DFSRunnableSearch(this, model, vars, objective,
                exhaustive.randomSwapProbability(), exhaustive.randomSeed());

        addPhase("initial-exhaustive", initialExhaustiveSearch, initialExhaustiveShare, false);
        addPhase("feasibility-restarts", restartSearch, restartShare, false);
        addPhase("lns-improvement", lnsSearch, lnsShare, true);
        addPhase("exhaustive-fds", exhaustiveSearch, exhaustiveShare, false);
        return this;
    }

    /**
     * Configures the feasibility phase plan explicitly.
     */
    public BlackBoxSearch withFeasibilityPlan(RestartPhaseOptions restart,
            ExhaustivePhaseOptions exhaustive,
            double initialExhaustiveShare,
            double restartShare,
            double exhaustiveShare) {
        validateShares(initialExhaustiveShare, restartShare, exhaustiveShare);
        phases.clear();
        userConfiguredPhases = true;

        RunnableSearch initialExhaustiveSearch = new DFSRunnableSearch(this, model, vars, null,
                exhaustive.randomSwapProbability(), exhaustive.randomSeed());

        RunnableSearch restartSearch = new RestartRunnableSearch(this, model, vars,
                restart.baseFailureLimit(), restart.randomSwapProbability(), restart.randomSeed());
        RunnableSearch exhaustiveSearch = new DFSRunnableSearch(this, model, vars, null,
                exhaustive.randomSwapProbability(), exhaustive.randomSeed());

        addPhase("initial-exhaustive", initialExhaustiveSearch, initialExhaustiveShare, false);
        addPhase("feasibility-restarts", restartSearch, restartShare, false);
        addPhase("exhaustive-fds", exhaustiveSearch, exhaustiveShare, false);
        return this;
    }

    private static void validateShares(double... shares) {
        double total = 0.0;
        boolean hasPositiveShare = false;
        for (double share : shares) {
            if (share < 0.0 || share > 1.0) {
                throw new IllegalArgumentException("Each phase share must be in [0,1]");
            }
            total += share;
            hasPositiveShare |= share > 0.0;
        }
        if (total > 1.001) {
            throw new IllegalArgumentException("Phase shares must sum to at most 1.0");
        }
        if (!hasPositiveShare) {
            throw new IllegalArgumentException("At least one phase share must be > 0");
        }
    }

    private void resetRunMetrics() {
        liveStats = new SearchStatistics();
        accumulatedNodes = 0;
        accumulatedFailures = 0;
        searchStartMillis = System.currentTimeMillis();
        limitReached = false;
        bestSolution = Optional.empty();
        bestIntervalSolution = Optional.empty();
        bestSeqSolution = Optional.empty();
        bestVarValues.clear();
        bestObjectiveValue = Optional.empty();
        solutionCount = 0;
        executedPhaseNames.clear();
    }

    private void setObjectiveInternal(Objective newObjective) {
        if (this.objective != newObjective) {
            this.objective = newObjective;
            this.cachedStructure = null;
            this.sharedConflictBranching = null;
            this.sharedPrimaryFds = null;
            this.sharedSchedulingFds = null;
            this.sharedSequenceFds = null;
            this.sharedSequencePrimal = null;
            this.cachedConcreteModel = null;
            if (autoDiscoveredVars) {
                this.vars = DecisionVarCollector.collectAllIntVars(model, newObjective);
            }
            if (!userConfiguredPhases) {
                phases.clear();
            }
        }
    }

    /**
     * Executes the configured phase plan under a global timeout.
     *
     * @param timeLimitInSeconds global timeout in seconds
     * @return merged global status across executed phases
     */
    public SearchStatus start(int timeLimitInSeconds) {
        if (phases.isEmpty()) {
            throw new IllegalStateException("No search phases configured");
        }
        if (!model.isConcrete()) {
            try {
                return model.runCP(() -> startInConcreteContext(timeLimitInSeconds));
            } catch (InconsistencyException e) {
                lastStatus = SearchStatus.UNSAT;
                liveStats.setCompleted();
                return SearchStatus.UNSAT;
            }
        }
        return startInConcreteContext(timeLimitInSeconds);
    }

    private SearchStatus startInConcreteContext(int timeLimitInSeconds) {
        ensureCpCache();
        if (searchStartMillis == 0L) {
            searchStartMillis = System.currentTimeMillis();
        }

        // Check trivial root satisfaction when there are no variables, intervals, or sequences
        if (vars.isEmpty() && intervals.isEmpty() && sequences.isEmpty()) {
            recordSolutionFromCurrentState();
            liveStats.setCompleted();
            lastStatus = objective == null ? SearchStatus.SAT : SearchStatus.PROVEN_OPTIMAL;
            return lastStatus;
        }

        long totalBudgetMillis = Math.max(1L, timeLimitInSeconds * 1000L);
        long t0 = System.currentTimeMillis();
        SearchStatus globalStatus = SearchStatus.UNKNOWN;
        int phaseIndex = 0;
        executedPhaseNames.clear();
        solutionCount = 0;

        logPhase("[blackbox] start timeout=%ds phases=%d objective=%s"
                .formatted(timeLimitInSeconds, phases.size(),
                        objective == null ? "none" : objective.getClass().getSimpleName()));

        for (Phase phase : phases) {
            long elapsed = System.currentTimeMillis() - t0;
            if (elapsed >= totalBudgetMillis || isLimitReached()) {
                logPhase("[blackbox] timeout/limit reached before phase '%s'".formatted(phase.name));
                break;
            }

            if (phase.requiresFeasible && !hasAnyIncumbent()) {
                logPhase("[blackbox] skip phase '%s' (no feasible incumbent yet)".formatted(phase.name));
                phaseIndex++;
                continue;
            }

            long remaining = totalBudgetMillis - elapsed;
            long phaseBudget;
            if (phaseIndex == phases.size() - 1) {
                phaseBudget = remaining;
            } else {
                double sumAll = 0.0;
                for (Phase p : phases) {
                    sumAll += p.timeShare;
                }
                double implicitFraction = Math.max(0.0, 1.0 - sumAll);
                double S_rem = implicitFraction;
                for (int j = phaseIndex; j < phases.size(); j++) {
                    S_rem += phases.get(j).timeShare;
                }
                if (S_rem > 0.0) {
                    phaseBudget = Math.max(1L, Math.round(remaining * (phase.timeShare / S_rem)));
                } else {
                    phaseBudget = Math.max(1L, Math.round(totalBudgetMillis * phase.timeShare));
                }
            }
            phaseBudget = Math.min(phaseBudget, remaining);

            logPhase("[blackbox] phase '%s' budget=%dms remaining=%dms"
                    .formatted(phase.name, phaseBudget, remaining));
            executedPhaseNames.add(phase.name);
            SearchStatus status = phase.search.run(phaseBudget);
            logPhase("[blackbox] phase '%s' completed with status=%s".formatted(phase.name, status));
            globalStatus = mergeStatus(globalStatus, status);
            if (objective == null && hasAnyIncumbent()) {
                logPhase("[blackbox] stop early on first feasible solution");
                lastStatus = SearchStatus.SAT;
                return SearchStatus.SAT;
            }
            if (status == SearchStatus.PROVEN_OPTIMAL || status == SearchStatus.UNSAT) {
                logPhase("[blackbox] stop early with status=%s".formatted(status));
                liveStats.setCompleted();
                lastStatus = status;
                return status;
            }
            phaseIndex++;
        }

        logPhase("[blackbox] finished with status=%s".formatted(globalStatus));
        lastStatus = globalStatus;
        return globalStatus;
    }

    private boolean hasAnyIncumbent() {
        return bestSolution.isPresent() || bestIntervalSolution.isPresent() || bestSeqSolution.isPresent();
    }

    // -------------------------------------------------------------------------
    // SearchMethod Implementation
    // -------------------------------------------------------------------------

    @Override
    public SearchStatistics solve() {
        return solve(stats -> false);
    }

    /**
     * Solves the feasibility model under a wall-clock timeout in seconds.
     *
     * @param timeLimitInSeconds timeout in seconds
     * @return aggregated search statistics
     */
    public SearchStatistics solve(int timeLimitInSeconds) {
        setObjectiveInternal(null);
        if (phases.isEmpty()) {
            withDefaultPhasePlan();
        }
        resetRunMetrics();
        this.activeLimit = null;
        start(timeLimitInSeconds);
        syncLiveStats(null);
        return liveStats;
    }

    @Override
    public SearchStatistics solve(Predicate<SearchStatistics> limit) {
        setObjectiveInternal(null);
        return runWithLimit(limit, null);
    }

    @Override
    public SearchStatistics solveSubjectTo(Predicate<SearchStatistics> limit, Runnable subjectTo) {
        setObjectiveInternal(null);
        return runWithLimit(limit, subjectTo);
    }

    @Override
    public SearchStatistics optimize(Objective obj) {
        return optimize(obj, stats -> false);
    }

    /**
     * Optimizes the given objective under a wall-clock timeout in seconds.
     *
     * @param obj                symbolic objective to minimize or maximize
     * @param timeLimitInSeconds timeout in seconds
     * @return aggregated search statistics
     */
    public SearchStatistics optimize(Objective obj, int timeLimitInSeconds) {
        setObjectiveInternal(obj);
        if (phases.isEmpty()) {
            withDefaultPhasePlan();
        }
        resetRunMetrics();
        this.activeLimit = null;
        start(timeLimitInSeconds);
        syncLiveStats(null);
        return liveStats;
    }

    @Override
    public SearchStatistics optimize(Objective obj, Predicate<SearchStatistics> limit) {
        setObjectiveInternal(obj);
        return runWithLimit(limit, null);
    }

    @Override
    public SearchStatistics optimizeSubjectTo(Objective objToTighten, Predicate<SearchStatistics> limit, Runnable subjectTo) {
        setObjectiveInternal(objToTighten);
        return runWithLimit(limit, subjectTo);
    }

    @Override
    public SearchStatistics optimize(org.maxicp.search.Objective obj) {
        return optimize(obj, stats -> false);
    }

    /**
     * Optimizes the given low-level objective within the specified time limit (in seconds).
     */
    public SearchStatistics optimize(org.maxicp.search.Objective obj, int timeLimitInSeconds) {
        long budgetMillis = Math.max(1L, timeLimitInSeconds) * 1000L;
        return optimize(obj, stats -> stats.timeInMillis() >= budgetMillis);
    }

    @Override
    public SearchStatistics optimize(org.maxicp.search.Objective toTighten, Predicate<SearchStatistics> limit) {
        return optimizeSubjectTo(toTighten, limit, null);
    }

    @Override
    public SearchStatistics optimizeSubjectTo(
            org.maxicp.search.Objective objToTighten,
            Predicate<SearchStatistics> limit,
            Runnable subjectTo) {
        if (objToTighten instanceof Minimize min) {
            return optimizeSubjectTo(new Minimization(min.getVar()), limit, subjectTo);
        }
        if (objToTighten instanceof Maximize max) {
            return optimizeSubjectTo(new Maximization(max.getVar()), limit, subjectTo);
        }
        if (!model.isConcrete()) {
            return model.runCP(() -> executeLowLevelObjective(objToTighten, limit, subjectTo));
        }
        return executeLowLevelObjective(objToTighten, limit, subjectTo);
    }

    private SearchStatistics executeLowLevelObjective(
            org.maxicp.search.Objective objToTighten,
            Predicate<SearchStatistics> limit,
            Runnable subjectTo) {
        resetRunMetrics();
        this.activeLimit = limit;
        model.getConcreteModel().getStateManager().withNewState(() -> {
            try {
                if (subjectTo != null) {
                    subjectTo.run();
                }
                Supplier<Runnable[]> branching = tieredFdsBranching(new Random(42L));
                DFSearch dfs = createSubSearch(branching);
                dfs.onSolution(this::recordSolutionFromCurrentState);
                SearchStatistics st = dfs.optimize(objToTighten,
                        s -> shouldAbortSubSearch(s, searchStartMillis, Long.MAX_VALUE, Integer.MAX_VALUE));
                accumulateSubSearchStats(st);
                if (st.isCompleted() && !isLimitReached()) {
                    liveStats.setCompleted();
                }
            } catch (InconsistencyException ignored) {
                liveStats.setCompleted();
            }
        });
        syncLiveStats(null);
        return liveStats;
    }

    private SearchStatistics runWithLimit(Predicate<SearchStatistics> limit, Runnable subjectTo) {
        if (!model.isConcrete()) {
            try {
                return model.runCP(() -> runWithLimitInConcreteContext(limit, subjectTo));
            } catch (InconsistencyException e) {
                resetRunMetrics();
                lastStatus = SearchStatus.UNSAT;
                liveStats.setCompleted();
                return liveStats;
            }
        }
        return runWithLimitInConcreteContext(limit, subjectTo);
    }

    private SearchStatistics runWithLimitInConcreteContext(Predicate<SearchStatistics> limit, Runnable subjectTo) {
        resetRunMetrics();
        this.activeLimit = limit;
        model.getConcreteModel().getStateManager().withNewState(() -> {
            try {
                if (subjectTo != null) {
                    subjectTo.run();
                }
                ensureCpCache();
                if (vars.isEmpty() && intervals.isEmpty() && sequences.isEmpty()) {
                    recordSolutionFromCurrentState();
                    liveStats.setCompleted();
                    lastStatus = objective == null ? SearchStatus.SAT : SearchStatus.PROVEN_OPTIMAL;
                    return;
                }
                if (phases.isEmpty()) {
                    withDefaultPhasePlan();
                }

                // Execute iterative rounds with exponentially growing round budgets until proven optimal/SAT/UNSAT or limit
                long roundBudgetMillis = 250L;
                SearchStatus globalStatus = SearchStatus.UNKNOWN;
                while (!isLimitReached()) {
                    for (Phase phase : phases) {
                        if (isLimitReached()) {
                            break;
                        }
                        if (phase.requiresFeasible && !hasAnyIncumbent()) {
                            continue;
                        }
                        long phaseBudget = Math.max(25L, Math.round(roundBudgetMillis * phase.timeShare));
                        executedPhaseNames.add(phase.name);
                        SearchStatus status = phase.search.run(phaseBudget);
                        globalStatus = mergeStatus(globalStatus, status);
                        if (objective == null && hasAnyIncumbent()) {
                            lastStatus = SearchStatus.SAT;
                            return;
                        }
                        if (status == SearchStatus.PROVEN_OPTIMAL || status == SearchStatus.UNSAT) {
                            liveStats.setCompleted();
                            lastStatus = status;
                            return;
                        }
                    }
                    roundBudgetMillis = Math.min(8000L, roundBudgetMillis * 2L);
                }
                lastStatus = globalStatus;
            } catch (InconsistencyException e) {
                lastStatus = hasAnyIncumbent() ? SearchStatus.PROVEN_OPTIMAL : SearchStatus.UNSAT;
                liveStats.setCompleted();
            }
        });
        syncLiveStats(null);
        return liveStats;
    }

    /** Returns the overall status of the most recent search run. */
    public SearchStatus status() {
        return lastStatus;
    }

    /** Returns the best incumbent solution found so far, if any. */
    public Optional<List<Integer>> bestSolution() {
        return bestSolution.map(List::copyOf);
    }

    /** Returns the best incumbent interval snapshots found so far, if any. */
    public Optional<List<IntervalSnapshot>> bestIntervalSolution() {
        return bestIntervalSolution;
    }

    /** Returns the best incumbent sequence snapshots found so far, if any. */
    public Optional<List<SeqSnapshot>> bestSeqSolution() {
        return bestSeqSolution;
    }

    /**
     * Returns the value of {@code var} in the best incumbent solution found so far, if available.
     */
    public Optional<Integer> bestValue(IntExpression var) {
        Integer v = bestVarValues.get(var);
        if (v != null) {
            return Optional.of(v);
        }
        if (var != null && var.isFixed()) {
            return Optional.of(var.min());
        }
        return Optional.empty();
    }

    /**
     * Returns the values of the given expressions in the best incumbent solution found so far.
     */
    public Optional<List<Integer>> bestValues(IntExpression... targetVars) {
        if (bestSolution.isEmpty() && bestIntervalSolution.isEmpty() && bestSeqSolution.isEmpty()) {
            return Optional.empty();
        }
        List<Integer> out = new ArrayList<>(targetVars.length);
        for (IntExpression expr : targetVars) {
            Integer v = bestVarValues.get(expr);
            if (v != null) {
                out.add(v);
            } else if (expr.isFixed()) {
                out.add(expr.min());
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(Collections.unmodifiableList(out));
    }

    /** Returns an unmodifiable view of the recorded variable values in the best solution. */
    public Map<IntExpression, Integer> bestVarValuesMap() {
        return Collections.unmodifiableMap(bestVarValues);
    }

    /** Returns the incumbent objective value, if any. */
    public Optional<Integer> bestObjectiveValue() {
        return bestObjectiveValue;
    }

    /** Returns phase names that were actually executed (in execution order). */
    public List<String> executedPhases() {
        return List.copyOf(executedPhaseNames);
    }

    Integer evaluateCurrentObjective() {
        CPIntVar objVar = currentObjCpVar();
        if (objVar != null) {
            return objVar.min();
        }
        return evaluateObjective(objective);
    }

    public void updateObjective(Integer objectiveValue) {
        if (objectiveValue == null) {
            return;
        }
        if (bestObjectiveValue.isEmpty()) {
            bestObjectiveValue = Optional.of(objectiveValue);
            logProgress("[blackbox] objective initialized to %d".formatted(objectiveValue));
            return;
        }
        int incumbent = bestObjectiveValue.get();
        if ((objective instanceof Maximization || objective instanceof SharedMaximization) && objectiveValue > incumbent) {
            bestObjectiveValue = Optional.of(objectiveValue);
            logProgress("[blackbox] objective improved %d -> %d".formatted(incumbent, objectiveValue));
        }
        if ((objective instanceof Minimization || objective instanceof SharedMinimization) && objectiveValue < incumbent) {
            bestObjectiveValue = Optional.of(objectiveValue);
            logProgress("[blackbox] objective improved %d -> %d".formatted(incumbent, objectiveValue));
        }
    }

    void postIncumbentCut() {
        postIncumbentCut(true);
    }

    void postIncumbentCut(boolean enforceFixPoint) {
        if (objective == null || bestObjectiveValue.isEmpty()) {
            return;
        }
        int incumbent = bestObjectiveValue.get();
        CPIntVar objVar = currentObjCpVar();
        if (objVar != null && cachedConcreteModel instanceof ConcreteCPModel cpm) {
            if (objective instanceof Minimization || objective instanceof SharedMinimization) {
                objVar.removeAbove(incumbent - 1);
            } else if (objective instanceof Maximization || objective instanceof SharedMaximization) {
                objVar.removeBelow(incumbent + 1);
            }
            if (enforceFixPoint) {
                cpm.solver.fixPoint();
            }
            return;
        }
        switch (objective) {
            case Minimization min -> model.add(Factory.lt(min.expr(), incumbent), enforceFixPoint);
            case SharedMinimization min -> model.add(Factory.lt(min.expr, incumbent), enforceFixPoint);
            case Maximization max -> model.add(Factory.gt(max.expr(), incumbent), enforceFixPoint);
            case SharedMaximization max -> model.add(Factory.gt(max.expr, incumbent), enforceFixPoint);
            default -> throw new IllegalStateException("Unsupported objective type: " + objective.getClass());
        }
    }

    private static IntExpression extractObjectiveExpr(Objective objective) {
        if (objective == null) {
            return null;
        }
        return switch (objective) {
            case Minimization min -> min.expr();
            case SharedMinimization min -> min.expr;
            case Maximization max -> max.expr();
            case SharedMaximization max -> max.expr;
            default -> throw new IllegalStateException("Unsupported objective type: " + objective.getClass());
        };
    }

    static Integer evaluateObjective(Objective objective) {
        if (objective == null) {
            return null;
        }
        IntExpression expr = extractObjectiveExpr(objective);
        try {
            return expr.evaluate();
        } catch (VariableNotFixedException e) {
            throw new IllegalStateException("Objective expression should be fixed on solution", e);
        }
    }

    private static SearchStatus mergeStatus(SearchStatus current, SearchStatus candidate) {
        if (candidate == SearchStatus.PROVEN_OPTIMAL || candidate == SearchStatus.UNSAT) {
            return candidate;
        }
        if (current == SearchStatus.PROVEN_OPTIMAL || current == SearchStatus.UNSAT) {
            return current;
        }
        List<SearchStatus> ranking = List.of(
                SearchStatus.UNKNOWN,
                SearchStatus.NOT_IMPROVED,
                SearchStatus.SAT,
                SearchStatus.IMPROVED);
        int currentRank = ranking.indexOf(current);
        int candidateRank = ranking.indexOf(candidate);
        if (candidateRank > currentRank) {
            return candidate;
        }
        return current;
    }

    public void updateSolution(List<Integer> solution) {
        boolean first = bestSolution.isEmpty();
        solutionCount++;
        List<Integer> copy = Collections.unmodifiableList(new ArrayList<>(solution));
        bestSolution = Optional.of(copy);
        if (copy.size() == vars.size()) {
            for (int i = 0; i < vars.size(); i++) {
                bestVarValues.put(vars.get(i), copy.get(i));
            }
        }
        if (sharedConflictBranching != null) {
            sharedConflictBranching.updateIncumbent(copy);
        }
        logProgress("[blackbox] solution #%d found%s".formatted(solutionCount, formatSolutionPreview(solution)));
        if (!first)
            logTrace("[blackbox] incumbent updated");
        for (RunnableSearch search : registeredSearches()) {
            search.updateSolution(copy);
        }
        if (incumbentListener != null) {
            incumbentListener.accept(copy, bestObjectiveValue.orElse(null));
        }
    }

    private Set<RunnableSearch> registeredSearches() {
        Set<RunnableSearch> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Phase phase : phases) {
            unique.add(phase.search);
        }
        return unique;
    }

    void logPhase(String msg) {
        if (verbosity.ordinal() >= Verbosity.PHASE.ordinal()) {
            System.out.println(msg);
        }
    }

    void logProgress(String msg) {
        if (verbosity.ordinal() >= Verbosity.PROGRESS.ordinal()) {
            System.out.println(msg);
        }
    }

    void logTrace(String msg) {
        if (verbosity.ordinal() >= Verbosity.TRACE.ordinal()) {
            System.out.println(msg);
        }
    }

    String formatStats(SearchStatistics stats) {
        return "nodes=%d fails=%d sols=%d completed=%s timeMs=%d"
                .formatted(stats.numberOfNodes(), stats.numberOfFailures(), stats.numberOfSolutions(),
                        stats.isCompleted(), stats.timeInMillis());
    }

    private String formatSolutionPreview(List<Integer> solution) {
        int maxLen = Math.min(8, solution.size());
        return " (first vars=" + solution.subList(0, maxLen) + (solution.size() > maxLen ? ", ..." : "") + ")";
    }

    public List<RunnableSearch> getRunnableSearches() {
        return phases.stream().map(p -> p.search).toList();
    }

}
