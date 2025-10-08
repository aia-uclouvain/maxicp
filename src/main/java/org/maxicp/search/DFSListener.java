package org.maxicp.search;

import org.maxicp.state.StateManager;

public interface DFSListener {
    default void clear() {};
    default  void solution(int id, int pId) {};
    default void fail(int id, int pId) {};
    default void branch(int id, int pId) {};

    default void saveState(StateManager sm) {}
    default void restoreState(StateManager sm) {}
    default void branchingAction(Runnable action) {}

}