/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * CP implementation of a Sum Cumulative Function
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public record CPSumCumulFunction(CPCumulFunction... functions) implements CPCumulFunction {

    @Override
    public List<Activity> flatten(boolean positive) {
        List<Activity> res = new LinkedList<>();
        for (CPCumulFunction f : functions) {
            res.addAll(f.flatten(positive));
        }
        return res;
    }

    @Override
    public boolean inScope(IntervalVar interval) {
        for (CumulFunction f : functions) {
            if (f.inScope(interval)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CPIntVar heightAtStart(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            ArrayList<CPIntVar> inScope = new ArrayList<>();
            for (CumulFunction f : functions) {
                if (f.inScope(interval)) {
                    inScope.add((CPIntVar) f.heightAtStart(interval));
                }
            }
            return CPFactory.sum(inScope.toArray(new CPIntVar[0]));
        }
    }

    @Override
    public CPIntVar heightAtEnd(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            ArrayList<CPIntVar> inScope = new ArrayList<>();
            for (CumulFunction f : functions) {
                if (f.inScope(interval)) {
                    inScope.add((CPIntVar) f.heightAtEnd(interval));
                }
            }
            return CPFactory.sum(inScope.toArray(new CPIntVar[0]));
        }
    }
}