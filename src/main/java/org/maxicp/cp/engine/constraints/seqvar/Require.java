/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.Delta;

/**
 * Requires a node in a sequence, forcing the node to be visited
 */
public class Require extends AbstractCPConstraint {

    private final CPSeqVar seqVar;
    private final int node;
    private boolean scheduled = false;

    public Require(CPSeqVar seqVar, int node) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.node = node;
    }

    @Override
    public void post() {
        seqVar.require(node);
    }

    @Override
    public void propagate() {

    }

    public void setScheduled(boolean scheduled) {
        this.scheduled = scheduled;
    }

    public boolean isScheduled() {
        return scheduled;
    }

    @Override
    public void setActive(boolean active) {

    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void registerDelta(Delta delta) {

    }

    @Override
    public void updateDeltas() {

    }
}
