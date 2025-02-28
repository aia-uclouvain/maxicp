/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.constraints.Equal;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER_ORDERED;
import static org.maxicp.util.exception.InconsistencyException.INCONSISTENCY;

public class Cumulative extends AbstractCPConstraint {

    private final CPSeqVar seqVar;
    private final int[] starts;
    private final int[] ends;
    private int nMember;
    protected Profile profile;
    private final int[] load;
    private final int[] closestPositionToCheck; // closestPosition[activity] = best position to insert the non-inserted node of a partially inserted activity
    private final List<Integer> partiallyInsertedNodes = new ArrayList<>();

    private final int[] order;
    private Map<Integer, Integer> orderIdx; // order[orderIdx[node]] = node

    // used to mark the insertions that are infeasible for non-inserted activities
    private Set<Integer> startInsertionsPos = new HashSet<>();
    private Set<Integer> endInsertionsPos = new HashSet<>();
    private List<Integer> activeStartInsertionsPos = new ArrayList<>();

    public class Profile {

        private int[] loadAt; // indexed by position (loadAt[3] = load right at the visit the 3rd node in the sequence)
        private int[] loadAfter; // indexed by position (loadAfter[3] = min load between visit of the 3rd node and the 4th node)
        private int maxLoad = 0;
        private int maxCapacity;

        public Profile(int maxCapacity) {
            this.maxCapacity = maxCapacity;
            this.loadAt = new int[seqVar.nNode()];
            this.loadAfter = new int[seqVar.nNode()];
        }

        public int loadAt(int position) {
            return loadAt[position];
        }

        public int loadAfter(int position) {
            return loadAfter[position];
        }

        /**
         * Set the load at the visit of a node
         * @param position position where the load must be set
         * @param load load set at the given position
         * @throws InconsistencyException if the given load is negative or exceeds the capacity
         */
        public void setLoadAt(int position, int load) {
            if (load < 0 || load > maxCapacity)
                throw INCONSISTENCY;
            maxLoad = Math.max(maxLoad, load);
            loadAt[position] = load;
        }

        /**
         * Set the load after the visit of a node
         * @param position position after which the load must be set
         * @param load load set after the given position
         * @throws InconsistencyException if the given load is negative or exceeds the capacity
         */
        public void setLoadAfter(int position, int load) {
            if (load < 0 || load > maxCapacity)
                throw INCONSISTENCY;
            maxLoad = Math.max(maxLoad, load);
            loadAfter[position] = load;
        }

        public void reset() {
            maxLoad = 0;
        }
    }

