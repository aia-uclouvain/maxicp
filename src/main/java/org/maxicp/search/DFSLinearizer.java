package org.maxicp.search;

import org.maxicp.state.StateManager;

import java.util.ArrayList;

public class DFSLinearizer implements DFSListener {

    ArrayList<Runnable> branchingActions = new ArrayList<>();

    @Override
    public void branchingAction(Runnable action) {
        branchingActions.add(action);
    }

    @Override
    public void saveState(StateManager sm) {
        branchingActions.add(() -> sm.saveState());
    }

    @Override
    public void restoreState(StateManager sm) {
        branchingActions.add(() -> sm.restoreState());
    }
}
