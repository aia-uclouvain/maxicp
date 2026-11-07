/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Dedicated runnable search for models containing sequence variables ({@link SeqVar}).
 *
 * <p>Separates primal solution construction from conflict-driven optimality proving:
 * <ol>
 *   <li><b>Stage 1 — Constructive Primal Heuristic ({@link org.maxicp.search.PrimalSeqVar}):</b>
 *       When no incumbent solution exists yet (or for feasibility problems), executes a fast
 *       First-Fail + 2-Regret binary constructive search with Luby restarts and light diversification
 *       noise to rapidly discover an initial feasible solution.</li>
 *   <li><b>Stage 2 — Exhaustive Optimality / Infeasibility Proving ({@link org.maxicp.search.FDSSeqVar}):</b>
 *       Executes Failure-Directed Search with the Lukacs sequential slot-splitting space-contraction
 *       estimator and short-detour lazy choice pool to close the search tree and prove optimality
 *       or infeasibility.</li>
 * </ol>
 */
public class SequenceRunnableSearch extends RunnableSearch {

    private static final int DEFAULT_BASE_FAILURE_LIMIT = 64;

    private final BlackBoxSearch owner;
    private final ModelDispatcher model;
    private final List<SeqVar> sequences;
    private final List<IntExpression> vars;
    private final Objective objective;
    private final Random random;
    private int lubyIndex = 1;

    public SequenceRunnableSearch(
            BlackBoxSearch owner,
            ModelDispatcher model,
            List<SeqVar> sequences,
            List<IntExpression> vars,
            Objective objective,
            long randomSeed) {
        super(owner);
        this.owner = owner;
        this.model = model;
        this.sequences = List.copyOf(sequences);
        this.vars = List.copyOf(vars);
        this.objective = objective;
        this.random = new Random(randomSeed);
    }

    @Override
    void updateSolution(List<Integer> solution) {
        // sequence snapshots are maintained on BlackBoxSearch
    }

    @Override
    public SearchStatus run(long timeBudgetMillis) {
        if (!model.isConcrete()) {
            return model.runCP(() -> runInConcreteContext(timeBudgetMillis));
        }
        return runInConcreteContext(timeBudgetMillis);
    }

    private SearchStatus runInConcreteContext(long timeBudgetMillis) {
        long startMillis = System.currentTimeMillis();
        long budgetMillis = Math.max(1L, timeBudgetMillis);
        owner.ensureCpCache();

        if (sequences.isEmpty() && vars.isEmpty()) {
            owner.recordSolutionFromCurrentState();
            return objective == null ? SearchStatus.SAT : SearchStatus.PROVEN_OPTIMAL;
        }

        boolean hadIncumbentAtStart = hasIncumbent();
        Integer startBestObj = owner.bestObjectiveValue().orElse(null);

        // Stage 1: Constructive Primal Heuristic with Luby restarts (when no incumbent exists yet, or for satisfaction)
        if (!hadIncumbentAtStart) {
            long stage1Budget = objective == null ? budgetMillis : Math.max(40L, (budgetMillis * 3) / 5);
            int localRestartsAfterFirstSol = 0;

            while (!isExpired(startMillis, stage1Budget) && !owner.isLimitReached()) {
                int failCutoff = DEFAULT_BASE_FAILURE_LIMIT * luby(lubyIndex++);
                double noiseProb = (lubyIndex <= 2) ? 0.0 : 0.25;
                SearchStatus st = runSinglePass(
                        owner.sequencePrimalBranching(random, noiseProb),
                        startMillis,
                        stage1Budget,
                        failCutoff);
                if (st == SearchStatus.PROVEN_OPTIMAL || st == SearchStatus.UNSAT) {
                    return st;
                }
                if (objective == null && hasIncumbent()) {
                    return SearchStatus.SAT;
                }
                if (hasIncumbent()) {
                    localRestartsAfterFirstSol++;
                    if (localRestartsAfterFirstSol >= 1) {
                        break;
                    }
                }
            }
        }

        // Stage 2: Exhaustive FDSSeqVar to prove optimality or infeasibility
        if (!isExpired(startMillis, budgetMillis) && !owner.isLimitReached()) {
            SearchStatus fdsStatus = runSinglePass(
                    owner.sequenceFdsBranching(random),
                    startMillis,
                    budgetMillis,
                    Integer.MAX_VALUE);
            if (fdsStatus == SearchStatus.PROVEN_OPTIMAL || fdsStatus == SearchStatus.UNSAT) {
                return fdsStatus;
            }
            if (objective == null && hasIncumbent()) {
                return SearchStatus.SAT;
            }
        }

        return summarizeStatus(hadIncumbentAtStart, startBestObj);
    }

