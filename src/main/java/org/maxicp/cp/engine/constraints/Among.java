package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.maxicp.cp.CPFactory.eq;
import static org.maxicp.cp.CPFactory.neq;

/**
 * Among constraint
 */
public class Among extends AbstractCPConstraint {

    private final CPIntVar[] x;
    private final Set<Integer> vals;
    private final CPIntVar N;
    private StateInt lbN;
    private StateInt ubN;

    // indexes of variables with a domain that is not totally excluded from vals
    private final StateSparseSet nonEmptyInterIdx;
    // indexes of variables with a domain that is not a subset of vals
    private final StateSparseSet nonSubsetIdx;
    private final StateInt[] interSize;

    public Among(CPIntVar[] x, Set<Integer> vals, CPIntVar N) {
        super(x[0].getSolver());
        this.x = x;
        this.vals = vals;
        this.N = N;

        nonEmptyInterIdx = new StateSparseSet(getSolver().getStateManager(), x.length, 0);
        nonSubsetIdx = new StateSparseSet(getSolver().getStateManager(), x.length, 0);
        interSize = new StateInt[x.length];
    }

    /**
     * @param var the variable
     * @return the number of value in the domain of a var that are included in vals
     */
    protected int interSize(CPIntVar var) {
        int count = 0;
        int[] domain = new int[var.size()];
        var.fillArray(domain);
        for (int val: domain) {
            if (vals.contains(val)) count++;
        }
        return count;
    }

    @Override
    public void post() {
        for (int i = 0; i < x.length; i++) {
            CPIntVar var = x[i];
            interSize[i] = getSolver().getStateManager().makeStateInt(interSize(var));
            if (interSize[i].value() == 0) nonEmptyInterIdx.remove(i);
            else if (interSize[i].value() == var.size()) nonSubsetIdx.remove(i);
        }

        // The lower bound for N is the number of variable with a domain that is totally included in vals
        lbN = getSolver().getStateManager().makeStateInt(x.length - nonSubsetIdx.size());
        // The upper bound for N is the number of variable with a domain that is not totally excluded from vals
        ubN = getSolver().getStateManager().makeStateInt(nonEmptyInterIdx.size());

        N.propagateOnBoundChange(this);
        for (int i = 0; i < nonEmptyInterIdx.size(); i++) {
            if (nonSubsetIdx.contains(i)) {
                int idx = i;
                x[idx].whenDomainChange(() -> {
                    if (x[idx].isFixed()) {
                        int val = x[idx].min();
                        if (vals.contains(val)) {
                            interSize[idx].setValue(1);
                            nonSubsetIdx.remove(idx);
                        }
                        else {
                            interSize[idx].setValue(0);
                            nonEmptyInterIdx.remove(idx);
                        }
                    } else { // x[idx] lost some values
                        interSize[idx].setValue(interSize(x[idx]));
                        if (interSize[idx].value() == 0) nonEmptyInterIdx.remove(idx);
                        else if (interSize[idx].value() == x[idx].size()) nonSubsetIdx.remove(idx);
                    }
                    propagate();
                });
            }
        }
        propagate();
    }

    @Override
    public void propagate() {
        if (!this.isActive()) return;

        // The lower bound for N is the number of variable with a domain that is totally included in vals
        lbN.setValue(x.length - nonSubsetIdx.size());
        // The upper bound for N is the number of variable with a domain that is totally excluded from vals
        ubN.setValue(nonEmptyInterIdx.size());


        if (!N.isFixed()) {
            N.removeBelow(lbN.value());
            N.removeAbove(ubN.value());
        }

        // If N is fixed and that exactly N variables still have at least one value in vals
        // the values in their domain that are not in vals
        if (N.isFixed() && N.min() == ubN.value()) {
            for (int i: nonSubsetIdx.toArray()) {
                if(!x[i].isFixed() && nonEmptyInterIdx.contains(i)) {
                    CPIntVar var = x[i];
                    int[] domain = new int[var.size()];
                    var.fillArray(domain);
                    for (int val: domain) {
                        if (!vals.contains(val)) {
                            var.remove(val);
                        }
                    }
                    nonSubsetIdx.remove(i);
                }
            }
            this.setActive(false);
            return;
        }

        // If N is fixed and exactly N variables have a domain that is a subset of vals
        // the values in the domains of the other variables that are contained in vals are removed
        if (N.isFixed() && N.max() == lbN.value()) {
            for (int i: nonEmptyInterIdx.toArray()) {
                if (!x[i].isFixed() && nonSubsetIdx.contains(i)) {
                    CPIntVar var = x[i];
                    int[] domain = new int[var.size()];
                    var.fillArray(domain);
                    for (int val: domain) {
                        if (vals.contains(val)) var.remove(val);
                    }
                    nonEmptyInterIdx.remove(i);
                }
            }
            this.setActive(false);
        }
    }
}
