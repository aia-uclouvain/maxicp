package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.FDSModeling;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Feasibility search phase based on repeated DFS restarts.
 *
 * <p>The class repeatedly launches a depth-first search on the same model,
 * but stops each restart after a bounded number of failures. The failure budget
 * grows according to the Luby sequence, which gives a robust restart policy for
 * hard search spaces: many short runs are tried first, then progressively longer
 * runs are allowed when the search still has not found a solution.</p>
 *
 * <p>When a solution is found, it is copied out of the current model state and
 * stored in the enclosing {@link BlackBoxSearch}. This phase does not optimize
 * anything; it only tries to determine whether the model is feasible and, if so,
 * record one feasible assignment.</p>
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
        long t0 = System.currentTimeMillis();
        int restart = 1;
        SearchStatus bestStatus = SearchStatus.UNKNOWN;
        blackBoxSearch.logPhase("[phase feasibility-restarts] start budget=%dms".formatted(timeLimitInMillis));

        while ((System.currentTimeMillis() - t0) < timeLimitInMillis && !hasFeasibleSolution()) {
            Supplier<Runnable[]> branching = new FDSModeling(vars.toArray(IntExpression[]::new));
            if (randomSwapProbability > 0.0) {
                branching = new RandomizedBranching(branching, random, randomSwapProbability);
            }

            DFSearch dfs = model.dfSearch(branching);
            dfs.onSolution(() -> {
                List<Integer> solution = new ArrayList<>(vars.size());
                for (IntExpression var : vars) {
                    solution.add(var.min());
                }
                blackBoxSearch.updateSolution(solution);
                blackBoxSearch.updateObjective(blackBoxSearch.evaluateCurrentObjective());
            });

            final int failureLimit = baseFailureLimit * luby(restart);
            blackBoxSearch.logProgress("[phase feasibility-restarts] restart=%d failureLimit=%d"
                    .formatted(restart, failureLimit));
            SearchStatistics stats = dfs.solve(s -> {
                long elapsed = System.currentTimeMillis() - t0;
                return elapsed >= timeLimitInMillis || s.numberOfFailures() >= failureLimit || hasFeasibleSolution();
            });
            blackBoxSearch.logTrace("[phase feasibility-restarts] restart=%d stats: %s"
                    .formatted(restart, blackBoxSearch.formatStats(stats)));

            if (stats.isCompleted() && stats.numberOfSolutions() == 0) {
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

    /**
     * Returns the {@code i}-th term of the Luby sequence, using 1-based indexing.
     *
     * <p>The sequence starts as {@code 1, 1, 2, 1, 1, 2, 4, ...}. In this class,
     * the returned value is multiplied by {@link #baseFailureLimit} to obtain the
     * failure cutoff for a restart. This keeps the restart budget small at first
     * while still occasionally granting much larger budgets.</p>
     *
     * @param i position in the sequence, starting at 1
     * @return the Luby scaling factor for restart {@code i}
     */
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

