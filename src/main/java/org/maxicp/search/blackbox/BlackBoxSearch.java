package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Maximization;
import org.maxicp.modeling.symbolic.Minimization;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Phase-based black-box search orchestrator for modeling-level CP problems.
 *
 * <p>
 * A {@code BlackBoxSearch} executes a sequence of configured phases, each phase
 * being a
 * {@link RunnableSearch} with a relative time budget. The class stores and
 * propagates incumbents
 * (solution and objective value), handles early stopping criteria, and merges
 * per-phase statuses
 * into a global status.
 * </p>
 *
 * <p>
 * <b>Time-share semantics:</b>
 * </p>
 * <ul>
 * <li>Each phase has a {@code timeShare} in {@code (0,1]}.</li>
 * <li>Configured shares must sum to at most {@code 1.0} (not necessarily
 * exactly {@code 1.0}).</li>
 * <li>If shares sum to less than {@code 1.0}, the remaining budget is assigned
 * to the last phase
 * in the configured sequence when that phase is reached.</li>
 * <li>The actual per-phase budget is always capped by the currently remaining
 * global time.</li>
 * </ul>
 */
public class BlackBoxSearch {

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
    private final List<IntExpression> vars;
    private final Objective objective;
    private final List<Phase> phases = new ArrayList<>();
    private final List<String> executedPhaseNames = new ArrayList<>();
    private Verbosity verbosity = Verbosity.QUIET;
    private Optional<List<Integer>> bestSolution = Optional.empty();
    private Optional<Integer> bestObjectiveValue = Optional.empty();
    private long solutionCount = 0;

    /**
     * Creates a black-box search on decision variables with an objective
     * (optimization).
     */
    public BlackBoxSearch(ModelDispatcher model, List<IntExpression> vars, Objective objective) {
        this.model = model;
        this.vars = List.copyOf(vars);
        this.objective = objective;
    }

    /**
     * Creates a black-box search with automatic decision-variable inference.
     *
     * <p>
     * The variables are obtained from
     * {@link ModelDispatcher#getDecisionVariables()}.
     * This is convenient for LNS users who want to freeze only real decision
     * variables and avoid freezing derived expressions.
     * </p>
     */
    public BlackBoxSearch(ModelDispatcher model, Objective objective) {
        this(model, model.getDecisionVariables(), objective);
    }

    /**
     * Creates a black-box search on an array of decision variables with an
     * objective (optimization).
     */
    public BlackBoxSearch(ModelDispatcher model, IntExpression[] vars, Objective objective) {
        this(model, Arrays.asList(vars), objective);
    }

    /**
     * Creates a black-box search on decision variables without objective
     * (feasibility).
     */
    public BlackBoxSearch(ModelDispatcher model, IntExpression[] vars) {
        this(model, Arrays.asList(vars), null);
    }

    /**
     * Creates a feasibility black-box search with automatic decision-variable
     * inference.
     */
    public BlackBoxSearch(ModelDispatcher model) {
        this(model, model.getDecisionVariables(), null);
    }

    /** Sets logging verbosity for this search. */
    public BlackBoxSearch withVerbosity(Verbosity verbosity) {
        this.verbosity = verbosity == null ? Verbosity.QUIET : verbosity;
        return this;
    }

    /**
     * Registers one executable phase in the black-box plan.
     *
     * @param name             descriptive phase name used in logs and diagnostics
     * @param search           runnable implementation executed for the phase
     * @param timeShare        relative share of the global timeout allocated to
     *                         this phase for non-last phases
     *                         (must be in {@code (0,1]}; shares across phases must
     *                         sum to at most {@code 1.0})
     * @param requiresFeasible whether this phase can run only after an incumbent
     *                         exists
     * @return this search instance for fluent configuration
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
     *
     * <p>
     * Feasibility: initial exhaustive + restart phases.
     * </p>
     * <p>
     * Optimization: initial exhaustive + restart + LNS + exhaustive phases.
     * </p>
     */
    public BlackBoxSearch withDefaultPhasePlan() {
        phases.clear();

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
        addPhase("exhaustive-fds", exhaustive, (1.0 - 0.25 - 0.20 - 1.0 / 3.0), true);

        return this;
    }

