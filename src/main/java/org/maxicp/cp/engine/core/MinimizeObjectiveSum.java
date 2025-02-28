/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.CPFactory;
import org.maxicp.search.IntObjective;
import org.maxicp.search.Objective;

import java.util.Arrays;

/**
 *  Objective to minimize a sum of variables following
 *  the approach described in the paper:
 *
 *  Variable Objective Large Neighborhood Search:
 *  A practical approach to solve over-constrained problems (ICTAI 2013, Pierre Schaus)
 *
 */
public class MinimizeObjectiveSum implements Objective {


    CPIntVar [] variables;
    IntObjective [] ox;
    IntObjective  osumx;
    CPIntVar sumx;

    /**
     * Create a new objective to minimize x[0] + x[1] + ... + x[n-1]
     * @param x
     */
    public MinimizeObjectiveSum(CPIntVar ... x) {

        this.variables = x;
        CPSolver cp = variables[0].getSolver();


        // the terms update the bounds but are not filtered
        // and must not improve
        ox = new IntObjective[variables.length];
        for (int i = 0; i < variables.length; i++) {
            ox[i] = cp.minimize(variables[i]);
            ox[i].setDelta(0);
            ox[i].setFilter(false);
        }

        // the sum must improve and is filtered
        sumx = CPFactory.sum(x);
        osumx = cp.minimize(sumx);
        osumx.setDelta(1);
        osumx.setFilter(true);
    }

    /**
     * Set all the terms, including the sum in Weak tighten mode.
     * Weak tightening means that the bound are updated on each new
     * solution and the next solution must at least equalize (but not necessarily improve)
     * the bounds for each term and the sum.
     */
    public void weakTightenAll() {
        for (int i = 0; i < variables.length; i++) {
            ox[i].setDelta(0);
            ox[i].setFilter(true);
        }
        osumx.setDelta(0);
        osumx.setFilter(true);
    }

    /**
     * Strong tighten the sum
     * It means that the sum bound is updated on each new
     * solution and the next solution must strictly improve the sum.
     */
    public void strongTightenSum() {
        osumx.setDelta(1); // the sum must improve
        osumx.setFilter(true);
    }

    /**
     * Strong tighten the worse term
     * It means that the bound of the worse term is updated on each new
     * solution and the next solution must strictly improve this worse term.
     */
    public void strongTigthenWorseTerm() {
        int [] bounds = Arrays.stream(ox).mapToInt(o -> o.getBound()).toArray();
        int i = org.maxicp.util.Arrays.argMax(bounds); // index of worse term
        ox[i].setDelta(1);
    }

    /**
     * Bound of the sum objective
     * @return the bound of the sum objective
     */
    public int getBound() {
        return osumx.getBound();
    }


    /**
     * Method called each time a solution is found
     * during the search to let the tightening
     * of the primal bound occurs such that
     * the next found solution is better.
     *
     * This will tighten all the objectives
     */
    @Override
    public void tighten() {
        for (Objective o : ox) {
            o.tighten();
        }
        osumx.tighten();
    }

    /**
     * Relax all the objectives bound so that they
     * can be deteriorated.
     */
    @Override
    public void relax() {
        for (Objective o : ox) {
            o.relax();
        }
        osumx.relax();
    }

    @Override
    public void filter() {
        for (Objective o : ox) {
            o.filter();
        }
        osumx.filter();
    }
}
