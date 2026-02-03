package org.maxicp.search;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayDeque;
import java.util.function.Supplier;

public class ConcurrentDFSearch extends AbstractConcurrentSearchMethod {
    private final ArrayDeque<DFSTodoElement> alternatives;
    private final ModelProxy modelProxy;
    private static final DFSTodoElement restore = new Restore();
    private static final DFSTodoElement save = new Save();

    private sealed interface DFSTodoElement {};
    private record SymbolicModelToRun(SymbolicModel m) implements DFSTodoElement {};
    private record Save() implements DFSTodoElement {};
    private record Restore() implements DFSTodoElement {};

    public ConcurrentDFSearch(ModelProxy mp, Supplier<SymbolicModel[]> branching) {
        super(mp.getConcreteModel().getStateManager(), branching);
        modelProxy = mp;
        alternatives = new ArrayDeque<>();
    }

    @Override
    protected void initSolve() {
        alternatives.clear();
        expandNode();
        modelProxy.getConcreteModel().getStateManager().saveState();
    }

    @Override
    protected boolean done() {
        return alternatives.isEmpty();
    }

    @Override
    protected void processNextStep() {
        boolean hasSeenNode = false;
        while (!alternatives.isEmpty()) {
            switch (alternatives.getLast()) {
                case Save() -> {
                    alternatives.removeLast();
                    sm.saveState();
                }
                case Restore() -> {
                    alternatives.removeLast();
                    sm.restoreState();
                }
                case SymbolicModelToRun(SymbolicModel m) -> {
                    //if we don't have seen a node yet, process it
                    if(!hasSeenNode) {
                        hasSeenNode = true;
                        alternatives.removeLast();
                        try {
                            statistics.incrNodes();
                            onNodeVisit.run();
                            modelProxy.getConcreteModel().jumpToChild(m);
                            expandNode();
                        } catch (InconsistencyException e) {
                            statistics.incrFailures();
                            notifyFailure();
                        }
                    }
                    else {
                        return;
                    }
                }
            }
        }
    }

    @Override
    protected void finishSolve() {
        alternatives.clear();
        modelProxy.getConcreteModel().getStateManager().restoreState();
    }

    private void expandNode() {
        SymbolicModel[] alts = branching.get();
        if (alts.length == 0) {
            statistics.incrSolutions();
            notifySolution();
        }
        else {
            boolean first = true;
            for (int i = alts.length - 1; i >= 0; i--) {
                SymbolicModel model = alts[i];

                if(!first)
                    alternatives.add(restore);
                alternatives.add(new SymbolicModelToRun(model));
                if(!first)
                    alternatives.add(save);
                else
                    first = false;
            }
        }
    }

    @Override
    protected SymbolicModel extractModel() {
        //SymbolicModel firstNode = alternatives.removeFirst();
        return null;
    }
}
