/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.algebra.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.ArrayList;

import static org.maxicp.modeling.Factory.sum;

/**
 * Sum of one or more Cumul Functions.
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public class SumCumulFunction implements CumulFunction {
    public final CumulFunction[] functions;

    public SumCumulFunction(CumulFunction... functions) {
        this.functions = functions;
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
    public IntExpression heightAtStart(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            ArrayList<IntExpression> inScope = new ArrayList<>();
            for (CumulFunction f : functions) {
                if (f.inScope(interval)) {
                    inScope.add(f.heightAtStart(interval));
                }
            }
            return sum(inScope.toArray(new IntExpression[0]));
        }
    }

    @Override
    public IntExpression heightAtEnd(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            ArrayList<IntExpression> inScope = new ArrayList<>();
            for (CumulFunction f : functions) {
                if (f.inScope(interval)) {
                    inScope.add(f.heightAtEnd(interval));
                }
            }
            return sum(inScope.toArray(new IntExpression[0]));
        }
    }
}