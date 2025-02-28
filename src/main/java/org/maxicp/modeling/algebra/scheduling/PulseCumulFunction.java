package org.maxicp.modeling.algebra.scheduling;

import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.integer.Constant;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.IntVarRangeImpl;

/**
 * Pulse Elementary Cumul Function.
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public class PulseCumulFunction implements CumulFunction {
    private final ModelProxy modelProxy;
    public final IntervalVar interval;
    private final IntVar height;
    public final int hMin;
    public final int hMax;

    public PulseCumulFunction(IntervalVar interval, int hMin, int hMax){
        if (hMin > hMax) throw new IllegalArgumentException("hMin > hMax: " + hMin + " > " + hMax);
        if (hMin <= 0) throw new IllegalArgumentException("hMin <= 0: " + hMin);
        this.modelProxy = interval.getModelProxy();
        this.interval = interval;
        height = new IntVarRangeImpl(modelProxy, hMin, hMax);
        this.hMin = hMin;
        this.hMax = hMax;
    }

    public PulseCumulFunction(IntervalVar interval, int h) {
        this(interval, h, h);
    }

    @Override
    public IntExpression heightAtStart(IntervalVar interval) {
        if (this.interval != interval) throw new IllegalArgumentException("this interval is not present in the function");
        IntExpression y = Factory.mul(height, interval.status());
        return y;
    }

    @Override
    public IntExpression heightAtEnd(IntervalVar interval) {
        if (this.interval != interval) throw new IllegalArgumentException("this interval is not present in the function");
        return new Constant(modelProxy, 0);
    }

    @Override
    public boolean inScope(IntervalVar interval) {
        return this.interval == interval;
    }
}
