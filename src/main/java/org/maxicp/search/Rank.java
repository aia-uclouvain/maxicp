/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.PrecedenceGraph;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Supplier;

/**
 * Rank Branching for scheduling problems.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li>With a {@link PrecedenceGraph}: precedences are added to the graph (preferred for job-shop).</li>
 *   <li>Without a graph: individual {@code endBeforeStart} constraints are posted (legacy).</li>
 * </ul>
 * <p>
 * When multiple resource groups are given, the branching selects the resource with
 * the least slack first and ranks one activity at a time on that resource.
 *
 * @author Pierre Schaus
 */
public class Rank implements Supplier<Runnable[]> {

    /**
     * Creates a rank branching for a single resource group using a precedence graph.
     */
    public static Supplier<Runnable[]> rank(PrecedenceGraph graph, int[] indices) {
        Ranker ranker = new Ranker(graph, indices);
        return ranker::alternatives;
    }

    /**
     * Creates a rank branching for a single resource group (legacy, without graph).
     */
    public static Supplier<Runnable[]> rank(CPIntervalVar[] intervals) {
        Ranker ranker = new Ranker(intervals);
        return ranker::alternatives;
    }

    private final Ranker[] rankers;
    private final StateSparseSet notRanked;
    private final StateInt currentRanker;

    /**
     * Creates a rank branching for multiple resource groups using a precedence graph.
     * <p>
     * Each entry in {@code resourceIndices} is an array of global indices into the
     * precedence graph's variable array, representing the activities on one resource.
     *
     * @param graph           the shared precedence graph
     * @param resourceIndices resourceIndices[m] = global indices for resource m
     */
    public Rank(PrecedenceGraph graph, int[][] resourceIndices) {
        CPSolver cp = graph.getVars()[0].getSolver();
        int nResources = resourceIndices.length;
        this.rankers = new Ranker[nResources];
        this.notRanked = new StateSparseSet(cp.getStateManager(), nResources, 0);
        this.currentRanker = cp.getStateManager().makeStateInt(-1);
        for (int i = 0; i < nResources; i++) {
            rankers[i] = new Ranker(graph, resourceIndices[i]);
        }
    }

    /**
     * Creates a rank branching for a single resource group using a precedence graph.
     */
    public Rank(PrecedenceGraph graph, int[] indices) {
        this(graph, new int[][]{indices});
    }

    /**
     * Creates a rank branching for multiple resource groups (legacy, without graph).
     */
    public Rank(CPIntervalVar[][] intervals) {
        CPSolver cp = intervals[0][0].getSolver();
        int nResources = intervals.length;
        this.rankers = new Ranker[nResources];
        this.notRanked = new StateSparseSet(cp.getStateManager(), nResources, 0);
        this.currentRanker = cp.getStateManager().makeStateInt(-1);
        for (int i = 0; i < nResources; i++) {
            rankers[i] = new Ranker(intervals[i]);
        }
    }

    /**
     * Creates a rank branching for a single resource group (legacy, without graph).
     */
    public Rank(CPIntervalVar[] intervals) {
        this(new CPIntervalVar[][]{intervals});
    }

    @Override
    public Runnable[] get() {
        if (currentRanker.value() == -1 || rankers[currentRanker.value()].isRanked()) {
            // find the resource with the least slack
            int bestRankerId = -1;
            int bestSlack = Integer.MAX_VALUE;
            int[] buf = new int[notRanked.size()];
            int nNotRanked = notRanked.fillArray(buf);
            for (int i = 0; i < nNotRanked; i++) {
                if (rankers[buf[i]].isRanked()) {
                    notRanked.remove(buf[i]);
                    continue;
                }
                int slack = rankers[buf[i]].slack();
                if (slack < bestSlack) {
                    bestSlack = slack;
                    bestRankerId = buf[i];
                }
            }
            if (bestRankerId == -1) {
                return Searches.EMPTY;
            }
            currentRanker.setValue(bestRankerId);
            return rankers[currentRanker.value()].alternatives();
        } else {
            return rankers[currentRanker.value()].alternatives();
        }
    }