    /**
     * Gives a maximum capacity for a resource over a sequence.
     * A set of activity (i.e. a start and corresponding ending node for an activity) can consume the resource.
     * The resource consumption can never exceed the maximum capacity of the resource.
     *
     * @param seqVar sequence over which the constraint is applied.
     * @param starts start of the activity.
     * @param ends corresponding end of the activity. ends[i] is the end activity i, beginning at starts[i].
     * @param load consumption of each activity.
     * @param maxCapa maximum capacity for the resource.
     */
    public Cumulative(CPSeqVar seqVar, int[] starts, int[] ends, int[] load, int maxCapa) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.starts = starts;
        this.ends = ends;
        this.load = load;
        profile = new Profile(maxCapa);
        this.order = new int[seqVar.nNode()];
        this.orderIdx = new HashMap<>();
        closestPositionToCheck = new int[starts.length];
        for (int i = 0 ; i < starts.length ; ++i) {
            orderIdx.put(starts[i], i);
            orderIdx.put(ends[i], i);
        }
        // never hurts to have a bit of protection
        if (starts.length != ends.length)
            throw new IllegalArgumentException("Every activity must have a start and a matching end");
        if (starts.length > load.length)
            throw new IllegalArgumentException("Every activity must have a matching capacity");
        for (int c: load)
            if (c < 0)
                throw new IllegalArgumentException("The capacity of an activity cannot be negative");
    }

    @Override
    public void post() {
        for (int i = 0 ; i < starts.length ; ++i) {
            // the start of the activity must come before its end
            getSolver().post(new Precedence(seqVar, true, starts[i], ends[i]));
        }
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnInsertRemoved(this);
        this.propagate();
    }

    @Override
    public void propagate() {
        // build the profile
        nMember = seqVar.fillNode(order, MEMBER_ORDERED);
        buildProfile(nMember);
        // filter the insertions
        filterInsertionsForPartiallyInserted();
        filterInsertionsForNonInserted();
    }

    /**
     * Build the profile for the current sequence
     * @param nMember
     * @throws InconsistencyException if the profile exceeds the load
     * @return true if one node has become inserted because of the profile computation
     */
    private void buildProfile(int nMember) {
        partiallyInsertedNodes.clear();
        profile.reset();
        int load = 0;
        // Sets the load based on the nodes currently visited.
        // The load originating from fully inserted activities is fully taken into account.
        // For the partially inserted activities, the load is optimistic: it assumes that
        // the non-inserted node can be inserted at the best place possible.
        // A more realistic load profile regarding the partially inserted activities will be computed afterward.
        for (int pos = 0 ; pos < nMember ; pos++) {
            int node = order[pos];
            int activity = getActivity(node);
            if (isFullyInserted(activity)) {
                if (isStart(activity, node)) { // start of activity
                    load += signedLoadChange(node);
                    profile.setLoadAt(pos, load);
                } else { // end of activity
                    profile.setLoadAt(pos, load);
                    load += signedLoadChange(node);
                }
            } else if (isPartiallyInserted(activity)) {
                int positiveLoad = Math.abs(signedLoadChange(node));
                // the node visited also contribute to the profile, no matter if it's a start or an end
                profile.setLoadAt(pos, load + positiveLoad);
                partiallyInsertedNodes.add(pos);
            } else {
                profile.setLoadAt(pos, load);
            }
            profile.setLoadAfter(pos, load);
        }
        // Computes a more accurate load based on the activities partially visited,
        // taking into account the closest place where to insert the non-inserted node.
        for (int posPartiallyInserted: partiallyInsertedNodes) {
            int insertedNode = order[posPartiallyInserted];
            int activity = getActivity(insertedNode);
            int loadChange = this.load[activity];
            int nonInsertedNode = getCorrespondingNode(activity, insertedNode);
            boolean foundClosest = false;
            if (isStart(activity, insertedNode)) {
                // attempt to find the closest place where to put the end
                for (int pos = posPartiallyInserted; pos < nMember ; pos++) {
                    if (seqVar.hasInsert(order[pos], nonInsertedNode)) {
                        closestPositionToCheck[activity] = pos + 1;
                        foundClosest = true;
                        break;
                    }
                    profile.setLoadAfter(pos, profile.loadAfter(pos) + loadChange);
                    profile.setLoadAt(pos + 1, profile.loadAt(pos + 1) + loadChange);
                }
            } else {
                // attempt to find the closest place where to put the start
                for (int pos = posPartiallyInserted - 1; pos >= 0 ; pos--) {
                    if (seqVar.hasInsert(order[pos], nonInsertedNode)) {
                        closestPositionToCheck[activity] = pos;
                        foundClosest = true;
                        break;
                    }
                    profile.setLoadAfter(pos, profile.loadAfter(pos) + loadChange);
                    profile.setLoadAt(pos, profile.loadAt(pos) + loadChange);
                }
            }
            if (!foundClosest)
                throw INCONSISTENCY;
        }
    }

    /**
     * Filter the insertions for the partially inserted activities
     * Preconditions:
     * - partiallyInsertedNodes must be filled with the position of the activities partially inserted
     * - profile must be up-to-date
     * - closestPosition[activity] contains the position of the closest node that can be used to insert the
     *      remaining node of the activity
     * @return true if a node was inserted because of the operations
     */
    private void filterInsertionsForPartiallyInserted() {
        for (int posPartiallyInserted: partiallyInsertedNodes) {
            int insertedNode = order[posPartiallyInserted];
            int activity = getActivity(insertedNode);
            int loadChange = this.load[activity];
            int nonInsertedNode = getCorrespondingNode(activity, insertedNode);
            int closestPosition = this.closestPositionToCheck[activity];
            if (isStart(activity, insertedNode)) {
                // start inserted, check if end positions are valid.
                for (int pos = closestPosition; pos < nMember ; pos++) {
                    if (profile.loadAt(pos) + loadChange > profile.maxCapacity) {
                        seqVar.removeDetour(order[pos], nonInsertedNode, seqVar.end());
                    }
                }
            } else {
                // end inserted, check if start positions are valid.
                for (int pos = closestPosition; pos >= 0 ; pos--) {
                    if (profile.loadAt(pos) + loadChange > profile.maxCapacity) {
                        seqVar.removeDetour(seqVar.start(), nonInsertedNode, order[pos]);
                    }
                }
            }
        }
    }

    /**
     * Filter the insertions for non-inserted activities.
     * This performs a filtering similar to Thomas, C., Kameugne, R., & Schaus, P.
     * Insertion sequence variables for hybrid routing and scheduling problems. CPAIOR 2020.
     * @return true if an insertion occurred
     */
    private void filterInsertionsForNonInserted() {
        for (int activity = 0 ; activity < starts.length; activity++) {
            if (isNonInserted(activity)) {
                if (this.load[activity] + profile.maxLoad <= profile.maxCapacity) {
                    // can always insert the activity, no matter where. This filtering will do nothing
                    continue;
                }
                int start = starts[activity];
                int end = ends[activity];
                int capacity = profile.maxCapacity - this.load[activity];
                startInsertionsPos.clear();
                endInsertionsPos.clear();
                for (int pos = 0; pos < nMember - 1; pos++) {
                    startInsertionsPos.add(pos);
                    endInsertionsPos.add(pos);
                }
                boolean canClose = false;
                activeStartInsertionsPos.clear();
                for (int pos = 0; pos < nMember; pos++) {
                    if (seqVar.hasInsert(order[pos], start)) {
                        activeStartInsertionsPos.add(pos);
                        canClose = true;
                    }
                    // check the load between two nodes
                    int load = profile.loadAfter(pos);
                    if (load > capacity) {
                        // capacity exceeded, cannot close the active start
                        activeStartInsertionsPos.clear();
                        canClose = false;
                    }
                    if (canClose && seqVar.hasInsert(order[pos], end)) {
                        endInsertionsPos.remove(pos); // current end has at least one matching start
                        for (int startPos : activeStartInsertionsPos)
                            startInsertionsPos.remove(startPos); // all starts stored have a matching end
                        activeStartInsertionsPos.clear();
                    }
                }
                // all points not marked for insertions must be removed
                for (int pos : endInsertionsPos)
                    seqVar.removeDetour(order[pos], end, order[pos + 1]);
                for (int pos : startInsertionsPos)
                    seqVar.removeDetour(order[pos], start, order[pos + 1]);
            }
        }
    }

    private boolean isStart(int activity, int node) {
        return activity != -1 && starts[activity] == node;
    }

    /**
     * Load change occurring at a node
     * Positive for start of activities, negative for end of activities, and zero if not corresponding to an activity
     * @param node
     * @return
     */
    private int signedLoadChange(int node) {
        int activity = getActivity(node);
        if (activity == -1)
            return 0;
        if (starts[activity] == node)
            return load[activity];
        return - load[activity];
    }

    private int getCorrespondingNode(int activity, int node) {
        if (starts[activity] == node)
            return ends[activity];
        return starts[activity];
    }

    private boolean isFullyInserted(int activity) {
        return activity != -1 && seqVar.isNode(starts[activity], MEMBER) && seqVar.isNode(ends[activity], MEMBER);
    }

    private boolean isNonInserted(int activity) {
        return !seqVar.isNode(starts[activity], MEMBER) && !seqVar.isNode(ends[activity], MEMBER);
    }

    private boolean isPartiallyInserted(int activity)  {
        return activity != -1 && !isFullyInserted(activity) && !isNonInserted(activity);
    }

    /**
     * Returns the activity corresponding to a node, or -1 if it does not correspond to an activity
     */
    private int getActivity(int node) {
        return orderIdx.getOrDefault(node, -1);
    }
}
