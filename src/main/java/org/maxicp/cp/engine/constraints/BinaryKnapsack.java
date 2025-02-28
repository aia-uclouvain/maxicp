/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.State;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Comparator;

/**
 * A Binary Knapsack constraint ensures that the total load
 * is equal to the sum of the weights of the selected items.
 *
 * <p>This constraint models
 *  <pre>
 *       load = sum_i (b[i] * weights[i])
 *  </pre>
 * where:
 * <ul>
 *     <li>{@code b[i]} is a binary decision variable indicating whether item {@code i} is selected (1) or not (0).</li>
 *     <li>{@code weights[i]} represents the weight of item {@code i}.</li>
 *     <li>{@code load} is an integer variable representing the total weight of selected items.</li>
 * </ul>
 *
 * @author pschaus
 */
public class BinaryKnapsack extends AbstractCPConstraint {

    CPBoolVar[] x;
    int [] w;
    CPIntVar c;

    State<Boolean>[]  candidate; //index of unfixed items(we don't know if they are packed)
    StateInt rcap;      //required cap: sum of weight of required items with x=1 (packed for sure in the knapsack)
    StateInt   pcap;      //possible cap: sum of weight of possible items (candidate + the ones already packed)
    StateInt   nb;        //number of possible items

    StateSparseSet notFixed; //index of items that are not fixed
    int [] notFixedArray;

    int alpha_;
    int beta_;
    int [] X;
    int n = -1; //number of element in the knapsack

    public BinaryKnapsack(CPBoolVar [] b, final int [] weights, CPIntVar load, int n) {
        this(b,weights,load);
        this.n = n;
        assert (n > 0);
    }

    public BinaryKnapsack(CPBoolVar [] b, final int [] weights, int load, int n) {
        this(b,weights,load);
        this.n = n;
        assert (n > 0);
    }

    /**
     * A Binary Knapsack constraint ensures that the total load
     * is equal to the sum of the weights of the selected items.
     *
     * @param b an array of {@link CPBoolVar} variables where {@code b[i]} is 1 if the item is selected, otherwise 0.
     * @param weights an array of non-negative integers representing the weights of the items. Must have the same length as {@code b}.
     * @param load a {@link CPIntVar} variable representing the total weight of selected items in the knapsack.
     */
    public BinaryKnapsack(CPBoolVar [] b, final int [] weights, CPIntVar load) {
        super(b[0].getSolver());
        assert(b.length == weights.length);
        Integer [] perm = new Integer [weights.length];
        for (int i = 0; i < perm.length; i++) {
            assert (weights[i] >= 0);
            perm[i] = i;
        }

        Arrays.sort(perm, Comparator.comparingInt(i -> -weights[i])); // Sort by values

        w = new int[weights.length];
        x = new CPBoolVar[weights.length];
        c = load;
        for (int i = 0; i < x.length; i++) {
            w[i] = weights[perm[i]];
            x[i] = b[perm[i]];
        }
    }

    /**
     * Constraint: load is the sum of the weights of items selected in to the knapsack. <br>
     * load = sum_i b[i]*weights[i] <br>
     * Available propagation strength are Weak and Strong (the default)
     * @param b
     * @param weights a vector of non-negative integers of the same length as b
     * @param load
     */
    public BinaryKnapsack(CPBoolVar [] b, final int [] weights, int load) {
        this(b,weights, CPFactory.makeIntVar(b[0].getSolver(),load,load));
    }

    @Override
    public void post() {

        //getSolver().post(new LightBinaryKnapsack(x,w,c));

        // post sum_i x[i]*w[i] = c (decomposition of the constraint)
        CPIntVar[] loadExpr = new CPIntVar[x.length];
        for (int i = 0; i < x.length; i++) {
            loadExpr[i] = CPFactory.mul(x[i], w[i]);
        }
        getSolver().post(new Sum(loadExpr, c));


        candidate = new State[x.length];
        for (int i = 0; i < candidate.length; i++) {
            candidate[i] = getSolver().getStateManager().makeStateRef(true);
        }
        int S = Arrays.stream(w).sum();
        rcap = getSolver().getStateManager().makeStateInt(0);
        pcap = getSolver().getStateManager().makeStateInt(S);
        nb = getSolver().getStateManager().makeStateInt(x.length);

        notFixed = new StateSparseSet(getSolver().getStateManager(), x.length,0);
        notFixedArray = new int[x.length];

        for (int i = 0; i < x.length; i++) {
            if (!x[i].isFixed()) {
                x[i].propagateOnDomainChange(this); // propagate
            }
        }
        if (!c.isFixed()) c.propagateOnBoundChange(this);

        alpha_ = 0;
        beta_ = 0;
        X = new int[x.length];

        propagate();
    }


