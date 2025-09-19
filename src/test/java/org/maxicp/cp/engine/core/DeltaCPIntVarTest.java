/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.state.State;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.makeDfs;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;


public class DeltaCPIntVarTest extends CPSolverTest {

    public Set<Integer> setOf(int [] values) {
        Set<Integer> s = new HashSet<>();
        for (int i: values) {
            s.add(i);
        }
        return s;
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp,Set.of(1,3,5,7));
        boolean [] propag = new boolean[]{false};
        int [] removed = new int[x.size()];
        cp.post(new AbstractCPConstraint(cp) {

            DeltaCPIntVar delta = x.delta(this);

            @Override
            public void post() {
                super.post();
                x.propagateOnBoundChange(this);
            }

            @Override
            public void propagate() {
                propag[0] = true;
                assertTrue(delta.changed());
                assertEquals(2,delta.size());
                assertTrue(delta.maxChanged());
                assertFalse(delta.minChanged());
                assertEquals(1,delta.oldMin());
                assertEquals(7,delta.oldMax());
                int s = delta.fillArray(removed);
                assertEquals(2,s);
                assertEquals(Set.of(5,7),setOf(Arrays.copyOfRange(removed,0,s)));
            }
        });

        cp.post(CPFactory.le(x,4));
        assertTrue(propag[0]);

    }


    private static Set<Integer> domain(CPIntVar x) {
        Set<Integer> values = new HashSet<>();
        for (int i = x.min(); i <= x.max(); i++) {
            if (x.contains(i)) {
                values.add(i);
            }
        }
        return values;
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp,Set.of(1,3,5,7,10,11,12,13,14,15,16,20,24,26,27));
        State<Set<Integer>> previousDom = cp.getStateManager().makeStateRef(domain(x));
        Random rand = new Random();
        int max = x.max();

        int [] removed = new int[x.size()];

        cp.post(new AbstractCPConstraint(cp) {

            DeltaCPIntVar delta = x.delta(this);
            @Override
            public void post() {
                super.post();
                x.propagateOnDomainChange(this);
            }

            @Override
            public void propagate() {
                Set<Integer> deltaSetExpected = new HashSet<>(previousDom.value());
                deltaSetExpected.removeAll(domain(x));
                int s = delta.fillArray(removed);
                Set<Integer> deltaComputed = setOf(Arrays.copyOfRange(removed,0,s));
                assertEquals(deltaSetExpected,deltaComputed);
                previousDom.setValue(domain(x));
            }
        });

        cp.post(new AbstractCPConstraint(cp) {

            @Override
            public void post() {
                super.post();
                x.propagateOnDomainChange(this);
            }

            @Override
            public void propagate() {
                x.remove(rand.nextInt(max-1));
            }
        });

        DFSearch dfs = makeDfs(cp, () -> {
            if (x.isFixed()) return EMPTY;
            else {
                final int v = x.min()+(x.max()-x.min())/2;
                return branch(() -> cp.post(CPFactory.le(x, v)),
                        () -> cp.post(CPFactory.ge(x, v+1)));
            }
        });
        SearchStatistics stats = dfs.solve();


    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void test3(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp,5,9);

        int [] removed = new int[x.size()];

        cp.post(new AbstractCPConstraint(cp) {

            DeltaCPIntVar delta;
            @Override
            public void post() {
                super.post();
                delta = x.delta(this);
                x.propagateOnDomainChange(this);
            }

            @Override
            public void propagate() {
                int s = delta.fillArray(removed);
                Set<Integer> deltaComputed = setOf(Arrays.copyOfRange(removed,0,s));
                assertEquals(Set.of(8,9), deltaComputed);
            }
        });

        cp.post(CPFactory.le(x,7));

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void test4(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp,1,10);

        int [] removed = new int[x.size()];

        cp.post(new AbstractCPConstraint(cp) {

            DeltaCPIntVar delta;
            @Override
            public void post() {
                super.post();
                delta = x.delta(this);
                x.propagateOnDomainChange(this);
            }

            @Override
            public void propagate() {
                int s = delta.fillArray(removed);
                Set<Integer> deltaComputed = setOf(Arrays.copyOfRange(removed,0,s));
                assertEquals(Set.of(1,2,3,4,5), deltaComputed);
            }
        });

        x.remove(1);
        x.remove(2);
        x.remove(4); // the domain will switch to a sparse-set
        x.remove(5);
        x.remove(3);

        cp.fixPoint();

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void test5(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp,1,10);

        int [] removed = new int[x.size()];

        boolean [] propag = new boolean[]{false};

        x.whenDomainChange(delta -> {
            propag[0] = true;
            int s = delta.fillArray(removed);
            Set<Integer> deltaComputed = setOf(Arrays.copyOfRange(removed, 0, s));
            assertEquals(Set.of(1, 2, 3, 4, 5), deltaComputed);
            assertTrue(delta.changed());
            assertEquals(5, delta.size());
            assertFalse(delta.maxChanged());
            assertTrue(delta.minChanged());
            assertEquals(1, delta.oldMin());
            assertEquals(10, delta.oldMax());
        });

        x.remove(1);
        x.remove(2);
        x.remove(4); // the domain will switch to a sparse-set
        x.remove(5);
        x.remove(3);

        cp.fixPoint();

        assertTrue(propag[0]);

    }



}
