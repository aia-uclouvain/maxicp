/*
 * MaxiCP is under MIT License
 * Copyright (c)  2026 UCLouvain
 */

package org.maxicp.cp.modeling;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.symbolic.SymbolicModel;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.modeling.Factory.*;

class JumpToTest {

    @Test
    void jumpToIsNotQuadraticInTheSizeOfTheModel() throws Exception {
        try (ModelDispatcher md = makeModelDispatcher()) {
            int n = 5000;
            IntVar[] x = md.intVarArray(n, 3);
            for (int i = 0; i + 1 < n; i++)
                md.add(neq(x[i], x[i + 1]));
            SymbolicModel root = (SymbolicModel) md.getModel();

            md.runCP(root, (Consumer<ConcreteCPModel>) cp -> {
                SymbolicModel sub = cp.getStateManager().withNewState(() -> {
                    md.add(eq(x[0], 1));
                    md.add(eq(x[n / 2], 2));
                    return cp.symbolicCopy();
                });
                long t0 = System.nanoTime();
                for (int k = 0; k < 20; k++) {
                    cp.getStateManager().withNewState(() -> {
                        cp.jumpTo(sub, true);
                        assertEquals(1, x[0].min());
                        assertEquals(2, x[n / 2].min());
                    });
                }
                long millis = (System.nanoTime() - t0) / 1_000_000;
                // the nodes are compared by identity: this takes a few milliseconds, not a hash of the whole chain
                // of 5000 constraints for each of its nodes
                assertTrue(millis < 2000, "20 jumps took " + millis + " ms");
            });
        }
    }
}
