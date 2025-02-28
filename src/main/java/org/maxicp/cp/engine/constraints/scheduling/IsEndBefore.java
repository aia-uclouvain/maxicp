package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.*;

public class IsEndBefore extends AbstractCPConstraint {

    private final CPIntervalVar intervalVar;
    private final CPIntVar end;
    private final CPBoolVar boolVar;

    private final CPConstraint endBefore;
    private final CPConstraint endAfter;

    public IsEndBefore(CPBoolVar boolVar, CPIntervalVar intervalVar, CPIntVar var) {
        super(intervalVar.getSolver());
        this.boolVar = boolVar;
        this.intervalVar = intervalVar;
        this.end = var;
        endBefore = new EndBefore(intervalVar, end);
        endAfter = new EndAfter(intervalVar, CPFactory.plus(end, 1));
    }

    @Override
    public void post() {
        intervalVar.propagateOnChange(this);
        end.propagateOnBoundChange(this);
        boolVar.propagateOnFix(this);
        propagate();
    }

    @Override
    public void propagate() {
        if (intervalVar.isAbsent()) {
            setActive(false);
        } else {
            if (boolVar.isFixed()) {
                if (boolVar.isTrue()) {
                    getSolver().post(endBefore, false);
                } else {
                    getSolver().post(endAfter, false);
                }
                setActive(false);
            } else if (intervalVar.isPresent()) {
                // only case for setting the boolean variable:
                // changing it with absent interval has no meaning
                if (intervalVar.endMax() <= end.min()) {
                    boolVar.fix(true);
                    setActive(false);
                } else if (intervalVar.endMin() > end.max()) {
                    boolVar.fix(false);
                    setActive(false);
                }
            }
        }
    }
}
