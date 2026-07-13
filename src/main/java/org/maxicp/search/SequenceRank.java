package org.maxicp.search;

import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;

import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.maxicp.cp.CPFactory.endBeforeStart;
import static org.maxicp.cp.CPFactory.insert;
import static org.maxicp.search.Searches.EMPTY;

public class SequenceRank {

    private final StateInt currentRanker;
    private final Ranker[] rankers;
    private final StateSparseSet unRanked;
    private final int[] buffer;

    public static Supplier<Runnable[]> sequenceRank(CPIntervalVar[][] intervals, CPSeqVar[] seqVars) {
        SequenceRank sequenceRank = new SequenceRank(intervals, seqVars);
        return sequenceRank::alternatives;
    }

    public SequenceRank(CPIntervalVar[][] intervals, CPSeqVar[] seqVars) {
        CPSolver cp = seqVars[0].getSolver();
        int n = seqVars.length;
        unRanked = new StateSparseSet(cp.getStateManager(), n, 0);
        this.currentRanker = cp.getStateManager().makeStateInt(-1);
        this.rankers = new Ranker[n];
        for (int i = 0; i < n; i++) {
            rankers[i] = new Ranker(intervals[i], seqVars[i]);
        }
        buffer = new int[n];
    }

    // A simplified priority class to avoid using records, in case of compatibility issues.
    private static class Priority {
        final int priority1;
        final int priority2;
        final int value;

        Priority(int p1, int p2, int v) {
            this.priority1 = p1;
            this.priority2 = p2;
            this.value = v;
        }

        Priority best(Priority other) {
            if (other == null) return this;
            if (this.priority1 < other.priority1) return this;
            if (this.priority1 > other.priority1) return other;
            if (this.priority2 < other.priority2) return this;
            return other;
        }
    }

    public Runnable[] alternatives() {
        int current = currentRanker.value();
        if (current == -1 || rankers[current].isRanked()) {
            int nUnranked = unRanked.fillArray(buffer);
            Priority best = null;
            for (int i = 0; i < nUnranked; i++) {
                int id = buffer[i];
                if (rankers[id].isRanked()) {
                    unRanked.remove(id);
                } else {
                    int slack = rankers[id].slack();
                    Priority candidate = new Priority(slack, 0, id);
                    best = candidate.best(best);
                }
            }
            if (best == null) {
                return EMPTY;
            }
            current = best.value;
            currentRanker.setValue(current);
        }
        return rankers[current].alternatives();
    }

    static class Ranker {

        private final CPSolver cp;
        private final CPSeqVar sequence;
        private final CPIntervalVar[] intervals;
        private final int[] buffer;

        Ranker(CPIntervalVar[] intervals, CPSeqVar seqVar) {
            cp = seqVar.getSolver();
            this.intervals = intervals;
            this.sequence = seqVar;
            this.buffer = new int[seqVar.nNode()];
        }

        public int slack() {
            int minEst = Integer.MAX_VALUE;
            int maxLct = Integer.MIN_VALUE;
            int nUnranked = sequence.fillNode(buffer, SeqStatus.INSERTABLE);
            int duration = 0;
            for (int i = 0; i < nUnranked; i++) {
                int node = buffer[i];
                CPIntervalVar interval = intervals[node];
                duration += interval.lengthMin();
                minEst = Integer.min(minEst, interval.startMin());
                maxLct = Integer.max(maxLct, interval.endMax());
            }
            return maxLct - minEst - duration;
        }

        public boolean isRanked() {
            return sequence.isFixed();
        }

        public Runnable[] alternatives() {
            assert (!isRanked());

            int nInsertable = sequence.fillNode(buffer, SeqStatus.INSERTABLE);
            if (nInsertable == 0) {
                return EMPTY;
            }

            // Create a stable copy of the insertable nodes for use in the lambda
            final int[] insertableNodes = new int[nInsertable];
            System.arraycopy(buffer, 0, insertableNodes, 0, nInsertable);

            int pred = sequence.memberBefore(sequence.end());

            return IntStream.range(0, nInsertable)
                    .mapToObj(i -> insertableNodes[i])
                    .sorted(Comparator.comparingInt(i -> intervals[i].startMin()))
                    .map(nodeToInsert -> (Runnable) () -> {
                        // Insert the node into the sequence structure.
                        cp.post(insert(sequence, pred, nodeToInsert));
                        // Add explicit precedence constraints, like Rank does, to strengthen guidance.
                        for (int otherNode : insertableNodes) {
                            if (otherNode != nodeToInsert) {
                                cp.post(endBeforeStart(intervals[nodeToInsert], intervals[otherNode]));
                            }
                        }
                    })
                    .toArray(Runnable[]::new);
        }
    }
}