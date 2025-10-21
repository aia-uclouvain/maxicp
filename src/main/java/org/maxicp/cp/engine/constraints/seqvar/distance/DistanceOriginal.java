package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

public class DistanceOriginal extends AbstractDistance {

    public DistanceOriginal(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
    }

    @Override
    public void updateLowerBound() {

    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {

    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {

    }
}
