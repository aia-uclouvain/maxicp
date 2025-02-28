/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Arrays;

import static org.maxicp.cp.examples.raw.SMoney.Letters.*;

/**
 * The Send-More-Money problem.
 *    S E N D
 * +  M O R E
 * ----------
 *  M O N E Y
 * All digits values are different.
 * Leading digits can't be zero
 */
public class SMoney {

    enum Letters {
        S(0), E(1), N(2), D(3), M(4), O(5), R(6), Y(7);
        public final int val;

        Letters(int v) {
            val = v;
        }
    };

    public static void main(String[] args) {
        CPSolver cp = CPFactory.makeSolver(false);
        CPIntVar[] values = CPFactory.makeIntVarArray(cp, Y.val + 1, 0, 9);
        CPIntVar[] carry = CPFactory.makeIntVarArray(cp, 4, 0, 1);

        cp.post(CPFactory.allDifferent(values));
        cp.post(CPFactory.notEqual(values[S.val], 0));
        cp.post(CPFactory.notEqual(values[M.val], 0));
        cp.post(CPFactory.equal(values[M.val], carry[3]));
        cp.post(CPFactory.equal(CPFactory.sum(carry[2], values[S.val], values[M.val], CPFactory.minus(values[O.val]), CPFactory.mul(carry[3], -10)), 0));
        cp.post(CPFactory.equal(CPFactory.sum(carry[1], values[E.val], values[O.val], CPFactory.minus(values[N.val]), CPFactory.mul(carry[2], -10)), 0));
        cp.post(CPFactory.equal(CPFactory.sum(carry[0], values[N.val], values[R.val], CPFactory.minus(values[E.val]), CPFactory.mul(carry[1], -10)), 0));
        cp.post(CPFactory.equal(CPFactory.sum(values[D.val], values[E.val], CPFactory.minus(values[Y.val]), CPFactory.mul(carry[0], -10)), 0));


        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFail(values));

        search.onSolution(() ->
                System.out.println("solution:" + Arrays.toString(values))
        );
        SearchStatistics stats = search.solve();
        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);
    }
}
