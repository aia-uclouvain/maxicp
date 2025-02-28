package org.maxicp.state;

public interface StateManaged {
    /**
     * Returns the state manager in charge of the global
     * state of the solver.
     *
     * @return the state manager
     */
    StateManager getStateManager();
}
