package org.maxicp.search.blackbox;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class DFSRunnableSearch extends RunnableSearch {

    BlackBoxSearch blackBoxSearch;
    CPSolver solver;
    List<CPIntVar> vars;
    Optional<List<Integer>> feasibleSolution;
    Objective objective;
    Random random;

    public DFSRunnableSearch(BlackBoxSearch blackBoxSearch, CPSolver solver, List<CPIntVar> vars, Objective objective) {
        this.blackBoxSearch = blackBoxSearch;
        this.solver = solver;
        this.vars = vars;
        this.objective = objective;
        this.feasibleSolution = Optional.empty();
        this.random = new Random(42);
    }

    @Override
    void updateSolution(List<Integer> solution) {
        feasibleSolution = Optional.of(solution);
    }

    @Override
    public SearchStatus run(int timeLimitInSeconds) {
        List<Integer> best = feasibleSolution.get();
        DFSearch dfs = CPFactory.makeDfs(solver, Searches.firstFail(vars.toArray(new CPIntVar[vars.size()])));
        dfs.onSolution(() -> {
            List<Integer> solution = new ArrayList<>();
            for (int i = 0; i < vars.size(); i++) {
                solution.add(vars.get(i).min());
            }
            blackBoxSearch.updateSolution(best);
        });
        SearchStatistics stats = dfs.optimize(objective, s -> s.numberOfFailures() > 50);
        if (stats.isCompleted()) {
            if (stats.numberOfSolutions() > 0) {
                return SearchStatus.PROVEN_OPTIMAL;
            } else {
                return SearchStatus.UNSAT;
            }
        } else {
            if (stats.numberOfSolutions() > 0) {
                return SearchStatus.IMPROVED;
            } else {
                return SearchStatus.NOT_IMPROVED;
            }
        }
    }

}
