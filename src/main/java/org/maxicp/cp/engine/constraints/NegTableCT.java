/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.DeltaCPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseBitSet;
import org.maxicp.util.exception.InconsistencyException;


import java.util.Arrays;
import java.util.HashSet;

import static org.maxicp.cp.CPFactory.minus;

/**
 * Implementation of Compact Table for Negative Table algorithm described in
 * <p><i>Extending Compact-Table to Negative and Short Tables</i>
 * Helene Verhaeghe, Christophe Lecoutre, Pierre Schaus
 * <p>See <a href="https://webperso.info.ucl.ac.be/~pschaus/assets/publi/aaai2017-shorttables.pdf">The article.</a>
 */
public class NegTableCT extends AbstractCPConstraint {

    // scope of the constraint
    private final CPIntVar[] offx;
    private final int scpSize;

    // contains the current value of the table (i.e., tuples valid given current state)
    private final StateSparseBitSet validTuples;
    // supports[i][v] is the set of tuples supported by x[i]=v (i.e., tuples with this value for this variable)
    private final StateSparseBitSet.SupportBitSet[][] supports;

    // keep track of the unbounded vars
    private final int[] unbounded;
    private final StateInt nUnbound;

    // delta to keep track of the modifications in the domains
    private final DeltaCPIntVar[] delta;

    // temporary var to optimize var creation
    private final StateSparseBitSet.CollectionBitSet collected;
    private final int[] tempDom;

    /**
     * Negative Table constraint.
     * <p>Assignment of {@code x_0=v_0, x_1=v_1,...} only valid if there does not
     * exists a row {@code (v_0, v_1, ...)} in the table.
     * The table represents the infeasible assignments for the variables.
     *
     * @param x the variables to constraint. x is not empty.
     * @param table the array of invalid solutions (second dimension must be of same size as the array x)
     */
    public NegTableCT(CPIntVar[] x, int[][] table) {
        super(x[0].getSolver());
        assert !hasDuplicates(table);
        this.scpSize = x.length;
        // variables with offset (min dom = 0)
        this.offx = new CPIntVar[this.scpSize];
        this.delta = new DeltaCPIntVar[this.scpSize];
        this.unbounded = new int[this.scpSize];
        for (int i = 0; i < this.scpSize; i++) {
            this.unbounded[i] = i;
        }
        this.nUnbound = this.getSolver().getStateManager().makeStateInt(this.scpSize);
        int[] offset = new int[this.scpSize];
        int maxsize = 0;

        this.validTuples = new StateSparseBitSet(this.getSolver().getStateManager(), table.length);
        this.collected = validTuples.new CollectionBitSet();

        // Allocate supportedByVarVal
        supports = new StateSparseBitSet.SupportBitSet[this.scpSize][];
        for (int i = 0; i < this.scpSize; i++) {
            offset[i] = x[i].min();
            this.offx[i] = minus(x[i], offset[i]); // map the variables domain to start at 0
            this.delta[i] = this.offx[i].delta(this);
            maxsize = Math.max(maxsize, this.offx[i].max());
            supports[i] = new StateSparseBitSet.SupportBitSet[this.offx[i].max() + 1];
            for (int j = 0; j < supports[i].length; j++)
                supports[i][j] = validTuples.new SupportBitSet();
        }

        // Set values in supports, which contains all the tuples supported by each var-val pair
        for (int i = 0; i < table.length; i++) { //i is the index of the tuple (in table)
            for (int j = 0; j < this.scpSize; j++) { //j is the index of the current variable (in x)
                if (x[j].contains(table[i][j])) {
                    supports[j][table[i][j] - offset[j]].set(i);
                }
            }
        }

        this.tempDom = new int[maxsize + 1];
    }

    public static Boolean hasDuplicates(int[][] table) {
        HashSet<Integer> set = new HashSet<Integer>();
        for (int[] tuple : table) {
            Integer hc = Arrays.hashCode(tuple);
            if (set.contains(hc)){
                return true;
            }
            set.add(hc);
        }
        return false;
    }

