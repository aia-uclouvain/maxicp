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
        long t0 = System.currentTimeMillis();
        blackBoxSearch.logPhase("[phase exhaustive-fds] start budget=%dms".formatted(timeLimitInMillis));
        Supplier<Runnable[]> branching = new FDSModeling(vars.toArray(IntExpression[]::new));
        if (randomSwapProbability > 0.0) {
            branching = new RandomizedBranching(branching, random, randomSwapProbability);
        }

        DFSearch dfs = model.dfSearch(branching);
        dfs.onSolution(() -> {
            List<Integer> solution = new ArrayList<>();
            for (IntExpression var : vars) {
                solution.add(var.min());
            }
            blackBoxSearch.updateSolution(solution);
            blackBoxSearch.updateObjective(blackBoxSearch.evaluateCurrentObjective());
        });

        SearchStatistics stats;
        if (objective == null) {
            stats = dfs.solve(s -> (System.currentTimeMillis() - t0) >= timeLimitInMillis);
        } else {
            stats = dfs.optimizeSubjectTo(objective,
                    s -> (System.currentTimeMillis() - t0) >= timeLimitInMillis,
                    blackBoxSearch::postIncumbentCut);
        }
        blackBoxSearch.logProgress("[phase exhaustive-fds] stats: %s".formatted(blackBoxSearch.formatStats(stats)));

        if (stats.isCompleted()) {
            if (stats.numberOfSolutions() > 0) {
                blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s"
                        .formatted(objective == null ? SearchStatus.SAT : SearchStatus.PROVEN_OPTIMAL));
                return objective == null ? SearchStatus.SAT : SearchStatus.PROVEN_OPTIMAL;
            } else {
                if (objective != null && hasFeasibleSolution()) {
                    blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(SearchStatus.PROVEN_OPTIMAL));
                    return SearchStatus.PROVEN_OPTIMAL;
                }
                blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(SearchStatus.UNSAT));
                return SearchStatus.UNSAT;
            }
        } else {
            if (stats.numberOfSolutions() > 0) {
                blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(SearchStatus.IMPROVED));
                return SearchStatus.IMPROVED;
            } else {
                blackBoxSearch.logPhase("[phase exhaustive-fds] end status=%s".formatted(SearchStatus.NOT_IMPROVED));
                return SearchStatus.NOT_IMPROVED;
            }
        }
    }

}