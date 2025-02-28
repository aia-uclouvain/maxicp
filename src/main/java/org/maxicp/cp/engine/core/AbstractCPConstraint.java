/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;


import org.maxicp.Constants;
import org.maxicp.state.State;

import java.util.ArrayList;


/**
 * Abstract class the most of the constraints
 * should extend.
 */
public abstract class AbstractCPConstraint implements CPConstraint {

    /**
     * The solver in which the constraint is created
     */
    private final CPSolver cp;
    private boolean scheduled = false;
    private final State<Boolean> active;

    private ArrayList<Delta> deltas;

    public AbstractCPConstraint(CPSolver cp) {
        this.cp = cp;
        active = cp.getStateManager().makeStateRef(true);
        deltas = new ArrayList<>();
    }

    public void post() {
    }

    public CPSolver getSolver() {
        return cp;
    }

    public void propagate() {
    }

    @Override
    public void registerDelta(Delta delta) {
        deltas.add(delta);
        delta.update();
    }

    @Override
    public void updateDeltas() {
        for (Delta d: deltas) {
            d.update();
        }
    }

    public void setScheduled(boolean scheduled) {
        this.scheduled = scheduled;
    }

    public boolean isScheduled() {
        return scheduled;
    }

    public void setActive(boolean active) {
        this.active.setValue(active);
    }

    public boolean isActive() {
        return active.value();
    }

    @Override
    public int priority() {
        return Constants.PIORITY_FAST;
    }
}