    /**
     * Configures the optimization phase plan explicitly.
     *
     * <p>
     * Shares do <b>not</b> need to sum to exactly {@code 1.0}; they must sum to at
     * most {@code 1.0}.
     * Any remaining fraction is effectively left to the last configured phase via
     * remaining-time assignment.
     * </p>
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
        addPhase("exhaustive-fds", exhaustiveSearch, exhaustiveShare, true);
        return this;
    }

    /**
     * Configures the feasibility phase plan explicitly.
     *
     * <p>
     * Shares do <b>not</b> need to sum to exactly {@code 1.0}; they must sum to at
     * most {@code 1.0}.
     * Any remaining fraction is effectively left to the last configured phase via
     * remaining-time assignment.
     * </p>
     */
    public BlackBoxSearch withFeasibilityPlan(RestartPhaseOptions restart,
            ExhaustivePhaseOptions exhaustive,
            double initialExhaustiveShare,
            double restartShare,
            double exhaustiveShare) {
        validateShares(initialExhaustiveShare, restartShare, exhaustiveShare);
        phases.clear();

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

    /**
     * Executes the configured phase plan under a global timeout.
     *
     * <p>
     * Early stop conditions:
     * </p>
     * <ul>
     * <li>Feasibility mode: stops on first feasible solution.</li>
     * <li>Any mode: stops on {@link SearchStatus#PROVEN_OPTIMAL} or
     * {@link SearchStatus#UNSAT}.</li>
     * </ul>
     *
     * @param timeLimitInSeconds global timeout in seconds
     * @return merged global status across executed phases
     */
    public SearchStatus start(int timeLimitInSeconds) {
        if (phases.isEmpty()) {
            throw new IllegalStateException("No search phases configured");
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
            if (elapsed >= totalBudgetMillis) {
                logPhase("[blackbox] timeout reached before phase '%s'".formatted(phase.name));
                break;
            }

            if (phase.requiresFeasible && bestSolution.isEmpty()) {
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
            if (objective == null && bestSolution.isPresent()) {
                logPhase("[blackbox] stop early on first feasible solution");
                return SearchStatus.SAT;
            }
            if (status == SearchStatus.PROVEN_OPTIMAL || status == SearchStatus.UNSAT) {
                logPhase("[blackbox] stop early with status=%s".formatted(status));
                return status;
            }
            phaseIndex++;
        }

        logPhase("[blackbox] finished with status=%s".formatted(globalStatus));
        return globalStatus;
    }

    /** Returns the best incumbent solution found so far, if any. */
    public Optional<List<Integer>> bestSolution() {
        return bestSolution.map(List::copyOf);
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
        if (objective instanceof Maximization && objectiveValue > incumbent) {
            bestObjectiveValue = Optional.of(objectiveValue);
            logProgress("[blackbox] objective improved %d -> %d".formatted(incumbent, objectiveValue));
        }
        if (objective instanceof Minimization && objectiveValue < incumbent) {
            bestObjectiveValue = Optional.of(objectiveValue);
            logProgress("[blackbox] objective improved %d -> %d".formatted(incumbent, objectiveValue));
        }
    }

    void postIncumbentCut() {
        if (objective == null || bestObjectiveValue.isEmpty()) {
            return;
        }
        int incumbent = bestObjectiveValue.get();
        switch (objective) {
            case Minimization min -> model.add(Factory.lt(min.expr(), incumbent));
            case Maximization max -> model.add(Factory.gt(max.expr(), incumbent));
            default -> throw new IllegalStateException("Unsupported objective type: " + objective.getClass());
        }
    }

    static Integer evaluateObjective(Objective objective) {
        if (objective == null) {
            return null;
        }
        IntExpression expr = switch (objective) {
            case Minimization min -> min.expr();
            case Maximization max -> max.expr();
            default -> throw new IllegalStateException("Unsupported objective type: " + objective.getClass());
        };
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
        bestSolution = Optional.of(Collections.unmodifiableList(new ArrayList<>(solution)));
        logProgress("[blackbox] solution #%d found%s".formatted(solutionCount, formatSolutionPreview(solution)));
        if (!first)
            logTrace("[blackbox] incumbent updated");
        for (RunnableSearch search : registeredSearches()) {
            search.updateSolution(solution);
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