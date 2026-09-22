package org.maxicp.search;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.state.datastructures.StateSparseSet;
import org.maxicp.modeling.ModelProxy;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Supplier;

import static org.maxicp.modeling.Factory.endBeforeStart;

/**
 * Rank Branching for Modeling Layer
 */
public class RankModeling implements Supplier<Runnable[]> {

    public static Supplier<Runnable[]> rank(IntervalVar[] intervals) {
        Ranker ranker = new Ranker(intervals);
        return ranker::alternatives;
    }

    IntervalVar[][] intervals;
    Ranker[] rankers;
    StateSparseSet notRanked;
    StateInt currentRanker;

    public RankModeling(IntervalVar[][] intervals) {
        this.intervals = intervals;
        this.rankers = new Ranker[intervals.length];
        StateManager sm = intervals[0][0].getModelProxy().getConcreteModel().getStateManager();
        this.notRanked = new StateSparseSet(sm, intervals.length, 0);
        this.currentRanker = sm.makeStateInt(-1);
        for (int i = 0; i < intervals.length; i++) {
            rankers[i] = new Ranker(intervals[i]);
        }
    }

    public RankModeling(IntervalVar[] intervals) {
        this(new IntervalVar[][] {intervals});
    }

    @Override
    public Runnable[] get() {
        if (currentRanker.value() == -1 || rankers[currentRanker.value()].isRanked()) {
            // need to find a new ranked
            int bestRankerId = -1;
            int bestSlack = Integer.MAX_VALUE;
            int [] notRankedIterator = new int[notRanked.size()];
            int nNotRanked = notRanked.fillArray(notRankedIterator);
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
                currentRanker.setValue(bestRankerId);
                return rankers[currentRanker.value()].alternatives();
            }
        } else {
            return rankers[currentRanker.value()].alternatives();
        }
    }

    static class Ranker {

        private final IntervalVar[] intervals;
        private final ModelProxy proxy;
        private final int[] notRankedIterator;
        private final StateSparseSet notRanked;

        Ranker(IntervalVar[] intervals) {
            this.intervals = intervals;
            this.proxy = intervals[0].getModelProxy();
            this.notRanked = new StateSparseSet(proxy.getConcreteModel().getStateManager(), intervals.length, 0);
            this.notRankedIterator = new int[intervals.length];
        }

        int slack() {
            int minEst = Integer.MAX_VALUE;
            int maxLct = Integer.MIN_VALUE;
            notRanked.fillArray(notRankedIterator);
            int nNotRanked = notRanked.size();
            for (int i = 0; i < nNotRanked; i++) {
                int taskId = notRankedIterator[i];
                IntervalVar interval = intervals[taskId];
                minEst = Integer.min(minEst, interval.startMin());
                maxLct = Integer.max(maxLct, interval.endMax());
            }
            return (maxLct - minEst) -
                    Arrays.stream(notRankedIterator, 0, nNotRanked).map(i -> intervals[i].lengthMin()).sum();
        }

        boolean isRanked() {
            return notRanked.isEmpty();
        }

        public Runnable[] alternatives() {
            assert (!isRanked());

            int [] notRankedIterator = new int[notRanked.size()];
            int nNotRanked = notRanked.fillArray(notRankedIterator); // fill the iterator with the unassigned tasks

            record RunnableWithPriority(int priority1, int priority2, Runnable action) {}

            RunnableWithPriority[] branches = new RunnableWithPriority[nNotRanked];

            for (int i = 0; i < nNotRanked; i++) {
                int i_ = i;
                int taskId = notRankedIterator[i];
                int priority1 = intervals[taskId].startMin();
                int priority2 = intervals[taskId].startMax();

                branches[i] = new RunnableWithPriority(priority1, priority2, () -> {
                    notRanked.remove(taskId);
                    for (int j = 0; j < nNotRanked; j++) {
                        if (i_ != j) {
                            int otherTaskId = notRankedIterator[j];
                            proxy.add(endBeforeStart(intervals[taskId], intervals[otherTaskId]));
                        }
                    }
                });
            }

            Arrays.sort(branches, Comparator.comparingInt(RunnableWithPriority::priority1).thenComparing(RunnableWithPriority::priority2));
            return Arrays.stream(branches).map(rwp -> rwp.action).toArray(Runnable[]::new);
        }
    }
}
