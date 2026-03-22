package org.maxicp.search;

import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;

import java.util.function.Supplier;

import static org.maxicp.cp.CPFactory.insert;
import static org.maxicp.cp.CPFactory.notBetween;
import static org.maxicp.search.Searches.*;

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

    record Priority(int priority1, int priority2, int value) {

        public Priority best(Priority other) {
            if (other == null)
                return this;
            if (this.priority1 < other.priority1) {
                return this;
            } else if (this.priority1 > other.priority1) {
                return other;
            }
            if (this.priority2 < other.priority2) {
                return this;
            } else {
                return other;
            }
        }
    }

    public Runnable[] alternatives() {
        int current = currentRanker.value();
        if (current == -1 || rankers[current].isRanked()) {
            // finds the sequence with the fewest insertable nodes remaining
            // ties are broken by taking the sequence with the smallest slack on the remaining nodes
            int nUnranked = unRanked.fillArray(buffer);
            Priority best = null;
            for (int i = 0 ; i < nUnranked ; i++) {
                int id = buffer[i];
                if (rankers[id].isRanked()) {
                    unRanked.remove(id);
                } else {
                    int nInsert = rankers[id].sequence.nNode(SeqStatus.INSERTABLE);
                    int slack = rankers[id].slack();
                    Priority candidate = new Priority(nInsert, slack, id);
                    best = candidate.best(best);
                }
            }
            if (best == null) {
                // all sequences are fixed
                return EMPTY;
            }
            current = best.value;
            currentRanker.setValue(current);
        }
        return rankers[current].alternatives();
    }

    static class Ranker {

        private CPSolver cp;
        private CPSeqVar sequence;
        private CPIntervalVar[] intervals;
        private int[] buffer;

        Ranker(CPIntervalVar[] intervals, CPSeqVar seqVar) {
            cp = seqVar.getSolver();
            this.intervals = intervals;
            this.sequence = seqVar;
            buffer = new int[seqVar.nNode()];
        }

        public int slack() {
            int minEst = Integer.MAX_VALUE;
            int maxLct = Integer.MIN_VALUE;
            int nUnranked = sequence.fillNode(buffer, SeqStatus.INSERTABLE);
            int duration = 0;
            for (int i = 0 ; i < nUnranked ; i++) {
                int node = buffer[i];
                CPIntervalVar interval = intervals[node];
                duration += interval.lengthMin();
                minEst = Integer.min(minEst, interval.startMin());
                maxLct = Integer.max(maxLct, interval.endMax());
            }
            return maxLct - minEst - duration;
        }

        public int slack(int pred, int node, int succ) {
            int tSucc = (succ >= intervals.length ? intervals[pred].endMax() : intervals[succ].endMax());
            int tPred = (pred >= intervals.length ? 0 : intervals[pred].startMin());
            int slackBefore = intervals[node].endMax() - tPred;
            int slackAfter = tSucc - intervals[node].startMin();
            return (slackAfter + slackBefore);
        }

        public int startMax(int node) {
            return node >= intervals.length ? 0 : intervals[node].startMax();
        }

        public int startMin(int node) {
            return node >= intervals.length ? 0 : intervals[node].startMin();
        }

        public int endMax(int node) {
            return node >= intervals.length ? 0 : intervals[node].endMax();
        }

        public int endMin(int node) {
            return node >= intervals.length ? 0 : intervals[node].endMin();
        }

        public boolean isRanked() {
            return sequence.isFixed();
        }

        public Runnable[] alternatives() {
            assert (!isRanked());
            // select the node to branch on
            int nUnranked = sequence.fillNode(buffer, SeqStatus.INSERTABLE);
            Priority best = null;
            for (int i = 0 ; i < nUnranked ; i++) {
                // pick the node with the fewest insertions. Break ties by startMin value
                int node = buffer[i];
                int nInsert = sequence.nInsert(node);
                int startMin = intervals[node].startMin();
                Priority candidate = new Priority(nInsert, startMin, node);
                best = candidate.best(best);
            }
            int node = best.value;
            // select the best insertion for the node
            int nInsert = sequence.fillInsert(node, buffer);
            best = null;
            for (int i = 0; i < nInsert ; i++) {
                // pick the predecessor leading to the largest slack.
                // Break ties by selecting the predecessor with the largest number of successors remaining
                int pred = buffer[i];
                int succ = sequence.memberAfter(pred);
                int slack = - slack(pred, node, succ);
                int nSucc = - sequence.nSucc(pred);
                Priority candidate = new Priority(slack, nSucc, pred);
                best = candidate.best(best);
            }
            int pred = best.value;
            int succ = sequence.memberAfter(pred);
            return branch(() -> cp.post(insert(sequence, pred, node)), () -> cp.post(notBetween(sequence, pred, node, succ)));
        }

    }

}
