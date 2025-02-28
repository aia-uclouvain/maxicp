package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.Delta;

public class RemoveDetour extends AbstractCPConstraint {

    private final CPSeqVar seqVar;
    private final int prev;
    private final int node;
    private final int succ;
    private boolean scheduled = false;

    public RemoveDetour(CPSeqVar seqVar, int prev, int node, int succ) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.prev = prev;
        this.node = node;
        this.succ = succ;
    }

    @Override
    public void post() {
        seqVar.removeDetour(prev, node, succ);
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
