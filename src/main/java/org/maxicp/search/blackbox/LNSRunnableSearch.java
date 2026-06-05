package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.FDSModeling;
import org.maxicp.search.SearchStatistics;

import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Override
    void updateSolution(List<Integer> solution) {
        feasibleSolution = Optional.of(solution);
    }

    @Override
    public SearchStatus run(long timeLimitInMillis) {
        if (feasibleSolution.isEmpty()) {
            return SearchStatus.UNKNOWN; // no feasible solution found yet to start LNS
        } else {
            AtomicBoolean improved = new AtomicBoolean(false);
            long t0 = System.currentTimeMillis();
            List<Integer> best = new ArrayList<>(feasibleSolution.get());
            int iteration = 0;
            int adaptiveFreezeRatePercent = clampFreezeRatePercent(freezeRatePercent);
            DFSearch deterministicDfs = randomSwapProbability <= 0.0 ? createDfs(0.0, best, improved) : null;
            blackBoxSearch.logPhase("[phase lns-improvement] start budget=%dms".formatted(timeLimitInMillis));

            SearchStatistics lastStats = null;
            while ((System.currentTimeMillis() - t0) < timeLimitInMillis) {
                iteration++;
                final List<Integer> incumbent = new ArrayList<>(best);
                final int restartFreezeRatePercent = adaptiveFreezeRatePercent;
                final double restartSwapProbability = randomSwapProbabilityForIteration(iteration);
                FragmentSelector.FragmentSelectionContext selectionContext =
                        new FragmentSelector.FragmentSelectionContext(decisionVarIndices, restartFreezeRatePercent, iteration);
                final List<Integer> frozenIndices = fragmentSelector.selectFrozenIndices(selectionContext);
                DFSearch dfs = deterministicDfs != null
                        ? deterministicDfs
                        : createDfs(restartSwapProbability, best, improved);

                if (shouldLogProgressIteration(iteration)) {
                    blackBoxSearch.logProgress("[phase lns-improvement] iteration=%d incumbentKnown=%s freezeRate=%d swapProb=%.3f selector=%s frozen=%d/%d"
                            .formatted(iteration,
                                    blackBoxSearch.bestObjectiveValue().map(Object::toString).orElse("n/a"),
                                    restartFreezeRatePercent,
                                    restartSwapProbability,
                                    fragmentSelector.name(),
                                    frozenIndices.size(),
                                    decisionVarIndices.size()));
                }
                if (objective == null) {
                    lastStats = dfs.solveSubjectTo(stats -> {
                        long elapsed = System.currentTimeMillis() - t0;
                        return elapsed >= timeLimitInMillis || stats.numberOfFailures() >= failureLimitPerRestart;
                    }, () -> {
                        // Only freeze decision variables
                        for (int idx : frozenIndices) {
                            model.add(Factory.eq(vars.get(idx), incumbent.get(idx)));
                        }
                    });
                } else {
                    lastStats = dfs.optimizeSubjectTo(objective,
                            stats -> {
                                long elapsed = System.currentTimeMillis() - t0;
                                return elapsed >= timeLimitInMillis || stats.numberOfFailures() >= failureLimitPerRestart;
                            }, () -> {
                                blackBoxSearch.postIncumbentCut();
                                // Only freeze decision variables
                                for (int idx : frozenIndices) {
                                    model.add(Factory.eq(vars.get(idx), incumbent.get(idx)));
                                }
                            });
                    //System.out.println(lastStats);
                }
                if (lastStats != null) {
                    blackBoxSearch.logTrace("[phase lns-improvement] iteration=%d stats: %s"
                            .formatted(iteration, blackBoxSearch.formatStats(lastStats)));
                }
                boolean restartImproved = lastStats != null && lastStats.numberOfSolutions() > 0;
                boolean restartExhausted = lastStats != null && lastStats.isCompleted() && !restartImproved;
                boolean reachedFailureLimit = lastStats != null
                        && !restartImproved
                        && lastStats.numberOfFailures() >= failureLimitPerRestart;
                fragmentSelector.onRestartCompleted(new FragmentSelector.FragmentSelectionFeedback(
                        decisionVarIndices,
                        frozenIndices,
                        changedDecisionVarIndices(incumbent, best),
                        restartImproved,
                        restartExhausted,
                        reachedFailureLimit));
                if (restartExhausted) {
                    adaptiveFreezeRatePercent = Math.max(0, adaptiveFreezeRatePercent - ADAPTIVE_FREEZE_STEP);
                    blackBoxSearch.logProgress("[phase lns-improvement] iteration=%d exhausted without improvement, relaxing more vars (freezeRate=%d)"
                            .formatted(iteration, adaptiveFreezeRatePercent));
                } else if (reachedFailureLimit) {
                    adaptiveFreezeRatePercent = Math.min(maxUsefulFreezeRatePercent(),
                            adaptiveFreezeRatePercent + ADAPTIVE_FREEZE_STEP);
                    blackBoxSearch.logProgress("[phase lns-improvement] iteration=%d reached failure limit, freezing more vars (freezeRate=%d)"
                            .formatted(iteration, adaptiveFreezeRatePercent));
                } else if (lastStats != null && restartImproved) {
                    adaptiveFreezeRatePercent = Math.min(clampFreezeRatePercent(freezeRatePercent),
                            adaptiveFreezeRatePercent + ADAPTIVE_FREEZE_STEP / 2);
                }

                if (iteration % FREEZE_RESET_PERIOD == 0) {
                    adaptiveFreezeRatePercent = clampFreezeRatePercent(freezeRatePercent);
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


    }

    static boolean shouldLogProgressIteration(int iteration) {
        return iteration > 0 && iteration % PROGRESS_LOG_PERIOD == 0;
    }

    private int clampFreezeRatePercent(int requestedRatePercent) {
        return Math.max(0, Math.min(maxUsefulFreezeRatePercent(), requestedRatePercent));
    }

    private int maxUsefulFreezeRatePercent() {
        int n = decisionVarIndices.size();
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

    private Set<Integer> changedDecisionVarIndices(List<Integer> incumbent, List<Integer> best) {
        Set<Integer> changed = new HashSet<>();
        for (int idx : decisionVarIndices) {
            if (!Objects.equals(incumbent.get(idx), best.get(idx))) {
                changed.add(idx);
            }
        }
        return changed;
    }

    private DFSearch createDfs(double swapProbability, List<Integer> best, AtomicBoolean improved) {
        Supplier<Runnable[]> branching = new FDSModeling(vars.toArray(IntExpression[]::new));
        if (swapProbability > 0.0) {
            branching = new RandomizedBranching(branching, random, swapProbability);
        }
        DFSearch dfs = model.dfSearch(branching);
        dfs.onSolution(() -> {
            for (int i = 0; i < vars.size(); i++) {
                best.set(i, vars.get(i).min());
            }
            blackBoxSearch.updateSolution(best);
            blackBoxSearch.updateObjective(blackBoxSearch.evaluateCurrentObjective());
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