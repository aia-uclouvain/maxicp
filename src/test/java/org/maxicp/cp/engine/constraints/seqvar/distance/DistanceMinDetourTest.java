package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

public class DistanceMinDetourTest extends DistanceTest{
    @Override
    protected CPConstraint getDistanceConstraint(CPSeqVar seqVar, int[][] transitions, CPIntVar distance) {
        return new DistanceMinDetour(seqVar, transitions, distance);
    }
}
