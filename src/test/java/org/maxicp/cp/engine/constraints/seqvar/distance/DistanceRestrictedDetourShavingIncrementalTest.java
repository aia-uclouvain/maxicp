package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.*;

public class DistanceRestrictedDetourShavingIncrementalTest extends DistanceTest {
    @Override
    protected CPConstraint getDistanceConstraint(CPSeqVar seqVar, int[][] transitions, CPIntVar distance) {
        return new DistanceRestrictedDetourShavingIncremental(seqVar, transitions, distance);
    }

}

