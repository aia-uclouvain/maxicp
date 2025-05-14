/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.Constants;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;
import java.util.stream.IntStream;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.util.exception.InconsistencyException.INCONSISTENCY;

public class Cumulative extends AbstractCPConstraint {

    // only activities in 0..nValidActivities are considered (the remaining ones are excluded from the sequence)
    private final int[] activities;
    private final StateInt nValidActivities;

    private final CPSeqVar seqVar;
    private final int[] starts;
    private final int[] ends;
    private final int[] load;

    // used with fill operations over the sequence
    private int nMember;
    private final int[] order;
    protected Profile profile;
    // closestNodeToCheck[activity] = best predecessor to insert the non-inserted node of a partially inserted activity
    private final int[] closestNodeToCheck;
    private final List<Integer> partiallyInsertedActivities = new ArrayList<>();

    // used to mark the insertions that are infeasible for non-inserted activities
    private final Set<Integer> startInsertionsPos = new HashSet<>();
    private final Set<Integer> endInsertionsPos = new HashSet<>();
    private final List<Integer> activeStartInsertionsPos = new ArrayList<>();

    public class Profile {

        private final int[] loadBefore; // indexed by position (loadAt[3] = load right before the visit of node 3 in the sequence)
        private final int[] loadAt; // indexed by position (loadAt[3] = load right at the visit of node 3 in the sequence)
        private final int[] loadAfter; // indexed by position (loadAfter[3] = min load between visit of node 3 and its successor)
        private int maxLoad = 0;
        private final int maxCapacity;

        public Profile(int maxCapacity) {
            this.maxCapacity = maxCapacity;
            this.loadAt = new int[seqVar.nNode()];
            this.loadAfter = new int[seqVar.nNode()];
            this.loadBefore = new int[seqVar.nNode()];
        }

        public int loadBefore(int node) {
            return loadBefore[node];
        }

        public int loadAt(int node) {
            return loadAt[node];
        }

        public int loadAfter(int node) {
            return loadAfter[node];
        }

        /**
         * Set the load at the visit of a node
         *
         * @param node node whose load must be set
         * @param load load set at the given position
         * @throws InconsistencyException if the given load is negative or exceeds the capacity
         */
        public void incrementLoadAtBy(int node, int load) {
            load += loadAt(node);
            if (load < 0 || load > maxCapacity)
                throw INCONSISTENCY;
            maxLoad = Math.max(maxLoad, load);
            loadAt[node] = load;
        }

        /**
         * Set the load after the visit of a node
         *
         * @param node node after which the load must be set
         * @param load load set after the given position
         * @throws InconsistencyException if the given load is negative or exceeds the capacity
         */
        public void incrementLoadAfterBy(int node, int load) {
            load += loadAfter(node);
            if (load < 0 || load > maxCapacity)
                throw INCONSISTENCY;
            maxLoad = Math.max(maxLoad, load);
            loadAfter[node] = load;
        }

        /**
         * Set the load after the visit of a node
         *
         * @param node node after which the load must be set
         * @param load load set after the given position
         * @throws InconsistencyException if the given load is negative or exceeds the capacity
         */
        public void incrementLoadBeforeBy(int node, int load) {
            load += loadBefore(node);
            if (load < 0 || load > maxCapacity)
                throw INCONSISTENCY;
            maxLoad = Math.max(maxLoad, load);
            loadBefore[node] = load;
        }

        public void reset() {
            maxLoad = 0;
            for (int i = 0; i < nMember; i++) {
                int node = order[i];
                loadBefore[node] = 0;
                loadAt[node] = 0;
                loadAfter[node] = 0;
            }
        }
    }

