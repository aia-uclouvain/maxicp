/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search;

import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;

import java.util.function.Supplier;

import static org.maxicp.cp.CPFactory.insert;
import static org.maxicp.search.Searches.EMPTY;

/**
 * Sequence-based ranking search inspired by {@link Rank}.
 *
 * <p>As long as the selected sequence (the one with the smallest slack) is not fully ranked,
 * a standard first-fail insertion search is applied to fix that sequence.
 * The node selected for insertion is the insertable one whose interval has the smallest earliest start time.
 * Branching tries every possible insertion position for the selected node.
 *
 * @author Pierre Schaus
 */
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

    public Runnable[] alternatives() {
        int current = currentRanker.value();
        if (current == -1 || rankers[current].isRanked()) {
            int nUnranked = unRanked.fillArray(buffer);
            int bestRankerId = -1;
            int bestSlack = Integer.MAX_VALUE;
            for (int i = 0; i < nUnranked; i++) {
                int id = buffer[i];
                if (rankers[id].isRanked()) {
                    unRanked.remove(id);
                    continue;
                }
                int slack = rankers[id].slack();
                if (slack < bestSlack) {
                    bestSlack = slack;
                    bestRankerId = id;
                }
            }
            if (bestRankerId == -1) {
                return EMPTY;
            }
            current = bestRankerId;
            currentRanker.setValue(current);
        }
        return rankers[current].alternatives();
    }

    static class Ranker {

        private final CPSolver cp;
        private final CPSeqVar sequence;
        private final CPIntervalVar[] intervals;
        private final int[] nodeBuffer;
        private final int[] insertBuffer;

        Ranker(CPIntervalVar[] intervals, CPSeqVar seqVar) {
            this.cp = seqVar.getSolver();
            this.intervals = intervals;
            this.sequence = seqVar;
            this.nodeBuffer = new int[seqVar.nNode()];
            this.insertBuffer = new int[seqVar.nNode()];
        }

        public int slack() {
            int minEst = Integer.MAX_VALUE;
            int maxLct = Integer.MIN_VALUE;
            int nInsertable = sequence.fillNode(nodeBuffer, SeqStatus.INSERTABLE);
            int duration = 0;
            for (int i = 0; i < nInsertable; i++) {
                int node = nodeBuffer[i];
                CPIntervalVar interval = intervals[node];
                duration += interval.lengthMin();
                minEst = Math.min(minEst, interval.startMin());
                maxLct = Math.max(maxLct, interval.endMax());
            }
            return maxLct - minEst - duration;
        }

        public boolean isRanked() {
            return sequence.isFixed();
        }

        public Runnable[] alternatives() {
            assert (!isRanked());

            int nInsertable = sequence.fillNode(nodeBuffer, SeqStatus.INSERTABLE);
            if (nInsertable == 0) {
                return EMPTY;
            }

            // select the insertable node with the smallest earliest start time
            int selected = -1;
            int bestStartMin = Integer.MAX_VALUE;
            for (int i = 0; i < nInsertable; i++) {
                int node = nodeBuffer[i];
                int startMin = intervals[node].startMin();
                if (startMin < bestStartMin) {
                    bestStartMin = startMin;
                    selected = node;
                }
            }

            final int node = selected;
            int nInsert = sequence.fillInsert(node, insertBuffer);

            // n-ary branching: one branch per possible insertion position
            Runnable[] branches = new Runnable[nInsert];
            for (int i = 0; i < nInsert; i++) {
                final int pred = insertBuffer[i];
                branches[i] = () -> cp.post(insert(sequence, pred, node));
            }
            return branches;
        }
    }
}