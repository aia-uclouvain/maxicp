/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.IsLessOrEqual;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;

/**
 * Cumulative constraint with sum decomposition (very slow).
 */
public class CumulativeDecomposition extends AbstractCPConstraint {

    private final CPIntVar[] start;
    private final int[] duration;
    private final CPIntVar[] end;
    private final int[] demand;
    private final int capa;

    /**
     * Creates a cumulative constraint with a decomposition into sum constraint.
     * At any time-point t, the sum of the demands
     * of the activities overlapping t do not overlap the capacity.
     *
     * @param start    the start time of each activities
     * @param duration the duration of each activities (non negative)
     * @param demand   the demand of each activities, non negative
     * @param capa     the capacity of the constraint
     */
    public CumulativeDecomposition(CPIntVar[] start, int[] duration, int[] demand, int capa) {
        super(start[0].getSolver());
        this.start = start;
        this.duration = duration;
        this.end = CPFactory.makeIntVarArray(start.length, i -> plus(start[i], duration[i]));
        this.demand = demand;
        this.capa = capa;
    }

    @Override
    public void post() {
        int min = Arrays.stream(start).map(s -> s.min()).min(Integer::compare).get();
        int max = Arrays.stream(end).map(e -> e.max()).max(Integer::compare).get();

        for (int t = min; t < max; t++) {

            CPBoolVar[] overlaps = new CPBoolVar[start.length];
            for (int i = 0; i < start.length; i++) {
                overlaps[i] = makeBoolVar(getSolver());
                CPBoolVar startsBefore = makeBoolVar(getSolver());
                CPBoolVar endsAfter = makeBoolVar(getSolver());
                getSolver().post(new IsLessOrEqual(startsBefore, start[i], t));
                getSolver().post(new IsLessOrEqual(endsAfter, minus(plus(start[i], duration[i] - 1)), -t));
                final int capa = -2;
                // overlaps = endsAfter & startsBefore
                getSolver().post(new IsLessOrEqual(overlaps[i], minus(sum(new CPIntVar[]{startsBefore, endsAfter})), capa));
            }

            CPIntVar[] overlapHeights = CPFactory.makeIntVarArray(start.length, i -> mul(overlaps[i], demand[i]));
            CPIntVar cumHeight = sum(overlapHeights);
            cumHeight.removeAbove(capa);
        }
    }
}
