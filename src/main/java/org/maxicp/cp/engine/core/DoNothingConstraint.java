/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.core;

public class DoNothingConstraint implements CPConstraint {

    @Override
    public void post() {

    }

    @Override
    public void propagate() {

    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public void setScheduled(boolean scheduled) {

    }

    @Override
    public boolean isScheduled() {
        return false;
    }

    @Override
    public void setActive(boolean active) {

    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void registerDelta(Delta delta) {

    }

    @Override
    public void updateDeltas() {

    }
}
