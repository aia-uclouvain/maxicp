/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.Equal;
import org.maxicp.cp.engine.constraints.scheduling.ThetaTree;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.Arrays;
import java.util.Comparator;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class Duration extends AbstractCPConstraint {

    private final int n;
    private final ThetaTree thetaTree;
    private final CPSeqVar seqVar;
    private final CPSeqVar flip;
    private final CPIntervalVar[] intervals;
    private LeftToRightFiltering filtering;

    private int nMember;
    private final int[] ordering;                  // used for fill operations over the nodes
    private final int[] insertable;                  // used for fill operations over the nodes
    private final int[] inserts;                  // used for fill operations over the pred of nodes

    private final int[] startMin;
    private final int[] duration;


    public Duration(CPSeqVar seqVar, CPIntervalVar[] intervals) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.flip = CPFactory.flip(seqVar);
        this.intervals = intervals;
        n = seqVar.nNode();
        thetaTree = new ThetaTree(n);
        this.ordering = new int[n];
        this.insertable = new int[n];
        this.inserts = new int[n];
        this.startMin = new int[n];
        this.duration = new int[n];
        filtering = new LeftToRightFiltering();
    }

    @Override
    public void post() {
        for (int node = 0 ; node < intervals.length ; node++) {
            CPIntervalVar interval = intervals[node];
            getSolver().post(new Equal(interval.status(), seqVar.isNodeRequired(node)), false);
            interval.propagateOnChange(this);
        }
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnInsertRemoved(this);
        propagate();
    }

    @Override
    public void propagate() {
        nMember = seqVar.fillNode(ordering, MEMBER_ORDERED);
        // enforces into the intervals the precedences from the sequence due to the member nodes
        updateTWForward();
        updateTWBackward();
        // filter insertions
        filterInsertsAndPossiblyTW();
        // filter startMin
        int nUnexcluded = seqVar.fillNode(ordering, NOT_EXCLUDED);
        for (int i = 0 ; i < nUnexcluded ; i++) {
            int node = ordering[i];
            startMin[node] = intervals[node].startMin();
            duration[node] = intervals[node].lengthMin();
        }
        filtering.filter(seqVar, startMin, duration);
        for (int i = 0 ; i < nUnexcluded ; i++) {
            int node = ordering[i];
            intervals[node].setStartMin(filtering.startMinNew[node]);
        }
        // filter endMax
        nUnexcluded = seqVar.fillNode(ordering, NOT_EXCLUDED);
        for (int i = 0 ; i < nUnexcluded ; i++) {
            int node = ordering[i];
            startMin[node] = - intervals[node].endMax();
            duration[node] = intervals[node].lengthMin();
        }
        filtering.filter(flip, startMin, duration);
        for (int i = 0 ; i < nUnexcluded ; i++) {
            int node = ordering[i];
            intervals[node].setEndMax(- filtering.startMinNew[node]);
        }
    }

    private class LeftToRightFiltering {

        // nMandatoryPred[pos] = how many insertable required nodes can come before this position
        private final int[] nMandatoryPred;
        // mandatoryPred[nMandatoryPred[pos]..nMandatoryPred[pos+1]] = insertable required nodes coming before this position
        private final int[] mandatoryPred;

        // nEarliestPredecessor[pos] = how many insertable nodes have this position as first insertion
        private final int[] nEarliestPredecessor;
        // earliestPredecessor[nEarliestPredecessor[pos]..nEarliestPredecessor[pos+1]] = insertable nodes having this position as first insertion
        private final int[] earliestPredecessor;
        private final int[] position;

        private final int[] earliestPosition;
        private final int[] latestPosition;

        private final int[] startMinNew;
        private final Integer[] permEst, rankEst;
        private final int[] required;

        public LeftToRightFiltering() {
            startMinNew = new int[n];
            required = new int[n];
            permEst = new Integer[n];
            rankEst = new Integer[n];
            nMandatoryPred = new int[n];
            mandatoryPred = new int[n];
            nEarliestPredecessor = new int[n];
            earliestPredecessor = new int[n];
            earliestPosition = new int[n];
            latestPosition = new int[n];
            position = new int[n];
        }

        private void update(int i, int startMin) {
            this.startMinNew[i] = startMin;
        }

        private void updatePositionMember(CPSeqVar seqVar, int[] startMin) {
            nMember = seqVar.fillNode(ordering, MEMBER_ORDERED);
            for (int pos = 0 ; pos < nMember ; pos++) {
                int node = ordering[pos];
                position[node] = pos;
                update(node, startMin[node]);
            }
        }

        private void updatePositionInsertable(CPSeqVar seqVar, int[] startMin) {
            int nInsertable = seqVar.fillNode(insertable, INSERTABLE);
            for (int i = 0 ; i < nInsertable ; i++) {
                int node = insertable[i];
                int nInsert = seqVar.fillInsert(node, inserts);
                earliestPosition[node] = Integer.MAX_VALUE;
                latestPosition[node] = Integer.MIN_VALUE;
                for (int j = 0 ; j < nInsert ; j++) {
                    int pred = inserts[j];
                    int pos = position[pred];
                    earliestPosition[node] = Math.min(earliestPosition[node], pos);
                    latestPosition[node] = Math.max(latestPosition[node], pos + 1);
                }
                update(node, startMin[node]);
            }
        }

        private void updateMandatoryPred() {
            int nInsertableRequired = seqVar.fillNode(insertable, INSERTABLE_REQUIRED);
            for (int i = 0 ; i < nInsertableRequired ; i++)
                permEst[i] = insertable[i];
            Arrays.sort(permEst, 0, nInsertableRequired, Comparator.comparingInt(i -> latestPosition[i]));
            int predPos = 0;
            for (int i = 0 ; i < nInsertableRequired ; i++) {
                int node = permEst[i];
                int pos = latestPosition[node];
                if (pos != predPos) {
                    for (int j = predPos ; j < pos ; j++) {
                        nMandatoryPred[j] = i;
                    }
                    predPos = pos;
                }
                mandatoryPred[i] = node;
            }
            for (int j = predPos ; j < nMember ; j++) {
                nMandatoryPred[j] = nInsertableRequired;
            }
        }

        private void updateEarliestPred() {
            int nInsertable = seqVar.fillNode(insertable, INSERTABLE);
            for (int i = 0 ; i < nInsertable ; i++)
                permEst[i] = insertable[i];
            Arrays.sort(permEst, 0, nInsertable, Comparator.comparingInt(i -> earliestPosition[i]));
            int predPos = 0;
            for (int i = 0 ; i < nInsertable ; i++) {
                int node = permEst[i];
                int pos = earliestPosition[node];
                if (pos != predPos) {
                    for (int j = predPos ; j < pos ; j++) {
                        nEarliestPredecessor[j] = i;
                    }
                    predPos = pos;
                }
                earliestPredecessor[i] = node;
            }
            for (int j = predPos ; j < nMember ; j++) {
                nEarliestPredecessor[j] = nInsertable;
            }
        }

        private void update(CPSeqVar seqVar, int[] startMin) {
            // update the position of member nodes
            updatePositionMember(seqVar, startMin);
            // compute the earliest and latest position for the insertable required nodes
            updatePositionInsertable(seqVar, startMin);
            // updates nMandatoryPred and mandatoryPred
            updateMandatoryPred();
            // updates nEarliestPredecessor and earliestPredecessor
            updateEarliestPred();
            // updates rankEst and permEst
            int nRequired = seqVar.fillNode(required, REQUIRED);
            for (int i = 0 ; i < nRequired ; i++)
                permEst[i] = required[i];
            Arrays.sort(permEst, 0, nRequired, Comparator.comparingInt(i -> startMin[i]));
            for (int i = 0; i < nRequired; i++) {
                int node = permEst[i];
                rankEst[node] = i;
            }
        }

        /**
         * For each interval, set its start min to the earliest completion tasks of all intervals coming before it
         * <p>
         * - if the interval is a member node, this takes into account
         * (i) its member predecessors and
         * (ii) the required nodes that must come be inserted it
         * <p>
         * - if the interval is an insertable node, this takes into account all nodes coming before its first insertion
         *
         */
        private void filter(CPSeqVar seqVar, int[] startMin, int[] duration) {
            update(seqVar, startMin);
            thetaTree.reset();
            int start = seqVar.start();
            thetaTree.insert(rankEst[start], startMin[start], duration[start]);
            for (int pos = 1 ; pos < nMember ; pos++) {
                int node = ordering[pos];
                // add the required insertable nodes having this node as their latest successor
                for (int j = nMandatoryPred[pos-1] ; j < nMandatoryPred[pos] ; j++) {
                    int mandatory = mandatoryPred[j];
                    thetaTree.insert(rankEst[mandatory], startMin[mandatory], duration[mandatory]);
                }
                startMinNew[node] = Math.max(startMin[node], thetaTree.getEct());
                thetaTree.insert(rankEst[node], startMin[node], duration[node]);
                // updates for insertable nodes having this node as their earliest predecessor
                for (int j = nEarliestPredecessor[pos-1] ; j < nEarliestPredecessor[pos] ; j++) {
                    int insertable = earliestPredecessor[j];
                    startMinNew[insertable] = Math.max(startMin[insertable], thetaTree.getEct());
                }
            }
        }

    }


    /**
     * Updates the min start time for intervals corresponding to member nodes
     * The array {@link Duration#ordering} must be filled with the nodes, in order of appearance.
     */
    private void updateTWForward() {
        int current = ordering[0];
        int endMinPred = intervals[current].endMin();
        for (int i = 1 ; i < nMember ; i++) {
            current = ordering[i];
            intervals[current].setStartMin(endMinPred);
            endMinPred = intervals[current].endMin();
        }
    }

    /**
     * Updates the max end time for intervals corresponding to member nodes
     * The array {@link Duration#ordering} must be filled with the nodes, in order of appearance.
     */
    private void updateTWBackward() {
        int succ = ordering[nMember-1];
        int startMaxSucc = intervals[succ].startMax();
        for (int i = nMember - 2 ; i >= 0 ; i--) {
            int current = ordering[i];
            intervals[current].setEndMax(startMaxSucc);
            startMaxSucc = intervals[current].startMax();
        }
    }

    /**
     * Filter the insertions for all the insertable nodes
     * - if the node is required, filters all insertions and updates its time window
     * - otherwise, filters only the insertions
     */
    private void filterInsertsAndPossiblyTW() {
        int nInsertable = seqVar.fillNode(ordering, INSERTABLE);
        for (int i = 0 ; i < nInsertable ; i++) {
            int node = ordering[i];
            filterInsertsAndPossiblyTW(node);
        }
    }

    /**
     * Filter the insertions for an insertable node and updates its time window
     */
    private void filterInsertsAndPossiblyTW(int node) {
        int nPred = seqVar.fillInsert(node, inserts);
        // update the insertions as well as the time window
        int newStartMin = Integer.MAX_VALUE;
        int newEndMax = Integer.MIN_VALUE;
        for (int i = 0; i < nPred; i++) {
            int pred = inserts[i];
            if (!filterInsert(pred, node)) {
                // if the insertion is feasible, it can be used to update the start min and end max
                int est = intervals[pred].endMin(); // start min candidate for the node
                newStartMin = Math.min(newStartMin, est);
                int succ = seqVar.memberAfter(pred);
                int lct = intervals[succ].startMax(); // end max candidate for the node
                newEndMax = Math.max(newEndMax, lct);
            }
        }
        if (!seqVar.isNode(node, MEMBER)) {
            // if the node has not become inserted because of the insertions removal, update its time window
            intervals[node].setStartMin(newStartMin);
            intervals[node].setEndMax(newEndMax);
        }
    }

    /**
     * Filter an edge from the sequence if it would violate the time windows.
     * @param pred origin of the edge
     * @param node destination of the edge
     * @return true if the edge has been removed
     */
    private boolean filterInsert(int pred, int node) {
        int succ = seqVar.memberAfter(pred); // successor of pred in the current partial sequence
        if (intervals[pred].endMin() > intervals[node].startMax()) { // no way node can fit after pred because we cannot delay node enough
            seqVar.notBetween(pred, node, succ);
            return true;
        } else { // check that node -> succ is feasible
            int nodeEst = Math.max(intervals[pred].endMin(), intervals[node].startMin());
            int nodeEct = nodeEst + intervals[node].lengthMin();
            if (nodeEct > intervals[succ].startMax()) { // no way node can fit between pred and succ because we cannot delay succ enough
                seqVar.notBetween(pred, node, succ);
                return true;
            }
        }
        return false;
    }

}
