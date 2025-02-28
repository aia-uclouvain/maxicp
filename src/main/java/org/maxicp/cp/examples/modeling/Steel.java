/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.modeling.CPModelInstantiator;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.bool.BoolExpression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.io.InputReader;

import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Steel is produced by casting molten iron into slabs.
 * A steel mill can produce a finite number of slab sizes.
 * An order has two properties, a colour corresponding to the route required through the steel mill and a weight.
 * Given d input orders, the problem is to assign the orders to slabs, the number and size of which are also to be determined,
 * such that the total weight of steel produced is minimised.
 * This assignment is subject to two further constraints:
 * - Capacity constraints: The total weight of orders assigned to a slab cannot exceed the slab capacity.
 * - Colour constraints: Each slab can contain at most p of k total colours (p is usually 2).
 * <a href="http://www.csplib.org/Problems/prob038/">CSPLib</a>
 */
public class Steel {

    public static void main(String[] args) {

        // Reading the data

        InputReader reader = new InputReader("data/STEEL/bench_19_0");
        int nCapa = reader.getInt();
        int[] capa = new int[nCapa];
        for (int i = 0; i < nCapa; i++) {
            capa[i] = reader.getInt();
        }
        int maxCapa = capa[capa.length - 1];
        int[] loss = new int[maxCapa + 1];
        int capaIdx = 0;
        for (int i = 0; i < maxCapa; i++) {
            loss[i] = capa[capaIdx] - i;
            if (loss[i] == 0) capaIdx++;
        }
        loss[0] = 0;

        int nCol = reader.getInt();
        int nSlab = reader.getInt();
        int nOrder = nSlab;
        int[] w = new int[nSlab];
        int[] c = new int[nSlab];
        for (int i = 0; i < nSlab; i++) {
            w[i] = reader.getInt();
            c[i] = reader.getInt() - 1;
        }

        // ---------------------------


        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntVar[] x = baseModel.intVarArray(nOrder, nSlab);
        IntVar[] l = baseModel.intVarArray(nSlab, maxCapa + 1);

        BoolExpression[][] inSlab = new BoolExpression[nSlab][nOrder]; // inSlab[j][i] = 1 if order i is placed in slab j

        for (int j = 0; j < nSlab; j++) {
            for (int i = 0; i < nOrder; i++) {
                inSlab[j][i] = eq(x[i], j);
            }
        }

        for (int j = 0; j < nSlab; j++) {
            // for each color, is it present in the slab
            BoolExpression[] presence = new BoolExpression[nCol];
            for (int col = 0; col < nCol; col++) {
                ArrayList<BoolExpression> inSlabWithColor = new ArrayList<>();
                for (int i = 0; i < nOrder; i++) {
                    if (c[i] == col) inSlabWithColor.add(inSlab[j][i]);
                }

                presence[col] = or(inSlabWithColor.toArray(new BoolExpression[0]));
            }
            baseModel.add(le(sum(presence), 2));
        }

        // bin packing constraint
        baseModel.add(binPacking(x, w, l));

        IntExpression totLoss = sum(IntStream.range(0, nSlab).mapToObj(j -> get(loss, l[j])).toArray(IntExpression[]::new));

        Objective obj = baseModel.minimize(totLoss);


        baseModel.runAsConcrete(CPModelInstantiator.withTrailing, (cp) -> {

            DFSearch search = cp.dfSearch(() -> {
                IntVar xs = Searches.selectMin(x,
                        xi -> xi.size() > 1,
                        xi -> xi.size());
                if (xs == null) return EMPTY;
                else {
                    int maxUsed = -1;
                    for (IntVar xi : x)
                        if (xi.isFixed() && xi.min() > maxUsed)
                            maxUsed = xi.min();
                    Runnable[] branches = new Runnable[maxUsed + 2];
                    for (int i = 0; i <= maxUsed + 1; i++) {
                        final int slab = i;
                        branches[i] = () -> baseModel.add(eq(xs, slab));
                    }
                    return branch(branches);
                }
            });

            search.onSolution(() -> {
                System.out.println(totLoss);
            });
            long t0 = System.currentTimeMillis();
            SearchStatistics statistics = search.optimize(obj);
            System.out.println("Time (ms): " + (System.currentTimeMillis() - t0));
            System.out.println(statistics);
        });

    }
}
