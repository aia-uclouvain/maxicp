package org.maxicp.search;

import org.maxicp.state.StateManager;

import java.util.function.Supplier;

public abstract class RunnableSearchMethod extends AbstractSearchMethod<Runnable> {
    public RunnableSearchMethod(StateManager sm, Supplier<Runnable[]> branching) {
        super(sm, branching);
    }
}
