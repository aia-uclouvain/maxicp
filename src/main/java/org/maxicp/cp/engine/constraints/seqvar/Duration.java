package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.constraints.Equal;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

import java.util.StringJoiner;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;

public class Duration extends AbstractCPConstraint {

    private final CPSeqVar seqVar;
    private final CPIntervalVar[] intervals;

    private final int[] nodes;                  // used for fill operations over the nodes
    private final int[] preds;                  // used for fill operations over the pred of nodes


    public Duration(CPSeqVar seqVar, CPIntervalVar[] intervals) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.intervals = intervals;
        this.nodes = new int[seqVar.nNode()];
        this.preds = new int[seqVar.nNode()];
    }

    @Override
    public void post() {
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnInsertRemoved(this);
        for (int node = 0 ; node < intervals.length ; node++) {
            CPIntervalVar interval = intervals[node];
            interval.propagateOnChange(this);
            getSolver().post(new Equal(interval.status(), seqVar.isNodeRequired(node)), false);
        }
        propagate();
    }

    @Override
    public void propagate() {
        int nMember = seqVar.fillNode(nodes, SeqStatus.MEMBER_ORDERED);
        // enforces into the intervals the precedences from the sequence
        updateTWForward(nMember);
        updateTWBackward(nMember);
        // filter invalid insertions
        filterInsertsAndPossiblyTW();
    }

    /**
     * Updates the min start time for intervals corresponding to member nodes
     * The array {@link Duration#nodes} must be filled with the nodes, in order of appearance.
     */
    private void updateTWForward(int nMember) {
        int current = nodes[0];
        int endMinPred = intervals[current].endMin();
        for (int i = 1 ; i < nMember ; ++i) {
            current = nodes[i];
            intervals[current].setStartMin(endMinPred);
            endMinPred = intervals[current].endMin();
        }
    }

    /**
     * Updates the max end time for intervals corresponding to member nodes
     * The array {@link Duration#nodes} must be filled with the nodes, in order of appearance.
     */
    private void updateTWBackward(int nMember) {
        int succ = nodes[nMember-1];
        int startMaxSucc = intervals[succ].startMax();
        for (int i = nMember - 2 ; i >= 0 ; --i) {
            int current = nodes[i];
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
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0 ; i < nInsertable ; ++i) {
            int node = nodes[i];
            filterInsertsAndPossiblyTW(node);
        }
    }

    /**
     * Filter the insertions for an insertable node
     * - if the node is required, filters all insertions and updates its time window
     * - otherwise, filters only the insertions
     */
    private void filterInsertsAndPossiblyTW(int node) {
        int nPred = seqVar.fillInsert(node, preds);
        if (seqVar.isNode(node, REQUIRED)) {
            // update the insertions as well as the time window
            int newStartMin = Integer.MAX_VALUE;
            int newEndMax = Integer.MIN_VALUE;
            for (int i = 0; i < nPred; ++i) {
                int pred = preds[i];
                if (!filterInsert(pred, node)) {
                    // if the insertion is valid, it can be used to update the start min and end max
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
        } else {
            for (int i = 0; i < nPred; ++i) {
                int pred = preds[i];
                filterInsert(pred, node);
            }
        }
    }

    /**
     * Filter an edge from the sequence if it would violate the time windows.
     * @param pred origin of the edge
     * @param node destination of the edge
     * @return true if the edge has been removed
     */
    private boolean filterInsert(int pred, int node) {
        int succ = seqVar.memberAfter(pred);
        int est = intervals[pred].endMin();
        if (est > intervals[node].startMax()) { // check that pred -> node is feasible
            seqVar.notBetween(pred, node, succ);
            return true;
        } else { // check that node -> succ is feasible
            int timeDeparture = Math.max(est, intervals[node].startMin());
            if (timeDeparture + intervals[node].lengthMin() > intervals[succ].startMax()) {
                // The detour pred->node->succ takes too much time.
                // Because of triangular inequality, there is no way to get a better result by inserting some node
                // between pred->node: this would only add a longer delay, the edge is still invalid.
                seqVar.notBetween(pred, node, succ);
                return true;
            }
        }
        return false;
    }

}
