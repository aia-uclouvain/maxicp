package org.maxicp.modeling.algebra.scheduling;

import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.integer.Constant;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.IntVarRangeImpl;
/**
 * Step at End Elementary Cumul Function.
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public class StepAtEndCumulFunction implements CumulFunction {
    public final IntervalVar interval;
    private final IntVar height;
    public final int hMin;
    public final int hMax;

    public StepAtEndCumulFunction(IntervalVar interval, int hMin, int hMax){
        this.interval = interval;
        height = new IntVarRangeImpl(interval.getModelProxy(), hMin, hMax);
        this.hMin = hMin;
        this.hMax = hMax;
    }

    @Override
    public IntExpression heightAtStart(IntervalVar interval) {
        if (this.interval != interval) throw new IllegalArgumentException("this interval is not present in the function");
        return new Constant(interval.getModelProxy(), 0);
    }

    @Override
    public IntExpression heightAtEnd(IntervalVar interval) {
        if (this.interval != interval) throw new IllegalArgumentException("this interval is not present in the function");
        IntExpression y = Factory.mul(height, interval.status());
        return y;
    }

    @Override
    public boolean inScope(IntervalVar interval) {
        return this.interval == interval;
    }
}
