package org.maxicp.search;

import org.maxicp.state.StateManager;

import java.util.ArrayList;

sealed interface Action permits BranchingAction, RestoreStateAction, SaveStateAction {
    void run();
}

record BranchingAction(Runnable action) implements Action {
    @Override
    public void run() {
        action.run();
    }
}


record RestoreStateAction(Runnable action) implements Action {
    @Override
    public void run() {
        action.run();
    }
}

record SaveStateAction(Runnable action) implements Action {
    @Override
    public void run() {
        action.run();
    }
}

public class DFSLinearizer implements DFSListener {

    ArrayList<Action> branchingActions = new ArrayList<>();

    @Override
    public void branchingAction(Runnable action) {
        branchingActions.add(new BranchingAction(action));
    }

    @Override
    public void saveState(StateManager sm) {
        branchingActions.add(new SaveStateAction(sm::saveState));
    }

    @Override
    public void restoreState(StateManager sm) {
        branchingActions.add(new RestoreStateAction(sm::restoreState));
    }

    public void clear() {
        branchingActions.clear();
    }

    public int size() {
        return branchingActions.size();
    }

    public Action get(int i) {
        return branchingActions.get(i);
    }
}