    /**
     * Gives a maximum capacity for a resource over a sequence.
     * A set of activity (i.e. a start and corresponding ending node for an activity) can consume the resource.
     * The resource consumption can never exceed the maximum capacity of the resource.
     *
     * @param seqVar   sequence over which the constraint is applied.
     * @param starts   start of the activities.
     * @param ends     corresponding end of each activity. ends[i] is the end activity of i, beginning at starts[i].
     * @param load     consumption of each activity.
     * @param capacity maximum capacity for the resource.
     */
    public Cumulative(CPSeqVar seqVar, int[] starts, int[] ends, int[] load, int capacity) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.starts = starts;
        this.ends = ends;
        this.load = load;
        profile = new Profile(capacity);
        this.order = new int[seqVar.nNode()];
        closestNodeToCheck = new int[starts.length];
        // never hurts to have a bit of protection
        if (starts.length != ends.length)
            throw new IllegalArgumentException("Every activity must have a start and a matching end");
        if (starts.length > load.length)
            throw new IllegalArgumentException("Every activity must have a matching capacity");
        for (int activity = 0; activity < starts.length; activity++) {
            if (load[activity] < 0)
                throw new IllegalArgumentException("The capacity of an activity cannot be negative");
            if (starts[activity] == ends[activity])
                throw new IllegalArgumentException("The start and the of an activity cannot be the same node");
        }
        activities = IntStream.range(0, starts.length).toArray();
        nValidActivities = getSolver().getStateManager().makeStateInt(activities.length);
    }

    @Override
    public void post() {
        for (int i = 0; i < starts.length; ++i) {
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
        // note: in the loop, the recomputation of the profile after the forced insertion of a node may be done incrementally
        boolean changed = true;
        while (changed) {
            nMember = seqVar.fillNode(order, MEMBER_ORDERED);
            buildProfile();
            // filter the insertions
            changed = filterInsertionsForPartiallyInserted();
            changed = changed || filterInsertionsForNonInserted();
        }
    }

    /**
     * Build the profile for the current sequence
     *
     * @throws InconsistencyException if the profile exceeds the load
     */
    private void buildProfile() {
        partiallyInsertedActivities.clear();
        profile.reset();
        int nValid = nValidActivities.value();
        for (int i = 0; i < nValid; i++) {
            int activity = activities[i];
            int start = starts[activity];
            int end = ends[activity];
            int load = this.load[activity];
            if (isFullyInserted(activity)) {
                addLoadFullyInserted(start, end, load);
            } else if (isPartiallyInserted(activity)) {
                partiallyInsertedActivities.add(activity);
                if (seqVar.isNode(start, MEMBER)) {
                    addLoadOnlyStartInserted(activity, start, end, load);
                } else if (seqVar.isNode(end, MEMBER)) {
                    addLoadOnlyEndInserted(activity, start, end, load);
                }
            } else if (isFullyExcluded(activity)) { // remove from the sparse set
                nValid--;
                activities[i] = activities[nValid];
                activities[nValid] = activity;
                i--; // next iteration needs to consider this index
            }
        }
        nValidActivities.setValue(nValid);
    }

    private void addLoadFullyInserted(int start, int end, int load) {
        int node = start;
        while (node != end) {
            profile.incrementLoadAtBy(node, load);
            profile.incrementLoadAfterBy(node, load);
            node = seqVar.memberAfter(node);
            if (node == seqVar.start())
                throw INCONSISTENCY;
            profile.incrementLoadBeforeBy(node, load);
        }
    }

    private void addLoadOnlyStartInserted(int activity, int start, int end, int load) {
        int node = start;
        profile.incrementLoadAtBy(node, load);
        while (!seqVar.hasInsert(node, end)) {
            profile.incrementLoadAfterBy(node, load);
            node = seqVar.memberAfter(node);
            if (node == seqVar.start())
                throw INCONSISTENCY;
            profile.incrementLoadBeforeBy(node, load);
            profile.incrementLoadAtBy(node, load);
        }
        closestNodeToCheck[activity] = seqVar.memberAfter(node);
    }

    private void addLoadOnlyEndInserted(int activity, int start, int end, int load) {
        profile.incrementLoadBeforeBy(end, load);
        int node = seqVar.memberBefore(end);
        while (!seqVar.hasInsert(node, start)) {
            profile.incrementLoadAfterBy(node, load);
            profile.incrementLoadAtBy(node, load);
            profile.incrementLoadBeforeBy(node, load);
            node = seqVar.memberBefore(node);
            if (node == seqVar.end())
                throw INCONSISTENCY;
        }
        closestNodeToCheck[activity] = node;
    }

    /**
     * Filter the insertions for the partially inserted activities
     * Preconditions:
     * - partiallyInsertedNodes must be filled with the position of the activities partially inserted
     * - profile must be up-to-date
     * - closestPosition[activity] contains the position of the closest node that can be used to insert the
     * remaining node of the activity
     *
     * @return true if a node was inserted because of the operations
     */
    private boolean filterInsertionsForPartiallyInserted() {
        for (int activity : partiallyInsertedActivities) {
            int start = starts[activity];
            int end = ends[activity];
            int loadChange = this.load[activity];
            int closestNode = this.closestNodeToCheck[activity];
            if (seqVar.isNode(start, MEMBER)) {
                // start inserted, check if end positions are valid.
                for (int node = closestNode; node != seqVar.start(); node = seqVar.memberAfter(node)) {
                    if (Math.max(profile.loadAt(node), profile.loadBefore(node)) + loadChange > profile.maxCapacity) {
                        seqVar.notBetween(node, end, seqVar.end());
                        if (seqVar.isNode(end, MEMBER))
                            return true;
                    }
                }
            } else {
                assert seqVar.isNode(end, MEMBER);
                // end inserted, check if start positions are valid.
                for (int node = closestNode; node != seqVar.end(); node = seqVar.memberBefore(node)) {
                    if (Math.max(profile.loadAt(node), profile.loadBefore(node)) + loadChange > profile.maxCapacity) {
                        seqVar.notBetween(seqVar.start(), start, node);
                        if (seqVar.isNode(start, MEMBER))
                            return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Filter the insertions for non-inserted activities.
     * This performs a filtering similar to Thomas, C., Kameugne, R., & Schaus, P.
     * Insertion sequence variables for hybrid routing and scheduling problems. CPAIOR 2020.
     *
     * @return true if an insertion occurred
     */
    private boolean filterInsertionsForNonInserted() {
        int nValid = nValidActivities.value();
        for (int i = 0; i < nValid; i++) {
            int activity = activities[i];
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
                    int load = profile.loadAfter(order[pos]);
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
                    seqVar.notBetween(order[pos], end, order[pos + 1]);
                for (int pos : startInsertionsPos)
                    seqVar.notBetween(order[pos], start, order[pos + 1]);
                if (seqVar.nNode(MEMBER) != nMember) {
                    // an insertion happened and profile may be updated
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFullyExcluded(int activity) {
        return seqVar.isNode(starts[activity], EXCLUDED) && seqVar.isNode(ends[activity], EXCLUDED);
    }

    private boolean isFullyInserted(int activity) {
        return seqVar.isNode(starts[activity], MEMBER) && seqVar.isNode(ends[activity], MEMBER);
    }

    private boolean isNonInserted(int activity) {
        return !seqVar.isNode(starts[activity], MEMBER) && !seqVar.isNode(ends[activity], MEMBER);
    }

    private boolean isPartiallyInserted(int activity) {
        return !isFullyInserted(activity) && !isNonInserted(activity);
    }

    @Override
    public int priority() {
        return Constants.PIORITY_MEDIUM;
    }
}