    private void bind(int i) {
        int wi = w[i];
        int nrcap = rcap.value() + wi;
        c.removeBelow(nrcap) ;
        rcap.setValue(nrcap);
        candidate[i].setValue(false);
        nb.decrement(); //nb--
    }

    private void remove(int i) {
        pcap.setValue(pcap.value() - w[i]);
        c.removeAbove(pcap.value()); ;
        candidate[i].setValue(false);
        nb.decrement();
    }



    @Override
    public void propagate() {
        int nUnfixed = notFixed.fillArray(notFixedArray);
        for (int i = 0; i < nUnfixed; i++) {
            if (x[notFixedArray[i]].isFixed()) {
                if (x[notFixedArray[i]].isTrue()) {
                    bind(notFixedArray[i]);
                }
                else {
                    remove(notFixedArray[i]);
                }
                notFixed.remove(notFixedArray[i]);
            }
        }

        this.alpha_ = 0;
        this.beta_ = 0;
        int leftover = c.max() - rcap.value();
        int slack = pcap.value() - c.min();
        for (int k = 0; k < x.length; k++) {
            if (candidate[k].value()) {
                if (w[k] > leftover) {
                    x[k].remove(1);
                    return;
                }
                if (w[k] > slack) {
                    x[k].fix(1);
                    return;
                }
            }
        }

        boolean pruneMore = false;
        if (nb.value() <= 2)
            return;
        if (noSumPossible(c.min() - rcap.value(), c.max() - rcap.value()))
            throw InconsistencyException.INCONSISTENCY;

        if (pruneMore) {
            int lastsize = -1;
            for (int k = 0; k < x.length; k++) {
                if (candidate[k].value() && w[k] != lastsize) {
                    lastsize = w[k];
                    candidate[k].setValue(false);
                    boolean toremove = noSumPossible(Math.max(c.min(), rcap.value() + w[k]) - rcap.value() - w[k], c.max() - rcap.value() - w[k]);
                    candidate[k].setValue(true);
                    if (toremove) {
                        x[k].remove(1);
                        return;
                    }
                }
            }
            lastsize = -1;
            for (int k = 0; k < x.length; k++) {
                if (candidate[k].value() && w[k] != lastsize) {
                    lastsize = w[k];
                    candidate[k].setValue(false);
                    boolean toinsert = noSumPossible(c.min() - rcap.value(),
                            Math.min(c.max(), pcap.value() - w[k]) - rcap.value());
                    candidate[k].setValue(true);
                    if (toinsert) {
                        x[k].fix(1);
                    }
                }
            }
        }
        if (noSumPossible(c.min() - rcap.value(), c.min() - rcap.value())) {
            c.removeBelow(rcap.value() + beta_);
        }
        if (noSumPossible(c.max() - rcap.value(), c.max() - rcap.value())) {
            c.removeAbove(rcap.value() + alpha_);
        }
    }


    private boolean noSumPossible(int alpha, int beta) {
        assert (alpha <= beta);

        if (alpha <= 0 || beta >= pcap.value()) {
            return false;
        }

        int Xs = 0;
        for (int i = 0; i < x.length; i++) {
            if (candidate[i].value()) Xs++;
        }

        int sumX = 0;
        int l = 0;
        for (int i = 0; i < Xs; i++) {
            while (!candidate[l].value()) {
                l++;
            }
            X[i] = w[l];
            sumX += X[i];
            l++;
        }

        if (beta >= sumX) {
            return false;
        }

        int Sa = 0;
        int Sb = 0;
        int Sc = 0;
        int k = 0;
        int k_ = 0;

        while (Sc + X[Xs - k_ - 1] < alpha) {
            Sc += X[Xs - k_ - 1];
            k_++;
        }
        Sb = X[Xs - k_ - 1];
        while (Sa < alpha && Sb <= beta) {
            k++;
            Sa += X[k - 1];
            if (Sa < alpha) {
                k_--;
                Sb += X[Xs - k_ - 1];
                Sc -= X[Xs - k_ - 1];
                while (Sa + Sc >= alpha) {
                    k_--;
                    Sc -= X[Xs - k_ - 1];
                    Sb += X[Xs - k_ - 1] - X[Xs - k_ - k - 1 - 1];
                }
            }
        }
        alpha_ = Sa + Sc;
        beta_ = Sb;
        return Sa < alpha;
    }
}

