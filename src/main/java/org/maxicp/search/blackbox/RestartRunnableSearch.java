package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Feasibility search phase based on repeated DFS restarts with persistent conflict learning.
 *
 * <p>Rotates between conflict-driven constructive heuristics ({@code dom/wdeg} + {@code Last-Conflict} +
 * phase saving / objective polarity) and failure-directed search ({@link org.maxicp.search.FDSModeling}),
 * while scaling failure cutoffs with the Luby sequence.</p>
 */
public class RestartRunnableSearch extends RunnableSearch {

    private final ModelDispatcher model;
    private final List<IntExpression> vars;
    private final double randomSwapProbability;
    private final int baseFailureLimit;
    private final Random random;

    public RestartRunnableSearch(BlackBoxSearch blackBoxSearch,
                                 ModelDispatcher model,
                                 List<IntExpression> vars,
                                 int baseFailureLimit,
                                 double randomSwapProbability,
                                 long randomSeed) {
        super(blackBoxSearch);
        this.model = model;
        this.vars = List.copyOf(vars);
        this.baseFailureLimit = baseFailureLimit;
        this.randomSwapProbability = randomSwapProbability;
        this.random = new Random(randomSeed);
    }

    @Override
    void updateSolution(List<Integer> solution) {
        // nothing to do, this phase only needs to know if a solution exists
    }

    @Override
    public SearchStatus run(long timeLimitInMillis) {
        if (!blackBoxSearch.intervals().isEmpty()) {
            return new SchedulingRunnableSearch(
                    blackBoxSearch, model, blackBoxSearch.intervals(), vars, null, random.nextLong())
                    .run(timeLimitInMillis);
        }

        long t0 = System.currentTimeMillis();
        int restart = 1;
        SearchStatus bestStatus = SearchStatus.UNKNOWN;
        blackBoxSearch.logPhase("[phase feasibility-restarts] start budget=%dms".formatted(timeLimitInMillis));

        ConflictDrivenBranching cdBranching = blackBoxSearch.conflictDrivenBranching(random);

        while ((System.currentTimeMillis() - t0) < timeLimitInMillis && !hasFeasibleSolution() && !blackBoxSearch.isLimitReached()) {
            Supplier<Runnable[]> branching;
            int mode = (restart - 1) % 4;
            if (mode == 2) {
                branching = blackBoxSearch.tieredFdsBranching(random);
                if (randomSwapProbability > 0.0) {
                    branching = new RandomizedBranching(branching, random, randomSwapProbability);
                }
            } else {
                ConflictDrivenBranching.ValueStrategy valStrat = switch (mode) {
                    case 0 -> ConflictDrivenBranching.ValueStrategy.OBJECTIVE_AND_PHASE;
                    case 1 -> ConflictDrivenBranching.ValueStrategy.PHASE_SAVING;
                    default -> ConflictDrivenBranching.ValueStrategy.RANDOM_BOUND;
                };
                double noise = restart == 1 ? 0.0 : Math.min(0.15, 0.02 * (1 + (restart % 5)));
                cdBranching.configureRestart(valStrat, noise, true);
                branching = cdBranching;
                if (randomSwapProbability > 0.0 && mode == 3) {
                    branching = new RandomizedBranching(branching, random, randomSwapProbability * 0.5);
                }
            }

            DFSearch dfs = blackBoxSearch.createSubSearch(branching);
            dfs.onSolution(blackBoxSearch::recordSolutionFromCurrentState);

            final int failureLimit = baseFailureLimit * luby(restart);
            blackBoxSearch.logProgress("[phase feasibility-restarts] restart=%d failureLimit=%d"
                    .formatted(restart, failureLimit));
            SearchStatistics stats = dfs.solve(s ->
                    blackBoxSearch.shouldAbortSubSearch(s, t0, timeLimitInMillis, failureLimit)
                            || hasFeasibleSolution());
            blackBoxSearch.accumulateSubSearchStats(stats);
            blackBoxSearch.logTrace("[phase feasibility-restarts] restart=%d stats: %s"
                    .formatted(restart, blackBoxSearch.formatStats(stats)));

            if (stats.isCompleted() && stats.numberOfSolutions() == 0 && stats.numberOfFailures() < failureLimit
                    && !blackBoxSearch.isLimitReached()) {
                blackBoxSearch.logPhase("[phase feasibility-restarts] proven UNSAT");
                return SearchStatus.UNSAT;
            }
            if (stats.numberOfSolutions() > 0) {
                bestStatus = SearchStatus.SAT;
                break;
            }
            restart++;
        }

        SearchStatus status = hasFeasibleSolution() ? SearchStatus.SAT : bestStatus;
        blackBoxSearch.logPhase("[phase feasibility-restarts] end status=%s".formatted(status));
        return status;
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
