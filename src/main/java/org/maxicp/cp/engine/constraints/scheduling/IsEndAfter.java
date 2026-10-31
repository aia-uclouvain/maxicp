package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.*;

/**
 * Reified constraint enforcing {@code boolVar <=> end(intervalVar) >= var} when {@code intervalVar} is present.
 */
public class IsEndAfter extends AbstractCPConstraint {

    private final CPIntervalVar intervalVar;
    private final CPIntVar end;
    private final CPBoolVar boolVar;

    private final CPConstraint endBefore;
    private final CPConstraint endAfter;

    /**
     * Creates a reified constraint linking {@code boolVar} to {@code end(intervalVar) >= var}.
     *
     * @param boolVar     the boolean variable that is true iff {@code end(intervalVar) >= var}
     * @param intervalVar the interval variable
     * @param var         the integer variable compared against the end of {@code intervalVar}
     */
    public IsEndAfter(CPBoolVar boolVar, CPIntervalVar intervalVar, CPIntVar var) {
        super(intervalVar.getSolver());
        this.boolVar = boolVar;
        this.intervalVar = intervalVar;
        this.end = var;
        endAfter = new EndAfter(intervalVar, end);
        endBefore = new EndBefore(intervalVar, CPFactory.plus(end, -1));
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
                    getSolver().post(endAfter, false);
                } else {
                    getSolver().post(endBefore, false);
                }
                setActive(false);
            } else if (intervalVar.isPresent()) {
                // only case for setting the boolean variable:
                // changing it with absent interval has no meaning
                if (intervalVar.endMin() >= end.max()) {
                    boolVar.fix(true);
                    setActive(false);
                } else if (intervalVar.endMax() < end.min()) {
                    boolVar.fix(false);
                    setActive(false);
                }
            }
        }
    }
}
