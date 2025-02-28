/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.util.exception.InconsistencyException;


/**
 * @author Pierre Schaus pschaus@gmail.com
 */
public class AtLeastNValueDC extends AbstractCPConstraint {

    private static final int NONE = -Integer.MIN_VALUE;

    private boolean posted;

    private CPIntVar[] x;

    private CPIntVar nValueVar;

    private int[] match;
    private int[] varSeen;

    private int min;
    private int max;
    private int valSize;
    private int[] valMatch;
    private int sizeMatching;
    private int[] valSeen;
    private int magic;

    private int dfs;
    private int component;

    private int[] varComponent;
    private int[] varDfs;
    private int[] varHigh;

    private int[] valComponent;
    private int[] valDfs;
    private int[] valHigh;

    private int[] stack;
    private int[] type;
    private int top;

    private int[][] domArray;
    private int[] unBoundIdx;
    private int nUnBound;


    public AtLeastNValueDC(CPIntVar[] x, CPIntVar nVal) {
        super(x[0].getSolver());
        this.x = x;
        this.posted = false;
        this.nValueVar = nVal;
    }

    public AtLeastNValueDC(CPIntVar[] x, CPIntVar nval, boolean dontPostFWC) {
        this(x, nval);
    }

    @Override
    public void post() {
        posted = true;

        findValueRange();

        unBoundIdx = new int[x.length];
        domArray = new int[x.length][valSize];

        initMatching();

        findInitialMatching();

        int sizeMatching = findMaximalMatching();

        nValueVar.removeAbove(sizeMatching);

        if (nValueVar.min() > sizeMatching) {
            throw InconsistencyException.INCONSISTENCY;
        }

        allocateSCC();

        propagate();

        for (int k = 0; k < x.length; k++) {
            if (!x[k].isFixed()) {
                x[k].propagateOnDomainChange(this);
            }
        }

        if (!nValueVar.isFixed()) {
            nValueVar.propagateOnBoundChange(this);
        }
    }

    public boolean hasValInBestAssignment(int i) {
        if (posted && i >= 0 && i < x.length && match[i] != NONE) return true;
        return false;
    }

    public int getValInBestAssignment(int i) {
        if (hasValInBestAssignment(i))
            return match[i];
        else if (i >= 0 && i < x.length && posted)
            return x[i].min();
        return Integer.MIN_VALUE;
    }

    @Override
    public void propagate() {
        nUnBound = 0;
        for (int k = 0; k < x.length; k++) {
            if (match[k] != NONE) {
                if (!x[k].contains(match[k])) {
                    valMatch[match[k] - min] = -1;
                    match[k] = NONE;
                    sizeMatching--;
                }
            }
            if (!x[k].isFixed()) {
                unBoundIdx[nUnBound] = k;
                nUnBound++;
            }
        }

        int maxMatching = findMaximalMatching();
        nValueVar.removeAbove(maxMatching);
        if (nValueVar.min() > maxMatching) {
            throw InconsistencyException.INCONSISTENCY;
        }
        else if (nValueVar.min() == maxMatching) {
            prune(maxMatching);
        }
    }

    private void findValueRange() {
        min = Integer.MAX_VALUE;
        max = Integer.MIN_VALUE;
        for (int i = 0; i < x.length; i++) {
            min = Math.min(min, x[i].min());
            max = Math.max(max, x[i].max());
        }
        valSize = max - min + 1;
        valMatch = new int[valSize];
        for (int k = 0; k < valSize; k++)
            valMatch[k] = -1;  // unmatched
    }

    private void initMatching() {
        magic = 0;
        match = new int[x.length];
        for (int k = 0; k < x.length; k++) {
            match[k] = NONE; // unmatched
        }
        varSeen = new int[x.length];
        valSeen = new int[valSize];
    }

    private void findInitialMatching() {
        sizeMatching = 0;
        for (int k = 0; k < x.length; k++) {
            int mx = x[k].min();
            int Mx = x[k].max();
            for (int i = mx; i <= Mx; i++)
                if (valMatch[i - min] < 0) // unmatched
                    if (x[k].contains(i)) {
                        match[k] = i;
                        valMatch[i - min] = k;
                        sizeMatching++;
                        break;
                    }
        }
    }

    private int findMaximalMatching() {
        if (sizeMatching < x.length) {
            for (int k = 0; k < x.length; k++) {
                if (match[k] == NONE) {
                    magic++;
                    if (findAlternatingPath(k)) {
                        sizeMatching++;
                    }
                }
            }
        }
        return sizeMatching;
    }

