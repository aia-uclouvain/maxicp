package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;

import static java.lang.Math.min;

public class NotFirst extends AbstractCPConstraint {
    final CPIntervalVar A;
    final CPIntervalVar[] vars;
    public NotFirst(CPIntervalVar A, CPIntervalVar[] vars) {
        super(A.getSolver());
        this.A = A;
        this.vars = vars;
    }

    @Override
    public void post() {
        if (!A.isAbsent() ) {
            A.propagateOnChange(this);
        }
        if (vars.length==0){
            throw new InconsistencyException();
        }
        for (CPIntervalVar var : vars) {
            if (!var.isAbsent()) {
                var.propagateOnChange(this);
            }
        }
        propagate();
    }
    @Override
    public void propagate() {
        int minEct = Integer.MAX_VALUE;
        for (CPIntervalVar var : vars) {
            if (!var.isAbsent()) {
                minEct = min(minEct, var.endMin());
            }
        }
        if (minEct == Integer.MAX_VALUE) {
            throw new InconsistencyException() ;
        }else{
            this.A.setStartMin(minEct);
        }
    }
}
