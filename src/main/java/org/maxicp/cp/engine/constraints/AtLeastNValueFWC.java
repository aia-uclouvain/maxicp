/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.State;
import org.maxicp.state.StateInt;

import java.util.stream.IntStream;


public class AtLeastNValueFWC extends AbstractCPConstraint {

    private final CPIntVar[] x;
    private final CPIntVar nValue;

    private final StateInt nFixed;
    private final int[] fixed;
    private final StateInt startPruneIdx;

    private final StateInt nValueLB; //number of value used
    private final State<Boolean>[] used; // for each value if it is used or not


    private int min;
    private int valSize;


    /**
     * The number of values that are used in the array x is at least nValue
     * #{x[i] | i in 0..x.length-1} >= nValue
     * @param x array of variables
     * @param nValue the number of values that are used in the array x is at least nValue
     */
    public AtLeastNValueFWC(CPIntVar [] x, CPIntVar nValue) {
        super(x[0].getSolver());
        this.x = x;
        this.nValue = nValue;
        nFixed = getSolver().getStateManager().makeStateInt(0);
        fixed = IntStream.range(0, x.length).toArray();

        findValueRange();
        nValueLB = getSolver().getStateManager().makeStateInt(0);
        used = new State[valSize];
        for (int v = 0; v < valSize; v++) {
            used[v] = getSolver().getStateManager().makeStateRef(false);
        }
        startPruneIdx = getSolver().getStateManager().makeStateInt(0);
    }

    @Override
    public void post() {
        for (CPIntVar var : x) {
            if (!var.isFixed())
                var.propagateOnFix(this);
        }
        propagate();
    }

    @Override
    public void propagate() {
        int nF = nFixed.value();
        // iterate over non fixed variables
        for (int i = nF; i < x.length; i++) {
            int idx = fixed[i];
            if (x[idx].isFixed()) {
                int val = x[fixed[i]].min(); // value to remove from unfixed
                fixed[i] = fixed[nF]; // Swap the variables
                fixed[nF] = idx;

                if (!used[val - min].value()) {
                    nValueLB.increment();
                    used[val - min].setValue(true);
                }
                nF++;

                int nValueUB = nValueLB.value() + (x.length-nF);
                if (nValueUB == nValue.min()) {
                    for (int k = startPruneIdx.value(); k < nF; k++) {
                        for (int j = nF; j < x.length; j++) {
                            x[fixed[j]].remove(x[fixed[k]].min());
                        }
                    }
                    // Update the start index for the next prune
                    // since those value were just removed, there is no need to consider them again on next call
                    startPruneIdx.setValue(nF-1);
                }
            }
        }
        nFixed.setValue(nF);
        nValue.removeBelow(nValueLB.value());
        nValue.removeAbove(nValueLB.value() + (x.length - nF));
    }

    private void findValueRange(){
        min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (CPIntVar cpIntVar : x) {
            min = Math.min(min, cpIntVar.min());
            max = Math.max(max, cpIntVar.max());
        }
        valSize = max - min + 1;
    }
}