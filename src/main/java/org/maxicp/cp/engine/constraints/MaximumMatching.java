/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Compute and Maintain a Maximum Matching
 * in the variable-value graph
 */
public class MaximumMatching {

    public static final int NONE = -Integer.MIN_VALUE;

    // For each variable, the setValue it is mached to
    private int[] match;
    private int[] varSeen;

    private int min;
    private int max;

    // Number of values
    private int valSize;
    // For each setValue, the variable idx matched to this setValue, -1 if none of them
    private int[] valMatch;
    private int[] valSeen;


    private int sizeMatching;

    private int magic;

    private CPIntVar[] x;

    public MaximumMatching(CPIntVar... x) {
        this.x = x;

        // find setValue ranges

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

        // initialize

        magic = 0;
        match = new int[x.length];
        for (int k = 0; k < x.length; k++) {
            match[k] = NONE; // unmatched
        }
        varSeen = new int[x.length];
        valSeen = new int[valSize];

        findInitialMatching();
    }


    public int compute(int[] result) {
        for (int k = 0; k < x.length; k++) {
            if (match[k] != NONE) {
                if (!x[k].contains(match[k])) {
                    valMatch[match[k] - min] = -1;
                    match[k] = NONE;
                    sizeMatching--;
                }
            }
        }
        int sizeMatching = findMaximalMatching();
        for (int k = 0; k < x.length; k++) {
            result[k] = match[k];
        }
        return sizeMatching;
    }


    private void findInitialMatching() { //returns the size of the maximum matching
        sizeMatching = 0;
        for (int k = 0; k < x.length; k++) {
            int minv = x[k].min();
            int maxv = x[k].max();
            for (int i = minv; i <= maxv; i++)
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
                    if (findAlternatingPathFromVar(k)) {
                        sizeMatching++;
                    }
                }
            }
        }
        return sizeMatching;
    }

    private boolean findAlternatingPathFromVar(int i) {
        if (varSeen[i] != magic) {
            varSeen[i] = magic;
            int xMin = x[i].min();
            int xMax = x[i].max();
            for (int v = xMin; v <= xMax; v++) {
                if (match[i] != v) {
                    if (x[i].contains(v)) {
                        if (findAlternatingPathFromVal(v)) {
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

    private boolean findAlternatingPathFromVal(int v) {
        if (valSeen[v - min] != magic) {
            valSeen[v - min] = magic;
            if (valMatch[v - min] == -1)
                return true;
            if (findAlternatingPathFromVar(valMatch[v - min]))
                return true;
        }
        return false;
    }


}
