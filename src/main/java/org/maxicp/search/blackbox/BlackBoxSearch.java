package org.maxicp.search.blackbox;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.Objective;

import java.util.ArrayList;
import java.util.List;

public class BlackBoxSearch {


    List<RunnableSearch> searches = new ArrayList<>();

    public BlackBoxSearch() {
    }

    public void addSearch(RunnableSearch search) {
        searches.add(search);

    }

    public void start() {

    }

    public void updateSolution(List<Integer> solution) {
        for (RunnableSearch search : searches) {
            search.updateSolution(solution);
        }
    }


    public static void main(String[] args) {
        CPSolver cp = CPFactory.makeSolver();
        CPIntVar[] vars = CPFactory.makeIntVarArray(cp, 10, 10);
        int [] best = new int[vars.length];
        Objective objective = cp.minimize(vars[0]);

        BlackBoxSearch bbSearch = new BlackBoxSearch();

        bbSearch.addSearch(new LNSRunnableSearch(bbSearch, cp, List.of(vars), objective));
        bbSearch.addSearch(new DFSRunnableSearch(bbSearch, cp, List.of(vars), objective));

        bbSearch.start();
    }


}