    private boolean findAlternatingPath(int i) {
        if (varSeen[i] != magic) {
            varSeen[i] = magic;
            int mx = x[i].min();
            int Mx = x[i].max();
            for (int v = mx; v <= Mx; v++) {
                if (match[i] != v) {
                    if (x[i].contains(v)) {
                        if (findAlternatingPathValue(v)) {
                            match[i] = v;
                            valMatch[v - min] = i;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean findAlternatingPathValue(int v) {
        if (valSeen[v - min] != magic) {
            valSeen[v - min] = magic;
            if (valMatch[v - min] == -1)
                return true;
            if (findAlternatingPath(valMatch[v - min]))
                return true;
        }
        return false;
    }

    private void allocateSCC() {
        varComponent = new int[x.length * 2];
        varDfs = new int[x.length * 2];
        varHigh = new int[x.length * 2];

        valComponent = new int[valSize];
        valDfs = new int[valSize * 2];
        valHigh = new int[valSize * 2];

        stack = new int[(x.length + valSize) * 2];
        type = new int[(x.length + valSize) * 2];
    }

    private void initSCC() {
        for (int k = 0; k < x.length; k++) {
            varComponent[k] = 0;
            varDfs[k] = 0;
            varHigh[k] = 0;
        }
        for (int v = min; v <= max; v++) {
            valComponent[v - min] = 0;
            valDfs[v - min] = 0;
            valHigh[v - min] = 0;
        }
        top = 0;
        dfs = x.length + valSize;
        component = 0;
    }

    private void findSCC() {
        initSCC();
        for (int k = 0; k < x.length; k++) {
            if (varDfs[k] == 0)
                findSCCvar(k);
        }
    }

    private void findSCCvar(int k) {
        varDfs[k] = dfs--;
        varHigh[k] = varDfs[k];
        stack[top] = k;
        type[top] = 0;
        top++;
        assert (top <= x.length + valSize);

        // a variable can go to values in its domain that it doesn't match
        int nVal = x[k].fillArray(domArray[k]);
        for (int i = 0; i < nVal; i++) {
            int w = domArray[k][i];
            if (match[k] != w) {
                if (valDfs[w - min] == 0) {
                    findSCCval(w);
                    if (valHigh[w - min] > varHigh[k])
                        varHigh[k] = valHigh[w - min];
                } else if ((valDfs[w - min] > varDfs[k]) && (valComponent[w - min] == 0)) {
                    if (valDfs[w - min] > varHigh[k])
                        varHigh[k] = valDfs[w - min];
                }
            }

        }

        // matched variable can go to other unmatched variables
        if (match[k] != NONE) {
            for (int i = 0; i < x.length; i++) {
                if (match[i] == NONE) {
                    if (varDfs[i] == 0) {
                        findSCCvar(i);
                        if (varHigh[i] > varHigh[k])
                            varHigh[k] = varHigh[i];
                    } else if ((varDfs[i] > varDfs[k]) && (varComponent[i] == 0)) {
                        if (varDfs[i] > varHigh[k])
                            varHigh[k] = varDfs[i];
                    }
                }
            }
        }

        if (varHigh[k] == varDfs[k]) {
            component++;
            do {
                assert (top > 0);
                int v = stack[--top];
                int t = type[top];
                if (t == 0)
                    varComponent[v] = component;
                else
                    valComponent[v - min] = component;
                if (t == 0 && v == k)
                    break;
            } while (true);
        }
    }

    private void findSCCval(int k) {
        int i;
        valDfs[k - min] = dfs--;
        valHigh[k - min] = valDfs[k - min];
        stack[top] = k;
        type[top] = 1;
        top++;
        assert (top <= x.length + valSize);
        // matched value can go to the variable that match it
        if (valMatch[k - min] != -1) {
            int w = valMatch[k - min];
            if (varDfs[w] == 0) {
                findSCCvar(w);
                if (varHigh[w] > valHigh[k - min])
                    valHigh[k - min] = varHigh[w];
            } else if ((varDfs[w] > valDfs[k - min]) && (varComponent[w] == 0)) {
                if (varDfs[w] > valHigh[k - min])
                    valHigh[k - min] = varDfs[w];
            }
        } else {
            for (i = 0; i < x.length; i++) {
                //unmatched value can go to every matched value
                if (match[i] != NONE) {
                    int w = match[i];
                    if (valDfs[w - min] == 0) {
                        findSCCval(w);
                        if (valHigh[w - min] > valHigh[k - min])
                            valHigh[k - min] = valHigh[w - min];
                    } else if ((valDfs[w - min] > valDfs[k - min]) && (valComponent[w - min] == 0)) {
                        if (valDfs[w - min] > valHigh[k - min])
                            valHigh[k - min] = valDfs[w - min];
                    }
                }
                //unmatched value can go to every unmatched variable
                else {
                    if (varDfs[i] == 0) {
                        findSCCvar(i);
                        if (varHigh[i] > valHigh[k - min])
                            valHigh[k - min] = varHigh[i];
                    } else if ((varDfs[i] > valDfs[k - min]) && (varComponent[i] == 0)) {
                        if (varDfs[i] > valHigh[k - min])
                            valHigh[k - min] = varDfs[i];
                    }
                }
            }
        }
        if (valHigh[k - min] == valDfs[k - min]) {
            component++;
            do {
                assert (top > 0);
                int v = stack[--top];
                int t = type[top];
                if (t == 0)
                    varComponent[v] = component;
                else
                    valComponent[v - min] = component;
                if (t == 1 && v == k)
                    break;
            } while (true);
        }
    }

    private void prune(int sizeMatching) {
        findSCC();
        for (int j = 0; j < nUnBound; j++) {
            int k = unBoundIdx[j];
            int nVal = x[k].fillArray(domArray[k]);
            for (int i = 0; i < nVal; i++) {
                int w = domArray[k][i];
                if (match[k] != w && varComponent[k] != valComponent[w - min]) {
                    x[k].remove(w);
                }
            }
        }
    }

}
