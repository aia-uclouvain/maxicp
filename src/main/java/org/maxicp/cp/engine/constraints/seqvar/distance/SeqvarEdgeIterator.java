package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPSeqVar;

public class SeqvarEdgeIterator implements EdgeIterator {

    private CPSeqVar seqVar;

    public SeqvarEdgeIterator(CPSeqVar seqVar) {
        this.seqVar = seqVar;
    }

    @Override
    public boolean hasEdge(int from, int to) {
        return seqVar.hasEdge(from, to);
    }

    @Override
    public int fillSucc(int node, int[] dest) {
        return seqVar.fillSucc(node, dest);
    }

    @Override
    public void update() {

    }
}
