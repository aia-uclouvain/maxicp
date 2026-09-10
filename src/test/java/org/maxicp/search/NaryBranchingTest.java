/*
 * MaxiCP is under MIT License
 * Copyright (c)  2026 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.IntVar;

import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.modeling.Factory.makeModelDispatcher;

/**
 * The n-ary branchings must enumerate exactly the same solutions as the binary ones.
 */
class NaryBranchingTest {

    private static int solveCount(ModelDispatcher model, Supplier<Runnable[]> branching) {
        return model.runCP(cp -> {
            DFSearch search = cp.dfSearch(branching);
            return search.solve().numberOfSolutions();
        });
    }

    @Test
    void naryEnumeratesTheWholeContiguousDomain() throws Exception {
        try (ModelDispatcher model = makeModelDispatcher()) {
            IntVar x = model.intVar(0, 2);
            IntVar y = model.intVar(0, 2);

            int binaryCount = solveCount(model, Searches.staticOrderBinary(x, y));
            int naryCount = solveCount(model, Searches.staticOrderNary(x, y));

            assertEquals(9, binaryCount);
            assertEquals(binaryCount, naryCount);
        }
    }

    @Test
    void naryEnumeratesTheWholeSparseDomain() throws Exception {
        try (ModelDispatcher model = makeModelDispatcher()) {
            IntVar x = model.intVar(Set.of(1, 4, 9));
            IntVar y = model.intVar(Set.of(1, 4, 9));

            int binaryCount = solveCount(model, Searches.staticOrderBinary(x, y));
            int naryCount = solveCount(model, Searches.staticOrderNary(x, y));

            assertEquals(9, binaryCount);
            assertEquals(binaryCount, naryCount);
        }
    }

    @Test
    void naryWithValueHeuristicEnumeratesTheWholeSparseDomain() throws Exception {
        try (ModelDispatcher model = makeModelDispatcher()) {
            IntVar x = model.intVar(Set.of(1, 4, 9));
            IntVar y = model.intVar(Set.of(1, 4, 9));

            int binaryCount = solveCount(model, Searches.staticOrderBinary(x, y));
            // decreasing value order: the value-heuristic overload must still branch on every value
            int naryCount = solveCount(model,
                    Searches.heuristicNary(Searches.staticOrderVariableSelector(x, y), v -> -v));

            assertEquals(9, binaryCount);
            assertEquals(binaryCount, naryCount);
        }
    }
}
