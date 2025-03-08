/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.Constants;
import org.maxicp.modeling.IntervalVar;

import java.util.List;

import static org.maxicp.cp.CPFactory.*;

/**
 * CP implementation of a Step at Start Cumulative Function
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public class CPStepAtStartCumulFunction implements CPCumulFunction {
    public final CPIntervalVar var;
    private final CPIntervalVar dummy;
    public CPIntVar height;

    public CPStepAtStartCumulFunction(CPIntervalVar var, int hMin, int hMax) {
        if (hMin > hMax) throw new IllegalArgumentException("hMin > hMax: " + hMin + " > " + hMax);
        if (hMin <= 0) throw new IllegalArgumentException("hMin <= 0: " + hMin);

        CPSolver cp = var.getSolver();
        dummy = makeIntervalVar(cp);
        dummy.setEndMin(Constants.HORIZON);
        dummy.setEndMax(Constants.HORIZON);
        cp.post(eq(var.status(), dummy.status()));
        cp.post(eq(startOr(var, 0), startOr(dummy, 0)));
        this.var = var;
        height = makeIntVar(var.getSolver(), hMin, hMax);
    }

    public CPStepAtStartCumulFunction(CPIntervalVar var, int h) {
        this(var, h, h);
    }

    @Override
    public List<Activity> flatten(boolean positive) {
        return List.of(new Activity(dummy, positive ? height : minus(height)));
    }

    @Override
    public boolean inScope(IntervalVar interval) {
        return var == interval;
    }

    @Override
    public CPIntVar heightAtStart(IntervalVar interval) {
        if (interval != var) throw new IllegalArgumentException("this interval is not present in the function");
        CPIntVar y = mul(height, ((CPIntervalVar) interval).status());
        return y;
    }

    @Override
    public CPIntVar heightAtEnd(IntervalVar interval) {
        if (interval != var) throw new IllegalArgumentException("this interval is not present in the function");
        CPIntVar y = mul(height, ((CPIntervalVar) interval).status());
        return y;
    }
}