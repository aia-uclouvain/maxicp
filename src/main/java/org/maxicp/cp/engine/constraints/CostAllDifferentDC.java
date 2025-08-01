/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntVarImpl;

import java.util.Arrays;

/**
 * Arc Consistent AllDifferent Constraint with Costs
 */
public class CostAllDifferentDC extends CostCardinalityMaxDC {

    public CostAllDifferentDC(CPIntVar[] x, int [][] costs, CPIntVar H) {
        super(x, cards(x), costs, H, Algorithm.SCHMIED_REGIN_2024);
    }

    private static int[] cards(CPIntVar[] x) {
        int maxVal = 0;
        for (CPIntVar v : x) {
            if (v.max() > maxVal) {
                maxVal = v.max();
            }
        }
        // create an array of size maxVal+1 with all values set to 1
        int[] cards = new int[maxVal + 1];
        Arrays.fill(cards, 1);
        return cards;
    }

}
