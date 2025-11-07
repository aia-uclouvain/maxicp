package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.Constants;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlap;
import org.maxicp.cp.engine.constraints.scheduling.ThetaTree;
import org.maxicp.cp.engine.core.*;

import java.util.Arrays;
import java.util.Comparator;

import static org.maxicp.cp.CPFactory.eq;
import static org.maxicp.cp.CPFactory.makeIntervalVar;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class DistanceScheduling extends AbstractDistance {

    /**
     * Attach "time windows" to every node, representing the distance at which the nodes are reached.
     * The duration of a node is the distance to its successor.
     *
     * NoOverlap algorithms are used to filter the time windows, which in turn may invalidate some insertions
     *
     * start[node] = cumulative distance at which the node is reached
     * duration[node] = distance increment to reach the successor of the node
     * end[node] = cumulative distance at which the successor of the node is reached
     *
     */

    CPIntervalVar[] intervals;
    int[] nodes;
    int[] inserts;

    public DistanceScheduling(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        int nNodes = seqVar.nNode();
        nodes = new int[nNodes];
        inserts = new int[nNodes];
        intervals = new CPIntervalVar[nNodes];
        for (int i = 0 ; i < nNodes ; i++) {
            intervals[i] = makeIntervalVar(seqVar.getSolver());
        }
    }

    /**
     * Synchronize the domains of the interval variables due to what is happening in the sequence variable
     */
    private void updateStartMember() {
        int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
        // forward pass to set the minimum start time of the intervals
        int pred = nodes[0];
        int currentDistMin = 0;
        for (int i = 1; i < nMember; i++) {
            int node = nodes[i];
            currentDistMin += dist[pred][node];
            intervals[node].setStartMin(currentDistMin);
            currentDistMin = intervals[node].startMin();

            if (seqVar.nSucc(pred) == 1) { // link between pred -> node is fixed
                if (intervals[node].startMin() == intervals[node].startMax()) {
                    intervals[pred].setEnd(intervals[node].startMin());
                }
                if (intervals[pred].endMin() == intervals[pred].endMax()) {
                    intervals[node].setStart(intervals[pred].endMin());
                }
            }
            pred = node;
        }
        // backward pass to set the maximum start time of the intervals
        int succDist = totalDist.max();
        int succ = nodes[nMember-1];
        for (int i = nMember - 2 ; i >= 0 ; i--) {
            int node = nodes[i];
            succDist -= dist[node][succ];
            intervals[node].setStartMax(succDist);
            succDist = intervals[node].startMax();
            succ = node;
        }

        for (int i = 0; i < nMember - 1 ; i++) {
            int node = nodes[i];
            succ = nodes[i+1];
            intervals[node].setEndMax(intervals[succ].startMax());
        }
    }

    /**
     * Updates the domains of the intervals
     */
    private void updateStartRequiredInsertable() {
        // TODO could be done more efficiently:
        //  maintain a list of the insertable required nodes that have been updated
        //  iterate over the sequence in order
        //  every time an insertion (i, j) is possible, this directly gives the earliest start time for
        //  updating j. Update j start time and mark it as processed
        //  same idea in reverse for updating the latest start time
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0 ; i < nInsertable ; i++) {
            int node = nodes[i];
            int nInsert = seqVar.fillInsert(node, inserts);
            int startMin = Integer.MAX_VALUE;
            int startMax = Integer.MIN_VALUE;
            int endMax = Integer.MIN_VALUE;
            for (int j = 0 ; j < nInsert ; j++) {
                int pred = inserts[j];
                int succ = seqVar.memberAfter(pred);
                // track the start
                int reachedAtMin = intervals[pred].startMin() + dist[pred][node];
                startMin = Math.min(startMin, reachedAtMin);
                int reachedAtMax = intervals[succ].startMax() - dist[node][succ];
                startMax = Math.max(startMax, reachedAtMax);
                // track the end
                int distSuccMax = intervals[succ].startMax();
                endMax = Math.max(endMax, distSuccMax);

            }
            intervals[node].setStartMin(startMin);
            intervals[node].setStartMax(startMax);
            intervals[node].setEndMax(endMax);
            // cannot update endMin in this manner: it represents the distance at which the successor is reached
            // some successor could be inserted right after this node ; the only estimation that can be done
            // is endMin = startMin + lengthMin, which is already enforced by the intervalVar itself
        }
    }

    @Override
    public void post() {
        // first task is always the start node
        intervals[seqVar.start()].setPresent();
        intervals[seqVar.start()].setStart(0);
        // last task is always the end node and has a duration of 0 (no successor)
        intervals[seqVar.end()].setPresent();
        intervals[seqVar.end()].setLength(0);
        // update the duration of each interval based on its successor in the sequence
        for (int node = 0 ; node < nNodes ; node++) {
            intervals[node].setEndMax(totalDist.max());
            if (node != seqVar.end()) { // no need to track the successor of the end node: there is none
                getSolver().post(new IntervalChanneling(node), false);
            }
            intervals[node].propagateOnChange(this);
        }
        // post a disjunctive constraint where the nodes are linked to optional tasks intervals
        getSolver().post(new NoOverlap(intervals), false);
        getSolver().post(new NoOverlapChanneling(true), false);
        getSolver().post(new NoOverlapChanneling(false), false);
        // end task == total distance
        getSolver().post(eq(CPFactory.end(intervals[seqVar.end()]), totalDist), false);
        super.post();
    }

    @Override
    public void propagate() {
        updateStartMember();
        updateStartRequiredInsertable();
        super.propagate();
    }


    @Override
    public void updateLowerBound() {

    }

    private void filterDetourUsingTimeWindows(int pred, int node, int succ) {
        int earliest = intervals[pred].startMin() + dist[pred][node];
        int latest = intervals[succ].startMax() - dist[node][succ];
        if (earliest > latest || latest < intervals[node].startMin() || earliest > intervals[node].startMax()) {
            seqVar.notBetween(pred, node, succ);
        }
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        filterDetourUsingTimeWindows(pred, node, succ);
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        filterDetourUsingTimeWindows(pred, node, succ);
    }

    /**
     * Updates the duration of an interval task based on its related node in the sequence
     */
    private class IntervalChanneling extends AbstractCPConstraint {

        int me; // id of the node related to this constraint
        CPIntervalVar interval;
        CPNodeVar node;

        public IntervalChanneling(int node) {
            super(DistanceScheduling.this.getSolver());
            this.me = node;
            interval = intervals[me];
            this.node = seqVar.getNodeVar(me);
        }

        @Override
        public void post() {
            interval.getSolver().post(eq(interval.status(), node.isRequired()), false);
            node.propagateOnInsertRemoved(this);
            seqVar.propagateOnInsert(this);
            interval.propagateOnChange(this);
            propagate();
        }

        @Override
        public void propagate() {
            if (interval.isAbsent() || node.isNode(EXCLUDED)) {
                setActive(false);
            } else {
                // inspects the current successor and updates the duration accordingly
                int nSucc = node.fillSucc(nodes);
                int minD = Integer.MAX_VALUE;
                int maxD = Integer.MIN_VALUE;
                for (int i = 0 ; i < nSucc ; i++) {
                    int succ = nodes[i];
                    int d = dist[me][succ];
                    minD = Math.min(minD, d);
                    maxD = Math.max(maxD, d);
                }
                interval.setLengthMin(minD);
                interval.setLengthMax(maxD);
            }
        }
    }

    private class NoOverlapChanneling extends AbstractCPConstraint {

        public int[] startMin, endMax;
        private boolean leftToRight;
        private int[] startMax, duration, endMin;
        int n;
        private Integer[] permEst, rankEst, permLct, permLst, permEct;

        private boolean[] inserted;
        private int[] idxToNode;
        private int[] nodeToIdx;
        private int[] members;
        private int nMembers;

        private ThetaTree thetaTree;

        @Override
        public int priority() {
            // ensures that this always runs after the detectable precedence filtering
            return Constants.PIORITY_SLOW;
        }

        public NoOverlapChanneling(boolean leftToRight) {
            super(DistanceScheduling.this.getSolver());
            this.leftToRight = leftToRight;

            int nMax = seqVar.nNode();
            startMin = new int[nMax];
            startMax = new int[nMax];
            duration = new int[nMax];
            endMin = new int[nMax];
            endMax = new int[nMax];

            permEst = new Integer[nMax];
            rankEst = new Integer[nMax];
            permLct = new Integer[nMax];
            permLst = new Integer[nMax];
            permEct = new Integer[nMax];
            inserted = new boolean[nMax];

            thetaTree = new ThetaTree(nMax);

            idxToNode = new int[nMax];
            nodeToIdx = new int[nMax];
            members = new int[nMax];

        }

        @Override
        public void post() {
            seqVar.propagateOnInsert(this);
            seqVar.propagateOnRequire(this);
            seqVar.propagateOnInsertRemoved(this);
            for (CPIntervalVar var : intervals) {
                var.propagateOnChange(this);
            }
            propagate();
        }

        @Override
        public void propagate() {
            // fill the mapping array with the nodes that are required
            n = seqVar.fillNode(idxToNode, REQUIRED);
            nMembers = seqVar.fillNode(members, MEMBER_ORDERED);
            for (int i = 0 ; i < n ; i++) {
                int node = idxToNode[i];
                nodeToIdx[node] = i;
                CPIntervalVar interval = intervals[node];
                if (leftToRight) {
                    startMin[i] = interval.startMin();
                    endMax[i] = interval.endMax();
                } else {
                    startMin[i] = - interval.endMax();
                    endMax[i] = - interval.startMin();
                }
                duration[i] = interval.lengthMin();
            }
            // enforce the precedences found by detectable precedence
            usePrecedencesFromDetectablePrecedences();
        }

        protected void update(int[] startMin, int[] duration, int[] endMax, int n) {
            this.n = n;
            for (int i = 0; i < n; i++) {
                this.startMin[i] = startMin[i];
                this.startMax[i] = endMax[i] - duration[i];
                this.duration[i] = duration[i];
                this.endMin[i] = startMin[i] + duration[i];
                this.endMax[i] = endMax[i];

                this.permEst[i] = i;
                this.permLct[i] = i;
                this.permLst[i] = i;
                this.permEct[i] = i;
            }
            Arrays.sort(permEst, 0, n, Comparator.comparingInt(i -> startMin[i]));
            for (int i = 0; i < n; i++) {
                rankEst[permEst[i]] = i;
            }
        }

        /**
         * Dirty way to enumerate the precedences found by the detectable precedences and enforce them within the sequence
         * It would be WAY better to use this directly in the NoOverlap constraint itself instead of redoing the computation
         * This is more a proof of concept :-)
         */
        private void usePrecedencesFromDetectablePrecedences() {
            update(startMin, duration, endMax, n);
            Arrays.sort(permLst, 0, n, Comparator.comparingInt(i -> endMax[i] - duration[i]));
            Arrays.sort(permEct, 0, n, Comparator.comparingInt(i -> startMin[i] + duration[i]));
            Arrays.fill(inserted, 0, n, false);
            int idxj = 0; // j = permLst[idxj];
            thetaTree.reset();
            for (int i = 0; i < n; i++) {
                int acti = permEct[i];
                while (idxj < n && endMin[acti] > startMax[permLst[idxj]]) {
                    int j = permLst[idxj];
                    inserted[j] = true;
                    thetaTree.insert(rankEst[j], startMin[j], duration[j]);
                    idxj++;
                }
                if (inserted[acti]) {
                    thetaTree.remove(rankEst[acti]);
                    // enforce the precedence onto the sequence variable
                    if (leftToRight) {
                        afterTaskFromThetaTree(acti);
                    } else {
                        beforeTaskFromThetaTree(acti);
                    }
                    thetaTree.insert(rankEst[acti], startMin[acti], duration[acti]);
                } else {
                    // enforce the precedence onto the sequence variable
                    if (leftToRight) {
                        afterTaskFromThetaTree(acti);
                    } else {
                        beforeTaskFromThetaTree(acti);
                    }
                }
            }
        }

        /**
         * Say that a task must come after the ones that are currently in the theta tree
         * @param task task to put after
         * @return
         */
        private void afterTaskFromThetaTree(int task) {
            if (seqVar.isNode(getNode(task), MEMBER)) {
                return;
            }
            // find the latest member node being inserted in the theta-tree
            int latestNode = -1;
            for (int i = nMembers - 1; i >= 0; i--) {
                int node = members[i];
                int idx = getIndex(node);
                if (inserted[idx] && idx != task) {
                    latestNode = node;
                    break;
                }
            }
            // task must come after this latest node <-> task cannot come between the start and the latest node
            if (latestNode != -1) {
                int node = getNode(task);
                seqVar.notBetween(seqVar.start(), node, latestNode);
            }
        }

        /**
         * Say that a task must come before the ones that are currently in the theta tree
         * @param task task to put before
         * @return
         */
        private void beforeTaskFromThetaTree(int task) {
            if (seqVar.isNode(getNode(task), MEMBER)) {
                return;
            }
            // find the earliest member node that is currently in the theta-tree
            int earliestNode = -1;
            for (int i = 0; i < nMembers; i++) {
                int node = members[i];
                int idx = getIndex(node);
                if (inserted[idx] && idx != task) {
                    earliestNode = node;
                    break;
                }
            }
            // task must come before this earliest node <-> task cannot come between the earliest node and the end
            if (earliestNode != -1) {
                int node = getNode(task);
                seqVar.notBetween(earliestNode, node, seqVar.end());
            }
        }

        private int getNode(int idx) {
            return idxToNode[idx];
        }

        private int getIndex(int node) {
            return nodeToIdx[node];
        }

    }
}
