/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.cp.engine.core.CPSeqPatternVar;
import org.maxicp.cp.engine.core.CPSolver;

import java.util.function.Supplier;

import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Branching schemes over pattern variables.
 * <p>
 * The decisions are pattern refinements: for an itemset variable, the two
 * branches of a decision on a possible item i are require(i) and exclude(i).
 * This binary partition of the remaining completions makes the search complete.
 */
public final class PatternSearches {

    private PatternSearches() {
    }

    /**
     * Binary require/exclude branching over the possible items of an itemset
     * pattern variable, selecting the possible item with the smallest identifier.
     *
     * @param pattern the itemset pattern variable
     * @return a complete branching scheme fixing the variable
     */
    public static Supplier<Runnable[]> requireExclude(CPItemsetVar pattern) {
        int[] items = new int[pattern.nItems()];
        return () -> {
            if (pattern.isFixed())
                return EMPTY;
            int n = pattern.fillPossible(items);
            int item = items[0];
            for (int k = 1; k < n; k++) {
                item = Math.min(item, items[k]);
            }
            return branches(pattern, item);
        };
    }

    /**
     * Binary require/exclude branching over the possible items of an itemset
     * pattern variable, selecting the possible item with the smallest extension
     * support (fail-first: the require branch is the most constrained one).
     *
     * @param pattern the itemset pattern variable
     * @return a complete branching scheme fixing the variable
     */
    public static Supplier<Runnable[]> minExtensionSupport(CPItemsetVar pattern) {
        int[] items = new int[pattern.nItems()];
        return () -> {
            if (pattern.isFixed())
                return EMPTY;
            int n = pattern.fillPossible(items);
            int item = items[0];
            int bestSupport = pattern.supportAfter(item);
            for (int k = 1; k < n; k++) {
                int support = pattern.supportAfter(items[k]);
                if (support < bestSupport || (support == bestSupport && items[k] < item)) {
                    item = items[k];
                    bestSupport = support;
                }
            }
            return branches(pattern, item);
        };
    }

    private static Runnable[] branches(CPItemsetVar pattern, int item) {
        CPSolver cp = pattern.getSolver();
        return branch(
                () -> {
                    pattern.require(item);
                    cp.fixPoint();
                },
                () -> {
                    pattern.exclude(item);
                    cp.fixPoint();
                });
    }

    /**
     * Binary append/exclude branching for a sequence pattern variable.
     * At each node the candidate with the largest projected support is selected
     * (ties broken by the smallest symbol): the left branch appends it to the
     * prefix, the right branch excludes it from the candidate frontier.
     * <p>
     * The search is complete and enumerates every pattern exactly once: a pattern
     * is a solution at the unique node where its candidate frontier becomes empty,
     * at which point the variable auto-terminates.
     *
     * @param p the sequence pattern variable
     * @return a binary append/exclude branching strategy
     */
    public static Supplier<Runnable[]> appendExclude(CPSeqPatternVar p) {
        int[] candidates = new int[Math.max(p.nSymbols() - 1, 1)];
        CPSolver cp = p.getSolver();
        return () -> {
            if (p.isFixed())
                return EMPTY;
            int n = p.fillCandidates(candidates);
            int best = -1;
            int bestSup = -1;
            for (int i = 0; i < n; i++) {
                int a = candidates[i];
                int sup = p.supportAfter(a);
                if (sup > bestSup || (sup == bestSup && a < best)) {
                    best = a;
                    bestSup = sup;
                }
            }
            int a = best;
            return branch(
                    () -> {
                        p.append(a);
                        cp.fixPoint();
                    },
                    () -> {
                        p.excludeNext(a);
                        cp.fixPoint();
                    });
        };
    }
}
