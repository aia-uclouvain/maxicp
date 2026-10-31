package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class LNSRunnableSearch extends RunnableSearch {

    static final int PROGRESS_LOG_PERIOD = 100;
    static final int ADAPTIVE_FREEZE_STEP = 5;
    static final int FREEZE_RESET_PERIOD = 25;

    private final ModelDispatcher model;
    private final List<IntExpression> vars;
    private final List<IntExpression> decisionVars;
    private final List<Integer> decisionVarIndices;  // indices in 'vars' of decision variables
    private Optional<List<Integer>> feasibleSolution;
    private final org.maxicp.modeling.symbolic.Objective objective;
    private final Random random;
    private final int failureLimitPerRestart;
    private final int freezeRatePercent;
    private final double randomSwapProbability;
    private final FragmentSelector fragmentSelector;

    private ConcreteModel cachedConcreteModel;
    private CPIntVar[] cpVars;

    public LNSRunnableSearch(BlackBoxSearch blackBoxSearch, ModelDispatcher model, List<IntExpression> vars,
                             org.maxicp.modeling.symbolic.Objective objective,
                             int failureLimitPerRestart,
                             int freezeRatePercent,
                             double randomSwapProbability,
                             long randomSeed) {
        this(blackBoxSearch, model, vars, objective, failureLimitPerRestart, freezeRatePercent,
                randomSwapProbability, randomSeed, BlackBoxSearch.FragmentSelectionStrategy.RANDOM_UNIFORM);
    }

    public LNSRunnableSearch(BlackBoxSearch blackBoxSearch, ModelDispatcher model, List<IntExpression> vars,
                             org.maxicp.modeling.symbolic.Objective objective,
                             int failureLimitPerRestart,
                             int freezeRatePercent,
                             double randomSwapProbability,
                             long randomSeed,
                             BlackBoxSearch.FragmentSelectionStrategy fragmentSelectionStrategy) {
        super(blackBoxSearch);
        this.model = model;
        this.vars = List.copyOf(vars);
        this.decisionVars = model.getDecisionVariables();
        this.decisionVarIndices = computeDecisionVarIndices();
        this.objective = objective;
        this.feasibleSolution = Optional.empty();
        this.failureLimitPerRestart = failureLimitPerRestart;
        this.freezeRatePercent = freezeRatePercent;
        this.randomSwapProbability = randomSwapProbability;
        this.random = new Random(randomSeed);
        this.fragmentSelector = buildFragmentSelector(fragmentSelectionStrategy);
    }

    /**
     * Compute the indices (in 'vars') of each decision variable.
     * This allows us to map decision variables back to their positions
     * in the incumbent solution list.
     */
    private List<Integer> computeDecisionVarIndices() {
        List<Integer> indices = new ArrayList<>();
        IdentityHashMap<IntExpression, Integer> varToIndex = new IdentityHashMap<>();
        for (int i = 0; i < vars.size(); i++) {
            varToIndex.put(vars.get(i), i);
        }
        for (IntExpression dvar : decisionVars) {
            Integer idx = varToIndex.get(dvar);
            if (idx != null) {
                indices.add(idx);
            }
        }
        if (indices.isEmpty()) {
            // Fallback: when inference misses this model shape, keep LNS operational on provided vars.
            for (int i = 0; i < vars.size(); i++) {
                indices.add(i);
            }
        }
        return List.copyOf(indices);
    }

    private List<Integer> computePrimaryDecisionVarIndices(ModelStructureAnalyzer structure) {
        List<Integer> primary = new ArrayList<>(decisionVarIndices.size());
        for (int idx : decisionVarIndices) {
            if (!structure.isSecondary(idx)) {
                primary.add(idx);
            }
        }
        return primary.isEmpty() ? decisionVarIndices : List.copyOf(primary);
    }

    private void ensureCpCache() {
        if (!model.isConcrete()) {
            return;
        }
        ConcreteModel cm = model.getConcreteModel();
        if (cm != cachedConcreteModel) {
            cachedConcreteModel = cm;
            if (cm instanceof ConcreteCPModel cpm) {
                cpVars = new CPIntVar[vars.size()];
                for (int i = 0; i < vars.size(); i++) {
                    cpVars[i] = cpm.getCPVar(vars.get(i));
                }
            } else {
                cpVars = null;
            }
        }
    }

    @Override
    void updateSolution(List<Integer> solution) {
        feasibleSolution = Optional.of(solution);
    }

    @Override
    public SearchStatus run(long timeLimitInMillis) {
        if (!blackBoxSearch.intervals().isEmpty()) {
            return new SchedulingLNSRunnableSearch(
                    blackBoxSearch, model, blackBoxSearch.intervals(), vars, objective,
                    failureLimitPerRestart, freezeRatePercent, random.nextLong())
                    .run(timeLimitInMillis);
        }
        if (feasibleSolution.isEmpty()) {
            return SearchStatus.UNKNOWN; // no feasible solution found yet to start LNS
        }
        ensureCpCache();
        ModelStructureAnalyzer structure = blackBoxSearch.structureAnalyzer();
        List<Integer> primaryDecisionVars = computePrimaryDecisionVarIndices(structure);
        ConflictDrivenBranching cdBranching = blackBoxSearch.conflictDrivenBranching(random);
        ImpactBasedFragmentSelector impactSelector = new ImpactBasedFragmentSelector(
                random, computePrimaryStructuralImpacts(structure, primaryDecisionVars));

        AtomicBoolean improved = new AtomicBoolean(false);
        long t0 = System.currentTimeMillis();
        List<Integer> best = new ArrayList<>(feasibleSolution.get());
        int iteration = 0;
        int stagnationIterations = 0;
        int adaptiveFreezeRatePercent = clampFreezeRatePercent(freezeRatePercent, primaryDecisionVars.size());

        // Bandit weights for [0: primarySelector, 1: impactSelector, 2: graphBall, 3: contiguousWindow]
        double[] opWeights = new double[]{4.0, 2.0, 1.5, 1.5};
        int minFreezeRate = primaryDecisionVars.size() > 4 ? Math.min(freezeRatePercent, 45) : 0;

        blackBoxSearch.logPhase("[phase lns-improvement] start budget=%dms".formatted(timeLimitInMillis));

        SearchStatistics lastStats = null;
        while ((System.currentTimeMillis() - t0) < timeLimitInMillis && !blackBoxSearch.isLimitReached()) {
            iteration++;
            final List<Integer> incumbent = new ArrayList<>(best);
            final int restartFreezeRatePercent = adaptiveFreezeRatePercent;
            final double restartSwapProbability = randomSwapProbabilityForIteration(iteration);

            int op = iteration <= 2 ? 0 : selectOperator(opWeights);
            final List<Integer> frozenIndices = selectPortfolioFrozenIndices(
                    op, primaryDecisionVars, structure, cdBranching, impactSelector, restartFreezeRatePercent, iteration);

            final int currentFailLimit = computeIterationFailureLimit(iteration, stagnationIterations);
            DFSearch dfs = createIterationDfs(iteration, restartSwapProbability, cdBranching, best, improved);

            if (shouldLogProgressIteration(iteration)) {
                blackBoxSearch.logProgress("[phase lns-improvement] iteration=%d incumbentKnown=%s freezeRate=%d swapProb=%.3f selector=%s frozen=%d/%d"
                        .formatted(iteration,
                                blackBoxSearch.bestObjectiveValue().map(Object::toString).orElse("n/a"),
                                restartFreezeRatePercent,
                                restartSwapProbability,
                                fragmentSelector.name(),
                                frozenIndices.size(),
                                primaryDecisionVars.size()));
            }
            if (objective == null) {
                lastStats = dfs.solveSubjectTo(
                        stats -> blackBoxSearch.shouldAbortSubSearch(stats, t0, timeLimitInMillis, currentFailLimit),
                        () -> freezeVariablesBatch(frozenIndices, incumbent, false));
            } else {
                lastStats = dfs.optimizeSubjectTo(objective,
                        stats -> blackBoxSearch.shouldAbortSubSearch(stats, t0, timeLimitInMillis, currentFailLimit),
                        () -> freezeVariablesBatch(frozenIndices, incumbent, true));
            }
            blackBoxSearch.accumulateSubSearchStats(lastStats);
            if (lastStats != null) {
                blackBoxSearch.logTrace("[phase lns-improvement] iteration=%d stats: %s"
                        .formatted(iteration, blackBoxSearch.formatStats(lastStats)));
            }
            boolean restartImproved = lastStats != null && lastStats.numberOfSolutions() > 0;
            boolean restartExhausted = lastStats != null && lastStats.isCompleted() && !restartImproved
                    && lastStats.numberOfFailures() < currentFailLimit;
            boolean reachedFailureLimit = lastStats != null
                    && !restartImproved
                    && lastStats.numberOfFailures() >= currentFailLimit;

            Set<Integer> changed = restartImproved ? changedVarIndices(primaryDecisionVars, incumbent, best) : Set.of();
            FragmentSelector.FragmentSelectionFeedback feedback = new FragmentSelector.FragmentSelectionFeedback(
                    primaryDecisionVars,
                    frozenIndices,
                    changed,
                    restartImproved,
                    restartExhausted,
                    reachedFailureLimit);
            if (op == 0) {
                fragmentSelector.onRestartCompleted(feedback);
            } else if (op == 1) {
                impactSelector.onRestartCompleted(feedback);
            }
            updateOperatorWeight(opWeights, op, restartImproved);

            if (restartImproved) {
                stagnationIterations = 0;
                adaptiveFreezeRatePercent = Math.min(
                        clampFreezeRatePercent(freezeRatePercent, primaryDecisionVars.size()),
                        adaptiveFreezeRatePercent + ADAPTIVE_FREEZE_STEP / 2);
            } else {
                stagnationIterations++;
                if (restartExhausted) {
                    int step = (lastStats.numberOfFailures() <= Math.max(2, currentFailLimit / 5))
                            ? ADAPTIVE_FREEZE_STEP * 2
                            : ADAPTIVE_FREEZE_STEP;
                    adaptiveFreezeRatePercent = Math.max(minFreezeRate, adaptiveFreezeRatePercent - step);
                    blackBoxSearch.logProgress("[phase lns-improvement] iteration=%d exhausted without improvement, relaxing more vars (freezeRate=%d)"
                            .formatted(iteration, adaptiveFreezeRatePercent));
                } else if (reachedFailureLimit) {
                    adaptiveFreezeRatePercent = Math.min(
                            maxUsefulFreezeRatePercent(primaryDecisionVars.size()),
                            adaptiveFreezeRatePercent + ADAPTIVE_FREEZE_STEP);
                    blackBoxSearch.logProgress("[phase lns-improvement] iteration=%d reached failure limit, freezing more vars (freezeRate=%d)"
                            .formatted(iteration, adaptiveFreezeRatePercent));
                }
            }

            if (stagnationIterations > 0 && stagnationIterations % FREEZE_RESET_PERIOD == 0) {
                int targetReset = Math.min(freezeRatePercent, 85);
                adaptiveFreezeRatePercent = clampFreezeRatePercent(targetReset, primaryDecisionVars.size());
                blackBoxSearch.logProgress("[phase lns-improvement] iteration=%d adaptive freeze reset (freezeRate=%d)"
                        .formatted(iteration, adaptiveFreezeRatePercent));
            }
        }

        if (improved.get()) {
            blackBoxSearch.logPhase("[phase lns-improvement] end status=%s".formatted(SearchStatus.IMPROVED));
            return SearchStatus.IMPROVED;
        } else if (lastStats != null && lastStats.isCompleted()) {
            SearchStatus status = lastStats.numberOfSolutions() > 0 ? SearchStatus.SAT : SearchStatus.NOT_IMPROVED;
            blackBoxSearch.logPhase("[phase lns-improvement] end status=%s".formatted(status));
            return status;
        } else {
            blackBoxSearch.logPhase("[phase lns-improvement] end status=%s".formatted(SearchStatus.NOT_IMPROVED));
            return SearchStatus.NOT_IMPROVED;
        }
    }

    private void freezeVariablesBatch(List<Integer> frozenIndices, List<Integer> incumbent, boolean withIncumbentCut) {
        ensureCpCache();
        if (cpVars != null && cachedConcreteModel instanceof ConcreteCPModel cpm) {
            if (withIncumbentCut) {
                blackBoxSearch.postIncumbentCut(false);
            }
            for (int i = 0, n = frozenIndices.size(); i < n; i++) {
                int idx = frozenIndices.get(i);
                cpVars[idx].fix(incumbent.get(idx));
            }
            cpm.solver.fixPoint();
        } else {
            if (withIncumbentCut) {
                blackBoxSearch.postIncumbentCut(false);
            }
            for (int idx : frozenIndices) {
                model.add(Factory.eq(vars.get(idx), incumbent.get(idx)), false);
            }
            model.add(new org.maxicp.modeling.constraints.NoOpConstraint(), true);
        }
    }

    private int computeIterationFailureLimit(int iteration, int stagnationIterations) {
        if (stagnationIterations >= 20 && iteration % 5 == 0) {
            return failureLimitPerRestart * 2;
        }
        return failureLimitPerRestart;
    }

    private int selectOperator(double[] weights) {
        double sum = 0.0;
        for (double w : weights) {
            sum += w;
        }
        double r = random.nextDouble() * sum;
        double acc = 0.0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (r <= acc) {
                return i;
            }
        }
        return 0;
    }

    private void updateOperatorWeight(double[] weights, int op, boolean improved) {
        if (improved) {
            weights[op] = Math.min(25.0, weights[op] * 0.9 + 5.0);
        } else {
            weights[op] = Math.max(0.3, weights[op] * 0.98);
        }
    }

    private List<Integer> selectPortfolioFrozenIndices(
            int op,
            List<Integer> primaryVars,
            ModelStructureAnalyzer structure,
            ConflictDrivenBranching cdBranching,
            ImpactBasedFragmentSelector impactSelector,
            int ratePercent,
            int iteration) {
        int n = primaryVars.size();
        if (n <= 1 || ratePercent <= 0) {
            return List.of();
        }
        List<Integer> rawFrozen = switch (op) {
            case 0 -> {
                FragmentSelector.FragmentSelectionContext ctx =
                        new FragmentSelector.FragmentSelectionContext(primaryVars, ratePercent, iteration);
                List<Integer> selected = fragmentSelector.selectFrozenIndices(ctx);
                List<Integer> filtered = new ArrayList<>(selected.size());
                for (int idx : selected) {
                    if (!structure.isSecondary(idx)) {
                        filtered.add(idx);
                    }
                }
                yield filtered;
            }
            case 1 -> {
                FragmentSelector.FragmentSelectionContext ctx =
                        new FragmentSelector.FragmentSelectionContext(primaryVars, ratePercent, iteration);
                yield impactSelector.selectFrozenIndices(ctx);
            }
            case 2 -> selectGraphNeighborhoodFrozen(primaryVars, structure, cdBranching, ratePercent);
            default -> selectContiguousWindowFrozen(primaryVars, ratePercent);
        };
        if (rawFrozen.size() >= n) {
            List<Integer> copy = new ArrayList<>(rawFrozen);
            copy.remove(random.nextInt(copy.size()));
            return copy;
        }
        return rawFrozen;
    }

    private List<Integer> selectGraphNeighborhoodFrozen(
            List<Integer> primaryVars,
            ModelStructureAnalyzer structure,
            ConflictDrivenBranching cdBranching,
            int ratePercent) {
        int n = primaryVars.size();
        int freezeCount = Math.max(0, Math.min(n - 1, (int) Math.round(n * (ratePercent / 100.0))));
        int relaxCount = n - freezeCount;
        if (relaxCount >= n) {
            return List.of();
        }

        // Choose a seed variable weighted by conflict weight
        int seed = primaryVars.get(random.nextInt(n));
        for (int t = 0; t < 3; t++) {
            int cand = primaryVars.get(random.nextInt(n));
            if (cdBranching.getVarWeight(cand) > cdBranching.getVarWeight(seed)) {
                seed = cand;
            }
        }

        boolean[] relaxed = new boolean[vars.size()];
        int relaxedSoFar = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(seed);
        relaxed[seed] = true;
        relaxedSoFar++;

        while (!queue.isEmpty() && relaxedSoFar < relaxCount) {
            int u = queue.poll();
            int[] adj = structure.neighbors(u);
            if (adj.length > 0) {
                int offset = random.nextInt(adj.length);
                for (int k = 0; k < adj.length; k++) {
                    if (relaxedSoFar >= relaxCount) break;
                    int v = adj[(offset + k) % adj.length];
                    if (!structure.isSecondary(v) && !relaxed[v]) {
                        relaxed[v] = true;
                        relaxedSoFar++;
                        queue.add(v);
                    }
                }
            }
        }

        // If graph component was smaller than relaxCount, relax additional random primary vars
        while (relaxedSoFar < relaxCount) {
            int v = primaryVars.get(random.nextInt(n));
            if (!relaxed[v]) {
                relaxed[v] = true;
                relaxedSoFar++;
            }
        }

        List<Integer> frozen = new ArrayList<>(freezeCount);
        for (int idx : primaryVars) {
            if (!relaxed[idx]) {
                frozen.add(idx);
            }
        }
        return frozen;
    }

    private List<Integer> selectContiguousWindowFrozen(List<Integer> primaryVars, int ratePercent) {
        int n = primaryVars.size();
        int freezeCount = Math.max(0, Math.min(n - 1, (int) Math.round(n * (ratePercent / 100.0))));
        int relaxCount = n - freezeCount;
        if (relaxCount >= n) {
            return List.of();
        }
        int start = random.nextInt(n);
        boolean[] relaxedPos = new boolean[n];
        for (int k = 0; k < relaxCount; k++) {
            relaxedPos[(start + k) % n] = true;
        }
        List<Integer> frozen = new ArrayList<>(freezeCount);
        for (int i = 0; i < n; i++) {
            if (!relaxedPos[i]) {
                frozen.add(primaryVars.get(i));
            }
        }
        return frozen;
    }

    static boolean shouldLogProgressIteration(int iteration) {
        return iteration > 0 && iteration % PROGRESS_LOG_PERIOD == 0;
    }

    private int clampFreezeRatePercent(int requestedRatePercent, int nVars) {
        return Math.max(0, Math.min(maxUsefulFreezeRatePercent(nVars), requestedRatePercent));
    }

    private int maxUsefulFreezeRatePercent(int n) {
        if (n == 0) {
            return 0;
        }
        if (n == 1) {
            return 100;
        }
        // Keep at least one relaxed variable under round(n * rate / 100).
        return Math.max(0, (int) Math.floor((100.0 * (n - 0.5) / n) - 1e-9));
    }

    private List<List<Integer>> computeGroupIndexLists() {
        IdentityHashMap<IntExpression, Integer> varToIndex = new IdentityHashMap<>();
        for (int i = 0; i < vars.size(); i++) {
            varToIndex.put(vars.get(i), i);
        }

        List<List<Integer>> groupIndices = new ArrayList<>();
        for (List<IntExpression> group : model.getModel().getVariableGroups()) {
            List<Integer> indices = new ArrayList<>();
            for (IntExpression expr : group) {
                Integer idx = varToIndex.get(expr);
                if (idx != null) {
                    indices.add(idx);
                }
            }
            if (!indices.isEmpty()) {
                groupIndices.add(List.copyOf(indices));
            }
        }
        return List.copyOf(groupIndices);
    }

    private FragmentSelector buildFragmentSelector(BlackBoxSearch.FragmentSelectionStrategy strategy) {
        BlackBoxSearch.FragmentSelectionStrategy safeStrategy = strategy == null
                ? BlackBoxSearch.FragmentSelectionStrategy.RANDOM_UNIFORM
                : strategy;
        List<List<Integer>> groups = computeGroupIndexLists();
        if (safeStrategy == BlackBoxSearch.FragmentSelectionStrategy.IMPACT_GUIDED && !groups.isEmpty()) {
            safeStrategy = BlackBoxSearch.FragmentSelectionStrategy.GROUP_COHERENT;
        } else if (safeStrategy == BlackBoxSearch.FragmentSelectionStrategy.GROUP_COHERENT && groups.isEmpty()) {
            safeStrategy = BlackBoxSearch.FragmentSelectionStrategy.IMPACT_GUIDED;
        }
        return switch (safeStrategy) {
            case IMPACT_GUIDED -> new ImpactBasedFragmentSelector(random, computeStructuralImpactScores());
            case RANDOM_UNIFORM -> new RandomFragmentSelector(random);
            case GROUP_COHERENT -> new GroupCoherentFragmentSelector(random, groups);
        };
    }

    private Map<Integer, Double> computePrimaryStructuralImpacts(
            ModelStructureAnalyzer structure,
            List<Integer> primaryVars) {
        Map<Integer, Double> impact = new HashMap<>(primaryVars.size() * 2);
        for (int idx : primaryVars) {
            impact.put(idx, structure.structuralWeight(idx));
        }
        return impact;
    }

    private Map<Integer, Double> computeStructuralImpactScores() {
        IdentityHashMap<IntExpression, Integer> varToIndex = new IdentityHashMap<>();
        for (int idx : decisionVarIndices) {
            varToIndex.put(vars.get(idx), idx);
        }

        Map<Integer, Double> impact = new HashMap<>();
        for (Constraint constraint : model.getConstraints()) {
            Set<Integer> involved = collectDecisionVarIndices(constraint.scope(), varToIndex);
            if (involved.isEmpty()) {
                continue;
            }
            double weight = 1.0 / involved.size();
            for (int idx : involved) {
                impact.merge(idx, weight, Double::sum);
            }
        }
        return impact;
    }

    private Set<Integer> collectDecisionVarIndices(Collection<? extends Expression> expressions,
                                                   IdentityHashMap<IntExpression, Integer> varToIndex) {
        Set<Integer> involved = new HashSet<>();
        IdentityHashMap<Expression, Boolean> seen = new IdentityHashMap<>();
        Deque<Expression> stack = new ArrayDeque<>(expressions);
        while (!stack.isEmpty()) {
            Expression expr = stack.removeLast();
            if (seen.putIfAbsent(expr, Boolean.TRUE) != null) {
                continue;
            }
            if (expr instanceof IntExpression intExpr) {
                Integer idx = varToIndex.get(intExpr);
                if (idx != null) {
                    involved.add(idx);
                }
            }
            for (Expression child : expr.subexpressions()) {
                stack.addLast(child);
            }
        }
        return involved;
    }

    private Set<Integer> changedVarIndices(List<Integer> targetIndices, List<Integer> incumbent, List<Integer> best) {
        Set<Integer> changed = new HashSet<>();
        for (int idx : targetIndices) {
            if (!Objects.equals(incumbent.get(idx), best.get(idx))) {
                changed.add(idx);
            }
        }
        return changed;
    }

    private DFSearch createIterationDfs(
            int iteration,
            double swapProbability,
            ConflictDrivenBranching cdBranching,
            List<Integer> best,
            AtomicBoolean improved) {
        Supplier<Runnable[]> branching;
        if (iteration % 4 == 3) {
            branching = blackBoxSearch.tieredFdsBranching(random);
            if (swapProbability > 0.0) {
                branching = new RandomizedBranching(branching, random, swapProbability);
            }
        } else {
            ConflictDrivenBranching.ValueStrategy valStrat = switch (iteration % 3) {
                case 0 -> ConflictDrivenBranching.ValueStrategy.OBJECTIVE_AND_PHASE;
                case 1 -> ConflictDrivenBranching.ValueStrategy.SOLUTION_AND_OBJECTIVE;
                default -> ConflictDrivenBranching.ValueStrategy.PHASE_SAVING;
            };
            double noise = swapProbability > 0.0 ? Math.min(0.10, swapProbability * 0.15) : 0.01;
            cdBranching.configureRestart(valStrat, noise, true);
            branching = cdBranching;
        }
        DFSearch dfs = blackBoxSearch.createSubSearch(branching);
        dfs.onSolution(() -> {
            ensureCpCache();
            if (cpVars != null) {
                for (int i = 0; i < vars.size(); i++) {
                    best.set(i, cpVars[i].min());
                }
            } else {
                for (int i = 0; i < vars.size(); i++) {
                    best.set(i, vars.get(i).min());
                }
            }
            blackBoxSearch.recordSolutionFromCurrentState();
            improved.set(true);
        });
        return dfs;
    }

    // Alternate between aggressive randomization and near-deterministic branching across LNS restarts.
    double randomSwapProbabilityForIteration(int iteration) {
        if (randomSwapProbability <= 0.0) {
            return 0.0;
        }
        double low = Math.min(0.02, randomSwapProbability * 0.10);
        double high = Math.max(0.90, randomSwapProbability);
        return iteration % 2 == 0 ? Math.min(1.0, high) : Math.max(0.0, low);
    }

    public FragmentSelector getFragmentSelector() {
        return fragmentSelector;
    }
}
