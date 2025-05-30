package org.maxicp.search.blackbox;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.Searches;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class LNSRunnableSearch extends RunnableSearch {

    BlackBoxSearch blackBoxSearch;
    CPSolver solver;
    List<CPIntVar> vars;
    Optional<List<Integer>> feasibleSolution;
    Objective objective;
    Random random;

    public LNSRunnableSearch(BlackBoxSearch blackBoxSearch, CPSolver solver, List<CPIntVar> vars, Objective objective) {
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
        if (feasibleSolution.isEmpty()) {
            return SearchStatus.UNKNOWN; // no feasible solution found yet to start LNS
        } else {
            AtomicBoolean improved = new AtomicBoolean(false);
            long t0 = System.currentTimeMillis();
            List<Integer> best = feasibleSolution.get();
            DFSearch dfs = CPFactory.makeDfs(solver, Searches.firstFail(vars.toArray(new CPIntVar[vars.size()])));
            dfs.onSolution(() -> {
                for (int i = 0; i < vars.size(); i++) {
                    best.set(i, vars.get(i).min());
                }
                blackBoxSearch.updateSolution(best);
                improved.set(true);
            });
            boolean timeOut = false;
            for (int iter = 0; iter < 10; iter++) {
                dfs.optimizeSubjectTo(objective,
                        stats -> {
                            long elapsed = System.currentTimeMillis() - t0;
                            return elapsed > timeLimitInSeconds * 1000 || stats.numberOfFailures() > 50;
                        }, () -> {
                            for (int i = 0; i < vars.size(); i++) {
                                if (random.nextBoolean()) {
                                    solver.post(CPFactory.eq(vars.get(i), best.get(i)));
                                }
                            }
                        });
            }
            if (improved.get()) {
                return SearchStatus.IMPROVED;
            } else {
                return SearchStatus.NOT_IMPROVED;
            }
        }


    }

}
