package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class DFSRunnableSearch extends RunnableSearch {

    private final ModelDispatcher model;
    private final List<IntExpression> vars;
    private final org.maxicp.modeling.symbolic.Objective objective;
    private final double randomSwapProbability;
    private final Random random;

    public DFSRunnableSearch(BlackBoxSearch blackBoxSearch, ModelDispatcher model, List<IntExpression> vars,
                             org.maxicp.modeling.symbolic.Objective objective,
                             double randomSwapProbability,
                             long randomSeed) {
        super(blackBoxSearch);
        this.model = model;
        this.vars = List.copyOf(vars);
        this.objective = objective;
        this.randomSwapProbability = randomSwapProbability;
        this.random = new Random(randomSeed);
    }

    @Override
    void updateSolution(List<Integer> solution) {
        // nothing to maintain locally
    }

    @Override
    public SearchStatus run(long timeLimitInMillis) {
        if (!blackBoxSearch.sequences().isEmpty()) {
            return new SequenceRunnableSearch(
                    blackBoxSearch, model, blackBoxSearch.sequences(), vars, objective, random.nextLong())
                    .run(timeLimitInMillis);
        }
        if (!blackBoxSearch.intervals().isEmpty()) {
            return new SchedulingRunnableSearch(
                    blackBoxSearch, model, blackBoxSearch.intervals(), vars, objective, random.nextLong())
                    .run(timeLimitInMillis);
        }

        long t0 = System.currentTimeMillis();
        blackBoxSearch.logPhase("[phase exhaustive-fds] start budget=%dms".formatted(timeLimitInMillis));

        AtomicBoolean improvedInPhase = new AtomicBoolean(false);
        ConflictDrivenBranching cdBranching = blackBoxSearch.conflictDrivenBranching(random);

        // Stage 1: Constructive conflict-driven search (dom/wdeg + Last-Conflict + Objective Polarity)
        // Gives a fast primal solution and closes small/constructive trees immediately.
        long stage1Budget = timeLimitInMillis >= 60 ? Math.max(25L, (timeLimitInMillis * 45L) / 100L) : timeLimitInMillis;
        int restart = 1;
        while ((System.currentTimeMillis() - t0) < stage1Budget && !blackBoxSearch.isLimitReached()) {
            ConflictDrivenBranching.ValueStrategy valStrat = switch ((restart - 1) % 3) {
                case 0 -> ConflictDrivenBranching.ValueStrategy.OBJECTIVE_AND_PHASE;
                case 1 -> hasFeasibleSolution()
                        ? ConflictDrivenBranching.ValueStrategy.SOLUTION_AND_OBJECTIVE
                        : ConflictDrivenBranching.ValueStrategy.PHASE_SAVING;
                default -> ConflictDrivenBranching.ValueStrategy.RANDOM_BOUND;
            };
            double noise = restart == 1 ? 0.0 : (0.02 + 0.01 * (restart % 4));
            cdBranching.configureRestart(valStrat, noise, true);

            Supplier<Runnable[]> branching = cdBranching;
            if (randomSwapProbability > 0.0) {
                branching = new RandomizedBranching(branching, random, randomSwapProbability);
            }

            final int failCutoff = restart == 1 ? 512 : 256 * luby(restart);
            DFSearch cdDfs = blackBoxSearch.createSubSearch(branching);
            cdDfs.onSolution(() -> {
                improvedInPhase.set(true);
                blackBoxSearch.recordSolutionFromCurrentState();
            });

            SearchStatistics stage1Stats;
            if (objective == null) {
                stage1Stats = cdDfs.solve(s ->
                        blackBoxSearch.shouldAbortSubSearch(s, t0, stage1Budget, failCutoff)
                                || hasFeasibleSolution());
            } else {
                stage1Stats = cdDfs.optimizeSubjectTo(objective,
                        s -> blackBoxSearch.shouldAbortSubSearch(s, t0, stage1Budget, failCutoff),
                        blackBoxSearch::postIncumbentCut);
            }
            blackBoxSearch.accumulateSubSearchStats(stage1Stats);
            blackBoxSearch.logProgress("[phase exhaustive-fds] cd-restart=%d stats: %s"
                    .formatted(restart, blackBoxSearch.formatStats(stage1Stats)));

            if (objective == null && hasFeasibleSolution()) {
                blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(SearchStatus.SAT));
                return SearchStatus.SAT;
            }

            if (stage1Stats.isCompleted() && stage1Stats.numberOfFailures() < failCutoff && !blackBoxSearch.isLimitReached()) {
                SearchStatus endStatus = completedStatus(improvedInPhase.get());
                blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(endStatus));
                return endStatus;
            }
            restart++;
        }

        // Stage 2: Unbounded exhaustive search until phase budget expires
        long elapsed = System.currentTimeMillis() - t0;
        if (elapsed < timeLimitInMillis && !blackBoxSearch.isLimitReached()) {
            long remaining = timeLimitInMillis - elapsed;
            long fdsBudget = elapsed + (remaining >= 100 ? (remaining * 60L) / 100L : remaining);

            Supplier<Runnable[]> fdsBranching = blackBoxSearch.tieredFdsBranching(random);
            if (randomSwapProbability > 0.0) {
                fdsBranching = new RandomizedBranching(fdsBranching, random, randomSwapProbability);
            }
            cdBranching.configureRestart(ConflictDrivenBranching.ValueStrategy.OBJECTIVE_AND_PHASE, 0.0, true);

            DFSearch fdsDfs = blackBoxSearch.createSubSearch(fdsBranching);
            fdsDfs.onSolution(() -> {
                improvedInPhase.set(true);
                blackBoxSearch.recordSolutionFromCurrentState();
            });

            SearchStatistics fdsStats;
            if (objective == null) {
                fdsStats = fdsDfs.solve(s ->
                        blackBoxSearch.shouldAbortSubSearch(s, t0, fdsBudget, Integer.MAX_VALUE)
                                || hasFeasibleSolution());
            } else {
                fdsStats = fdsDfs.optimizeSubjectTo(objective,
                        s -> blackBoxSearch.shouldAbortSubSearch(s, t0, fdsBudget, Integer.MAX_VALUE),
                        blackBoxSearch::postIncumbentCut);
            }
            blackBoxSearch.accumulateSubSearchStats(fdsStats);
            blackBoxSearch.logProgress("[phase exhaustive-fds] fds stats: %s".formatted(blackBoxSearch.formatStats(fdsStats)));

            if (objective == null && hasFeasibleSolution()) {
                blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(SearchStatus.SAT));
                return SearchStatus.SAT;
            }
            if (fdsStats.isCompleted() && !blackBoxSearch.isLimitReached()) {
                SearchStatus endStatus = completedStatus(improvedInPhase.get());
                blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(endStatus));
                return endStatus;
            }

            // Stage 3: Final unbounded conflict-driven search for any remaining time
            if ((System.currentTimeMillis() - t0) < timeLimitInMillis && !blackBoxSearch.isLimitReached()) {
                cdBranching.configureRestart(ConflictDrivenBranching.ValueStrategy.OBJECTIVE_AND_PHASE, 0.0, true);
                Supplier<Runnable[]> finalBranching = cdBranching;
                if (randomSwapProbability > 0.0) {
                    finalBranching = new RandomizedBranching(finalBranching, random, randomSwapProbability);
                }
                DFSearch finalDfs = blackBoxSearch.createSubSearch(finalBranching);
                finalDfs.onSolution(() -> {
                    improvedInPhase.set(true);
                    blackBoxSearch.recordSolutionFromCurrentState();
                });

                SearchStatistics finalStats;
                if (objective == null) {
                    finalStats = finalDfs.solve(s ->
                            blackBoxSearch.shouldAbortSubSearch(s, t0, timeLimitInMillis, Integer.MAX_VALUE)
                                    || hasFeasibleSolution());
                } else {
                    finalStats = finalDfs.optimizeSubjectTo(objective,
                            s -> blackBoxSearch.shouldAbortSubSearch(s, t0, timeLimitInMillis, Integer.MAX_VALUE),
                            blackBoxSearch::postIncumbentCut);
                }
                blackBoxSearch.accumulateSubSearchStats(finalStats);
                blackBoxSearch.logProgress("[phase exhaustive-fds] final-cd stats: %s"
                        .formatted(blackBoxSearch.formatStats(finalStats)));

                if (objective == null && hasFeasibleSolution()) {
                    blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(SearchStatus.SAT));
                    return SearchStatus.SAT;
                }
                if (finalStats.isCompleted() && !blackBoxSearch.isLimitReached()) {
                    SearchStatus endStatus = completedStatus(improvedInPhase.get());
                    blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(endStatus));
                    return endStatus;
                }
            }
        }

        SearchStatus endStatus = improvedInPhase.get() ? SearchStatus.IMPROVED : SearchStatus.NOT_IMPROVED;
        blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(endStatus));
        return endStatus;
    }

    private SearchStatus completedStatus(boolean foundInPhase) {
        if (objective == null) {
            return (foundInPhase || hasFeasibleSolution()) ? SearchStatus.SAT : SearchStatus.UNSAT;
        }
        return (foundInPhase || hasFeasibleSolution()) ? SearchStatus.PROVEN_OPTIMAL : SearchStatus.UNSAT;
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
