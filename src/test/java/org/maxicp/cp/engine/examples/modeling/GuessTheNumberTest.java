/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.examples.modeling;


import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.modeling.Factory.*;


public class GuessTheNumberTest extends CPSolverTest {

    @Test
    public void test() {

        /**
         *  I know a 5 digits number having a property that with a 1 after it,
         *  it is three times as large as it would be with a one before it.
         *  Guess the number ?
         */
        ModelDispatcher model = Factory.makeModelDispatcher();

        IntVar [] digits = model.intVarArray(5, 10);
        // with a one after (larger one)
        // 100000 * digits[0] + 10000 * digits[1] + 1000 * digits[2] + 100 * digits[3] + 10 * digits[4] + 1
        IntExpression nb1 = sum(mul(digits[0], 100000),mul(digits[1], 10000), mul(digits[2], 1000), mul(digits[3], 100), mul(digits[4], 10),  model.intVar(1,1));
        // with a one before (smaller one)
        // 10000 * digits[0] + 1000 * digits[1] + 100 * digits[2] + 10 * digits[3] + digits[4] + 100000
        IntExpression nb2 = sum(mul(digits[0], 10000), mul(digits[1], 1000), mul(digits[2], 100), mul(digits[3], 10), digits[4], model.intVar(100000,100000));

        IntExpression nb2times3 = mul(nb2, 3);

        model.add(eq(nb1, mul(nb2, 3)));

        ConcreteCPModel cp = model.cpInstantiate();


        DFSearch dfs = cp.dfSearch(Searches.staticOrder(digits));

        dfs.onSolution(() -> {
            assertEquals(428571, nb1.min());
            assertEquals(142857, nb2.min());
            // the number is 42857
            assertEquals(4,digits[0].min());
            assertEquals(2,digits[1].min());
            assertEquals(8,digits[2].min());
            assertEquals(5,digits[3].min());
            assertEquals(7,digits[4].min());
        });
        SearchStatistics stats = dfs.solve(); // actually solve the problem

        assertEquals(1, stats.numberOfSolutions());



    }


}
