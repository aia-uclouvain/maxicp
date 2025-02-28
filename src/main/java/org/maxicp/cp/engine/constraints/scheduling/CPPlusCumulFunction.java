/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.modeling.IntervalVar;

import java.util.LinkedList;
import java.util.List;

/**
 * CP implementation of a Plus Cumulative Function
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public record CPPlusCumulFunction(CPCumulFunction left, CPCumulFunction right) implements CPCumulFunction {

    @Override
    public List<Activity> flatten(boolean positive) {
        List<Activity> res = new LinkedList<>();
        res.addAll(left.flatten(positive));
        res.addAll(right.flatten(positive));
        return res;
    }

    @Override
    public boolean inScope(IntervalVar interval) {
        return left.inScope(interval) || right.inScope(interval);
    }

    @Override
    public CPIntVar heightAtStart(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            if (!left.inScope(interval)) {
                return right.heightAtStart(interval);
            } else if (!right.inScope(interval)) {
                return left.heightAtStart(interval);
            } else {
                CPIntVar leftHeight = left.heightAtStart(interval);
                CPIntVar rightHeight = right.heightAtStart(interval);
                return CPFactory.sum(leftHeight, rightHeight);
            }
        }
    }

    @Override
    public CPIntVar heightAtEnd(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            if (!left.inScope(interval)) {
                return right.heightAtEnd(interval);
            } else if (!right.inScope(interval)) {
                return left.heightAtEnd(interval);
            } else {
                CPIntVar leftHeight = left.heightAtEnd(interval);
                CPIntVar rightHeight = right.heightAtEnd(interval);
                return CPFactory.sum(leftHeight, rightHeight);
            }
        }
    }
}