    private SearchStatus runSinglePass(
            Supplier<Runnable[]> branching,
            long phaseStartMillis,
            long subBudgetMillis,
            int failureCutoff) {
        final boolean[] completedWithoutAbort = {false};
        final boolean[] aborted = {false};
        final boolean[] rootInconsistent = {false};

        model.getConcreteModel().getStateManager().withNewState(() -> {
            try {
                owner.postIncumbentCut();
            } catch (InconsistencyException e) {
                rootInconsistent[0] = true;
                return;
            }
            DFSearch dfs = owner.createSubSearch(branching);
            dfs.onSolution(owner::recordSolutionFromCurrentState);
            SearchStatistics stats;
            try {
                if (objective == null) {
                    stats = dfs.solve(s -> {
                        if (hasIncumbent()) {
                            aborted[0] = true;
                            return true;
                        }
                        boolean stop = owner.shouldAbortSubSearch(s, phaseStartMillis, subBudgetMillis, failureCutoff);
                        if (stop) {
                            aborted[0] = true;
                        }
                        return stop;
                    });
                } else {
                    org.maxicp.search.Objective cpObj = model.getConcreteModel().createObjective(objective);
                    stats = dfs.optimize(cpObj, s -> {
                        boolean stop = owner.shouldAbortSubSearch(s, phaseStartMillis, subBudgetMillis, failureCutoff);
                        if (stop) {
                            aborted[0] = true;
                        }
                        return stop;
                    });
                }
                owner.accumulateSubSearchStats(stats);
                if (stats.isCompleted() && !aborted[0] && !owner.isLimitReached()) {
                    completedWithoutAbort[0] = true;
                }
            } catch (InconsistencyException e) {
                completedWithoutAbort[0] = true;
            }
        });

        if (rootInconsistent[0] || completedWithoutAbort[0]) {
            if (objective == null) {
                return hasIncumbent() ? SearchStatus.SAT : SearchStatus.UNSAT;
            }
            return hasIncumbent() ? SearchStatus.PROVEN_OPTIMAL : SearchStatus.UNSAT;
        }
        return SearchStatus.UNKNOWN;
    }

    private SearchStatus summarizeStatus(boolean hadIncumbentAtStart, Integer startBestObj) {
        if (!hasIncumbent()) {
            return SearchStatus.UNKNOWN;
        }
        if (objective == null) {
            return SearchStatus.SAT;
        }
        if (!hadIncumbentAtStart) {
            return SearchStatus.IMPROVED;
        }
        Integer nowBest = owner.bestObjectiveValue().orElse(null);
        if (startBestObj != null && nowBest != null && !nowBest.equals(startBestObj)) {
            return SearchStatus.IMPROVED;
        }
        return SearchStatus.NOT_IMPROVED;
    }

    private boolean hasIncumbent() {
        return owner.bestSeqSolution().isPresent() || owner.bestSolution().isPresent();
    }

    private static boolean isExpired(long startMillis, long budgetMillis) {
        return (System.currentTimeMillis() - startMillis) >= budgetMillis;
    }

    private static int luby(int i) {
        int k = 1;
        while ((1 << k) - 1 < i) {
            k++;
        }
        if (i == (1 << k) - 1) {
            return 1 << (k - 1);
        }
        return luby(i - (1 << (k - 1)) + 1);
    }
}
