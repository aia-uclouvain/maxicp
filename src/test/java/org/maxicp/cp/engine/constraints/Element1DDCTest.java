/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;


import org.maxicp.cp.CPSolverTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;

import java.util.HashSet;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.*;

public class Element1DDCTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dTest1(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, -3, 10);
        CPIntVar z = CPFactory.makeIntVar(cp, 2, 40);

        int[] T = new int[]{9, 8, 7, 5, 6};

        cp.post(new Element1DDC(T, y, z));

        assertEquals(0, y.min());
        assertEquals(4, y.max());


        assertEquals(5, z.min());
        assertEquals(9, z.max());

        z.removeAbove(7);
        cp.fixPoint();

        assertEquals(2, y.min());

        y.remove(3);
        cp.fixPoint();

        assertEquals(7, z.max());
        assertEquals(6, z.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dTest2(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, -3, 10);
        CPIntVar z = CPFactory.makeIntVar(cp, -20, 40);

        int[] T = new int[]{9, 8, 7, 5, 6};

        cp.post(new Element1DDC(T, y, z));

        DFSearch dfs = CPFactory.makeDfs(cp, firstFailBinary(y, z));
        dfs.onSolution(() ->
                assertEquals(T[y.min()], z.min())
        );
        SearchStatistics stats = dfs.solve();

        assertEquals(5, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dTest3(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, 0, 4);
        CPIntVar z = CPFactory.makeIntVar(cp, 5, 9);


        int[] T = new int[]{9, 8, 7, 5, 6};

        cp.post(new Element1DDC(T, y, z));

        y.remove(3); //T[4]=5
        y.remove(0); //T[0]=9

        cp.fixPoint();

        assertEquals(6, z.min());
        assertEquals(8, z.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dTest4(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, 0, 4);
        CPIntVar z = CPFactory.makeIntVar(cp, 5, 9);


        int[] T = new int[]{9, 8, 7, 5, 6};

        cp.post(new Element1DDC(T, y, z));

        z.remove(9); //new max is 8
        z.remove(5); //new min is 6
        cp.fixPoint();

        assertFalse(y.contains(0));
        assertFalse(y.contains(3));
    }



    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dTest5(CPSolver cp) {
        Random rand = new Random(678);
        CPIntVar y = CPFactory.makeIntVar(cp, 0, 100);
        CPIntVar z = CPFactory.makeIntVar(cp, 0, 100);

        int[] T = new int[70];
        for (int i = 0; i < 70; i++)
            T[i] = rand.nextInt(100);

        cp.post(new Element1DDC(T, y, z));

        assertTrue(y.max() < 70);

        Supplier<Runnable[]> branching = () -> {
            if (y.isFixed() && z.isFixed()) {
                assertEquals(T[y.min()], z.min());
                return EMPTY;
            }
            int[] possibleY = new int[y.size()];
            y.fillArray(possibleY);

            int[] possibleZ = new int[z.size()];
            z.fillArray(possibleZ);

            HashSet<Integer> possibleValues = new HashSet<>();
            HashSet<Integer> possibleValues2 = new HashSet<>();
            for (int i = 0; i < possibleZ.length; i++) {
                possibleValues.add(possibleZ[i]);
            }
            for (int i = 0; i < possibleY.length; i++) {
                int val = T[possibleY[i]];
                assertTrue(possibleValues.contains(T[possibleY[i]]));
                possibleValues2.add(T[possibleY[i]]);
            }

            assertEquals(possibleValues.size(), possibleValues2.size());

            if (!y.isFixed() && (z.isFixed() || rand.nextBoolean())) {
                //select a random y
                int val = possibleY[rand.nextInt(possibleY.length)];
                return branch(() -> cp.post(CPFactory.eq(y, val)),
                        () -> cp.post(CPFactory.neq(y, val)));
            } else {
                int val = possibleZ[rand.nextInt(possibleZ.length)];
                return branch(() -> cp.post(CPFactory.eq(z, val)),
                        () -> cp.post(CPFactory.neq(z, val)));
            }
        };

        DFSearch dfs = CPFactory.makeDfs(cp, branching);

        SearchStatistics stats = dfs.solve();
        assertEquals(stats.numberOfSolutions(), T.length);
    }



}