    // =====================================================================

    static class Ranker {

        private final PrecedenceGraph graph;   // null for legacy mode
        private final int[] globalIndices;     // null for legacy mode
        private final CPIntervalVar[] intervals;
        private final CPSolver cp;
        private final StateSparseSet notRanked;
        private final int[] notRankedBuf;      // scratch buffer for slack()

        /**
         * Graph-based constructor.
         */
        Ranker(PrecedenceGraph graph, int[] globalIndices) {
            this.graph = graph;
            this.globalIndices = globalIndices;
            int m = globalIndices.length;
            this.intervals = new CPIntervalVar[m];
            for (int i = 0; i < m; i++) {
                intervals[i] = graph.getVar(globalIndices[i]);
            }
            this.cp = intervals[0].getSolver();
            this.notRanked = new StateSparseSet(cp.getStateManager(), m, 0);
            this.notRankedBuf = new int[m];
        }

        /**
         * Legacy constructor (no graph).
         */
        Ranker(CPIntervalVar[] intervals) {
            this.graph = null;
            this.globalIndices = null;
            this.intervals = intervals;
            this.cp = intervals[0].getSolver();
            this.notRanked = new StateSparseSet(cp.getStateManager(), intervals.length, 0);
            this.notRankedBuf = new int[intervals.length];
        }

        int slack() {
            int minEst = Integer.MAX_VALUE;
            int maxLct = Integer.MIN_VALUE;
            int nNotRanked = notRanked.fillArray(notRankedBuf);
            int totalDur = 0;
            for (int i = 0; i < nNotRanked; i++) {
                CPIntervalVar iv = intervals[notRankedBuf[i]];
                minEst = Math.min(minEst, iv.startMin());
                maxLct = Math.max(maxLct, iv.endMax());
                totalDur += iv.lengthMin();
            }
            return (maxLct - minEst) - totalDur;
        }

        boolean isRanked() {
            return notRanked.isEmpty();
        }

        public Runnable[] alternatives() {
            assert (!isRanked());

            // snapshot of not-yet-ranked local task ids (captured by lambdas)
            int[] currentNotRanked = new int[notRanked.size()];
            int nNotRanked = notRanked.fillArray(currentNotRanked);

            record RunnableWithPriority(int priority1, int priority2, Runnable action) {}

            RunnableWithPriority[] branches = new RunnableWithPriority[nNotRanked];

            for (int i = 0; i < nNotRanked; i++) {
                int i_ = i;
                int taskId = currentNotRanked[i];
                int priority1 = intervals[taskId].startMin();
                int priority2 = intervals[taskId].startMax();

                if (graph != null) {
                    // ---- Precedence-graph mode ----
                    int globalFrom = globalIndices[taskId];
                    branches[i] = new RunnableWithPriority(priority1, priority2, () -> {
                        notRanked.remove(taskId);
                        int[] tos = new int[nNotRanked - 1];
                        int nTos = 0;
                        for (int j = 0; j < nNotRanked; j++) {
                            if (j != i_) {
                                tos[nTos++] = globalIndices[currentNotRanked[j]];
                            }
                        }
                        graph.addPrecedences(globalFrom, tos, nTos);
                    });
                } else {
                    // ---- Legacy mode: post endBeforeStart constraints ----
                    branches[i] = new RunnableWithPriority(priority1, priority2, () -> {
                        notRanked.remove(taskId);
                        for (int j = 0; j < nNotRanked; j++) {
                            if (j != i_) {
                                int otherId = currentNotRanked[j];
                                cp.post(CPFactory.endBeforeStart(intervals[taskId], intervals[otherId]));
                            }
                        }
                    });
                }
            }

            Arrays.sort(branches, Comparator.comparingInt(RunnableWithPriority::priority1)
                    .thenComparing(RunnableWithPriority::priority2));
            return Arrays.stream(branches).map(rwp -> rwp.action).toArray(Runnable[]::new);
        }
    }
}
