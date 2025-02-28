/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.CPFactory;
import org.maxicp.Constants;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.concrete.BasicModelProxy;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.search.IntObjective;
import org.maxicp.state.StateManager;
import org.maxicp.util.PQueue;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;


public class MaxiCP implements CPSolver {

    private final PQueue<CPConstraint> propagationQueue = new PQueue<>(Constants.PIORITY_SLOW+1);
    private final List<Runnable> fixPointListeners = new LinkedList<>();

    private final StateManager sm;
    private final ModelProxy modelProxy;

    public MaxiCP(StateManager sm) {
        this.sm = sm;
        //use a very simple ModelProxy to allow usage of Expression-based searches
        this.modelProxy = new BasicModelProxy();
        this.modelProxy.setModel(new ConcreteCPModel(this.modelProxy, this, SymbolicModel.emptyModel(this.modelProxy)));
    }

    public MaxiCP(StateManager sm, ModelProxy modelProxy) {
        this.sm = sm;
        this.modelProxy = modelProxy;
    }

    @Override
    public StateManager getStateManager() {
        return sm;
    }

    public void schedule(CPConstraint c) {
        if (c.isActive() && !c.isScheduled()) {
            c.setScheduled(true);
            propagationQueue.add(c, c.priority());
        }
    }

    @Override
    public void onFixPoint(Runnable listener) {
        fixPointListeners.add(listener);
    }

    private void notifyFixPoint() {
        fixPointListeners.forEach(Runnable::run);
    }

    @Override
    public void fixPoint() {
        try {
            notifyFixPoint();
            while (!propagationQueue.isEmpty()) {
                propagate(propagationQueue.poll());
            }
        } catch (InconsistencyException e) {
            // empty the queue and unset the scheduled status
            while (!propagationQueue.isEmpty())
                propagationQueue.poll().setScheduled(false);
            throw e;
        }
    }

    private void propagate(CPConstraint c) {
        c.setScheduled(false);
        if (c.isActive()) {
            c.propagate();
            c.updateDeltas();
        }
    }

    @Override
    public IntObjective minimize(CPIntVar x) {
        return new Minimize(x);
    }

    @Override
    public IntObjective maximize(CPIntVar x) {
        return minimize(CPFactory.minus(x));
    }

    @Override
    public void post(CPConstraint c) {
        post(c, true);
    }

    @Override
    public void post(CPConstraint c, boolean enforceFixPoint) {
        c.post();
        if (enforceFixPoint) fixPoint();
    }

    @Override
    public void post(CPBoolVar b) {
        b.fix(true);
        fixPoint();
    }

    @Override
    public ModelProxy getModelProxy() {
        return modelProxy;
    }

    @Override
    public String toString() {
        return String.format("MaxiCP(%s)", sm);
    }
}
