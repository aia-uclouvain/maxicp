package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.*;

public class IsStartBefore extends AbstractCPConstraint {

    private final CPIntervalVar intervalVar;
    private final CPIntVar limit;
    private final CPBoolVar boolVar;

    private final CPConstraint startBefore;
    private final CPConstraint startAfter;

    public IsStartBefore(CPBoolVar boolVar, CPIntervalVar intervalVar, CPIntVar var) {
        super(intervalVar.getSolver());
        this.boolVar = boolVar;
        this.intervalVar = intervalVar;
        this.limit = var;
        startBefore = new StartBefore(intervalVar, limit);
        startAfter = new StartAfter(intervalVar, CPFactory.plus(limit, 1));
    }

    @Override
    public void post() {
        intervalVar.propagateOnChange(this);
        limit.propagateOnBoundChange(this);
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
                    getSolver().post(startBefore, false);
                } else {
                    getSolver().post(startAfter, false);
                }
                setActive(false);
            } else if (intervalVar.isPresent()) {
                // only case for setting the boolean variable:
                // changing it with absent interval has no meaning
                if (intervalVar.startMax() <= limit.min()) {
                    boolVar.fix(true);
                    setActive(false);
                } else if (intervalVar.startMin() > limit.max()) {
                    boolVar.fix(false);
                    setActive(false);
                }
            }
        }
    }

}
