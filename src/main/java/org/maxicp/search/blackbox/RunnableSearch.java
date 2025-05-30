package org.maxicp.search.blackbox;

import java.util.List;

public abstract class RunnableSearch {

    abstract void updateSolution(List<Integer> solution);

    abstract SearchStatus run(int timeLimitInSeconds);

}
