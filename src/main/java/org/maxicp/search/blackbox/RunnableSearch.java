package org.maxicp.search.blackbox;

import java.util.List;

public abstract class RunnableSearch {

    protected final BlackBoxSearch blackBoxSearch;

    protected RunnableSearch(BlackBoxSearch blackBoxSearch) {
        this.blackBoxSearch = blackBoxSearch;
    }

    abstract void updateSolution(List<Integer> solution);

    abstract SearchStatus run(long timeLimitInMillis);

    protected boolean hasFeasibleSolution() {
        return blackBoxSearch.bestSolution().isPresent();
    }

}