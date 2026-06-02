package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.FDSModeling;
import org.maxicp.search.SearchStatistics;

import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

public class LNSRunnableSearch extends RunnableSearch {

    static final int PROGRESS_LOG_PERIOD = 100;

    private final ModelDispatcher model;
    private final List<IntExpression> vars;
    private Optional<List<Integer>> feasibleSolution;
    private final org.maxicp.modeling.symbolic.Objective objective;
    private final Random random;
    private final int failureLimitPerRestart;
    private final int freezeRatePercent;
    private final double randomSwapProbability;

    public LNSRunnableSearch(BlackBoxSearch blackBoxSearch, ModelDispatcher model, List<IntExpression> vars,
                             org.maxicp.modeling.symbolic.Objective objective,
                             int failureLimitPerRestart,
                             int freezeRatePercent,
                             double randomSwapProbability,
                             long randomSeed) {
        super(blackBoxSearch);
        this.model = model;
        this.vars = List.copyOf(vars);
        this.objective = objective;
        this.feasibleSolution = Optional.empty();
        this.failureLimitPerRestart = failureLimitPerRestart;
        this.freezeRatePercent = freezeRatePercent;
        this.randomSwapProbability = randomSwapProbability;
        this.random = new Random(randomSeed);
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
            blackBoxSearch.logPhase("[phase lns-improvement] start budget=%dms".formatted(timeLimitInMillis));

            Supplier<Runnable[]> branching = new FDSModeling(vars.toArray(IntExpression[]::new));
            if (randomSwapProbability > 0.0) {
                branching = new RandomizedBranching(branching, random, randomSwapProbability);
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

            SearchStatistics lastStats = null;
            while ((System.currentTimeMillis() - t0) < timeLimitInMillis) {
                iteration++;
                final List<Integer> incumbent = new ArrayList<>(best);
                if (shouldLogProgressIteration(iteration)) {
                    blackBoxSearch.logProgress("[phase lns-improvement] iteration=%d incumbentKnown=%s"
                            .formatted(iteration, blackBoxSearch.bestObjectiveValue().map(Object::toString).orElse("n/a")));
                }
                if (objective == null) {
                    lastStats = dfs.solveSubjectTo(stats -> {
                        long elapsed = System.currentTimeMillis() - t0;
                        return elapsed >= timeLimitInMillis || stats.numberOfFailures() >= failureLimitPerRestart;
                    }, () -> {
                        for (int i = 0; i < vars.size(); i++) {
                            if (random.nextInt(100) < freezeRatePercent) {
                                model.add(Factory.eq(vars.get(i), incumbent.get(i)));
                            }
                        }
                    });
                } else {
                    lastStats = dfs.optimizeSubjectTo(objective,
                            stats -> {
                                long elapsed = System.currentTimeMillis() - t0;
                                return elapsed >= timeLimitInMillis || stats.numberOfFailures() >= failureLimitPerRestart;
                            }, () -> {
                                blackBoxSearch.postIncumbentCut();
                                for (int i = 0; i < vars.size(); i++) {
                                    if (random.nextInt(100) < freezeRatePercent) {
                                        model.add(Factory.eq(vars.get(i), incumbent.get(i)));
                                    }
                                }
                            });
                }
                                if (lastStats != null) {
                                    blackBoxSearch.logTrace("[phase lns-improvement] iteration=%d stats: %s"
                                            .formatted(iteration, blackBoxSearch.formatStats(lastStats)));
                                }
                if (lastStats != null && lastStats.isCompleted() && !improved.get()) {
                    break;
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

}