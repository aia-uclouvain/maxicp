/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Supplier;

/**
 * Rank Branching
 * @author Pierre Schaus
 */
public class Rank {

    public static Supplier<Runnable[]> rank(CPIntervalVar[][] intervals) {
        Rank rank = new Rank(intervals);
        return rank::alternatives;
    }

    CPIntervalVar[][] intervals;
    Ranker[] rankers;
    StateSparseSet notRanked;
    int [] notRankedIterator;
    StateInt currentRanker;

    public Rank(CPIntervalVar[][] intervals) {
        this.intervals = intervals;
        this.rankers = new Ranker[intervals.length];
        CPSolver cp = intervals[0][0].getSolver();
        this.notRanked = new StateSparseSet(cp.getStateManager(), intervals.length, 0);

        this.notRankedIterator = new int[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            rankers[i] = new Ranker(intervals[i]);
        }
    }

    public Runnable[] alternatives_() {
        int nNotRanked = notRanked.fillArray(notRankedIterator);
        // find the ranker with the least slack
        int bestRankerId = -1;
        int bestSlack = Integer.MAX_VALUE;
        for (int i = 0; i < nNotRanked; i++) {
            if (rankers[notRankedIterator[i]].isRanked()) {
                notRanked.remove(notRankedIterator[i]);
                continue;
            }
            int rankerId = notRankedIterator[i];
            int slack = rankers[rankerId].slack();
            if (slack < bestSlack) {
                bestSlack = slack;
                bestRankerId = rankerId;
            }
        }
        if (bestRankerId == -1) {
            return Searches.EMPTY;
        } else {
            Ranker bestRanker = rankers[bestRankerId];
            return bestRanker.alternatives();
        }

    }

    public Runnable[] alternatives() {
        int nNotRanked = notRanked.fillArray(notRankedIterator);
        // find the ranker with the least slack
        int bestRankerId = -1;
        int bestSlack = Integer.MAX_VALUE;
        for (int i = 0; i < nNotRanked; i++) {
            if (rankers[notRankedIterator[i]].isRanked()) {
                notRanked.remove(notRankedIterator[i]);
                continue;
            }
            int rankerId = notRankedIterator[i];
            int slack = rankers[rankerId].slack();
            if (slack < bestSlack) {
                bestSlack = slack;
                bestRankerId = rankerId;
            }
        }
        if (bestRankerId == -1) {
            return Searches.EMPTY;
        } else {
            Ranker bestRanker = rankers[bestRankerId];
            return bestRanker.alternatives();
        }

    }




    static class Ranker {

        private final CPIntervalVar[] intervals;
        private final CPSolver cp;
        private final int[] notRankedIterator;
        private final StateSparseSet notRanked;

        Ranker(CPIntervalVar[] intervals) {
            this.intervals = intervals;
            this.cp = intervals[0].getSolver();
            this.notRanked = new StateSparseSet(cp.getStateManager(), intervals.length, 0);
            this.notRankedIterator = new int[intervals.length];
        }

        int slack() {
            int minEst = Integer.MAX_VALUE;
            int maxLct = Integer.MIN_VALUE;
            notRanked.fillArray(notRankedIterator);
            int nNotRanked = notRanked.size();
            for (int i = 0; i < nNotRanked; i++) {
                int taskId = notRankedIterator[i];
                CPIntervalVar interval = intervals[taskId];
                minEst = Integer.min(minEst, interval.startMin());
                maxLct = Integer.max(maxLct, interval.endMax());
            }
            return (maxLct - minEst) - Arrays.stream(notRankedIterator, 0, nNotRanked).map(i -> intervals[i].lengthMin()).sum();
        }

        boolean isRanked() {
            return notRanked.isEmpty();
        }

        public Runnable[] alternatives() {
            assert (!isRanked());
            int nNotRanked = notRanked.fillArray(notRankedIterator); // fill the iterator with the unassigned tasks

            record RunnableWithPriority(int priority1, int priority2, Runnable action) {}

            RunnableWithPriority[] branches = new RunnableWithPriority[nNotRanked];

            for (int i = 0; i < nNotRanked; i++) {
                int i_ = i;
                int taskId = notRankedIterator[i];
                int priority1 = intervals[taskId].startMin();
                int priority2 = -intervals[taskId].lengthMin();
                branches[i] = new RunnableWithPriority(priority1, priority2, () -> {
                    notRanked.remove(taskId);
                    for (int j = 0; j < nNotRanked; j++) {
                        if (i_ != j) {
                            int otherTaskId = notRankedIterator[j];
                            cp.post(CPFactory.endBeforeStart(intervals[taskId], intervals[otherTaskId]));
                        }
                    }
                });
            }

            Arrays.sort(branches, Comparator.comparingInt(RunnableWithPriority::priority1).thenComparing(RunnableWithPriority::priority2));
            return Arrays.stream(branches).map(rwp -> rwp.action).toArray(Runnable[]::new);
        }


    }
}
