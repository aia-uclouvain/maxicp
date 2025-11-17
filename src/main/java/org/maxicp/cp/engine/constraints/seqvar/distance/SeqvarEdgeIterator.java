package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

public class SeqvarEdgeIterator implements EdgeIterator {

    private CPSeqVar seqVar;

    public SeqvarEdgeIterator(CPSeqVar seqVar) {
        this.seqVar = seqVar;
    }

    @Override
    public CPSeqVar seqVar() {
        return seqVar;
    }

    @Override
    public boolean hasEdge(int from, int to) {
        return seqVar.hasEdge(from, to);
    }

    @Override
    public int fillPred(int node, int[] dest, SeqStatus status) {
        return seqVar.fillPred(node, dest, status);
    }

    @Override
    public int fillSucc(int node, int[] dest, SeqStatus status) {
        return seqVar.fillSucc(node, dest, status);
    }

    @Override
    public void update() {

    }
}