    @Override
    public void post() {
        //// Step 1: update validTuples to find invalid tuples
        int nUnboundValue = this.nUnbound.value();
        for (int i = nUnboundValue - 1; i >= 0; i--) {
            // only previously unbounded vars can trigger modification

            // get the delta of the unbounded var
            int idx = this.unbounded[i];

            CPIntVar var = this.offx[idx];

            if (var.isFixed()) {
                // var has been bound, direct intersection with support
                validTuples.intersect(supports[idx][var.min()]);
            } else {
                // clear temp var collecting
                collected.clear();
                // less values remaining
                int n = var.fillArray(tempDom);
                for (int j = 0; j < n; j++) {
                    collected.union(supports[idx][tempDom[j]]);
                }

                validTuples.intersect(collected);
            }
        }

        //// Step 2: filter the domains to find invalids values
        int cardinal = 1;
        for (int i = nUnboundValue - 1; i >= 0; i--) {
            int idx = this.unbounded[i];
            CPIntVar var = this.offx[idx];
            cardinal *= var.size();
        }
        for (int i = nUnboundValue - 1; i >= 0; i--) {
            int idx = this.unbounded[i];
            CPIntVar var = this.offx[idx];
            int threshold = cardinal / var.size();
            this.filterDomain(var, supports[idx], threshold);
            if (var.isFixed()) {
                // var is bound, removed from unbounded
                nUnboundValue--;
                this.unbounded[i] = this.unbounded[nUnboundValue];
                this.unbounded[nUnboundValue] = idx;
            }
            cardinal = threshold * var.size();
        }
        this.nUnbound.setValue(nUnboundValue);
        for (int i = 0; i < this.scpSize; i++)
            this.offx[i].propagateOnDomainChange(this);
    }

    @Override
    public void propagate() {

        int nChange = 0;
        int idxChange = -1;

        //// Step 1: update validTuples to find invalid tuples

        int nUnboundValue = this.nUnbound.value();
        for (int i = nUnboundValue - 1; i >= 0; i--) {
            // only previously unbounded vars can trigger modification

            // get the delta of the unbounded var
            int idx = this.unbounded[i];
            DeltaCPIntVar dvar = this.delta[idx];

            if (dvar.changed()) {
                // list of valid tuples only modified for modified vars
                nChange++;
                idxChange = idx;

                CPIntVar var = this.offx[idx];

                if (var.isFixed()) {
                    // var has been bound, direct intersection with support
                    validTuples.intersect(supports[idx][var.min()]);
                } else {
                    // clear temp var collecting
                    collected.clear();
                    if (dvar.size() < var.size()) {
                        // less values removed
                        int n = dvar.fillArray(tempDom);
                        for (int j = 0; j < n; j++) {
                            collected.union(supports[idx][tempDom[j]]);
                        }
                        collected.invert();
                    } else {
                        // less values remaining
                        int n = var.fillArray(tempDom);
                        for (int j = 0; j < n; j++) {
                            collected.union(supports[idx][tempDom[j]]);
                        }
                    }
                    validTuples.intersect(collected);
                }
            }
        }

        //// Step 2: filter the domains to find invalids values
        int cardinal = 1;
        for (int i = nUnboundValue - 1; i >= 0; i--) {
            int idx = this.unbounded[i];
            CPIntVar var = this.offx[idx];
            cardinal *= var.size();
        }
        for (int i = nUnboundValue - 1; i >= 0; i--) {
            int idx = this.unbounded[i];
            CPIntVar var = this.offx[idx];
            int threshold = cardinal / var.size();
            if (nChange > 1 || idx != idxChange)
                // check all unbound left if at least two have changed or this one is not the last one changed
                this.filterDomain(var, supports[idx], threshold);
            if (var.isFixed()) {
                // var is bound, removed from unbounded
                nUnboundValue--;
                this.unbounded[i] = this.unbounded[nUnboundValue];
                this.unbounded[nUnboundValue] = idx;
            }

            cardinal = threshold * var.size();
        }
        this.nUnbound.setValue(nUnboundValue);
    }

    private void filterDomain(CPIntVar var, StateSparseBitSet.SupportBitSet[] supp, int threshold) {
        int n = var.fillArray(tempDom);
        collected.clear();
        for (int j = 0; j < n; j++) {
            if (threshold == validTuples.countIntersection(supp[tempDom[j]])) {
                var.remove(tempDom[j]);
                collected.union(supp[tempDom[j]]);
            }
        }
        collected.invert();
        validTuples.intersect(collected);
    }
}
