package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.selectMin;

/**
 * Uses a minRestrictedDetour for computing the bound, and a slow (but loyal) filtering for the insertions,
 * by forcing a node as first within the bound estimation and looking it this leads to an overestimation of the bound
 */
public class DistanceRestrictedDetourShavingIncremental extends AbstractCPConstraint {

    private BoundEstimator estimator;

    private CPSeqVar seqVar;
    protected final CPIntVar totalDist;
    private int[][] dist;
    private int[] nodes;
    private int[] inserts;

    private int[] newInserts = new int[2];
    private int[] newPreds = new int[2];

    private SimpleSparseSet nodesToFilter;

    public DistanceRestrictedDetourShavingIncremental(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.dist = dist;
        this.totalDist = totalDist;
        estimator = new BoundEstimator(seqVar, dist);
        nodes = new int[seqVar.nNode()];
        inserts = new int[seqVar.nNode()];
        nodesToFilter = new SimpleSparseSet(seqVar.nNode());
    }

    @Override
    public void post() {
        seqVar.propagateOnInsertRemoved(this);
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnFix(this);
        totalDist.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        int cost = 0;
        int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
        for (int i = 0; i < nMember-1; i++) {
            cost += dist[nodes[i]][nodes[i+1]];
        }
        if (seqVar.isFixed()) {
            totalDist.fix(cost);
            setActive(false);
        } else {
            int maxDetour = totalDist.max() - cost;
            int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
            for (int i = 0 ; i < nInsertable ; i++) {
                int node = nodes[i];
                int nPreds = seqVar.fillInsert(node, inserts);
                for (int p = 0 ; p < nPreds ; p++) {
                    int pred = inserts[p];
                    int succ = seqVar.memberAfter(pred);
                    int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
                    if (detour > maxDetour) { // detour is too long
                        seqVar.notBetween(pred, node, succ); // first obvious filtering
                    }
                }
            }

            estimator.init();
            // Computes the bound.
            // Use an early stop if it can be detected that we will not improve the lower bound that way.
            int lowerBound = estimator.compute(-1, totalDist.min());
            totalDist.removeBelow(lowerBound);
            // all nodes on which the filtering must be applied
            nodesToFilter.removeAll();
            nInsertable = seqVar.fillNode(nodes, INSERTABLE_REQUIRED);
            for (int i = 0; i < nInsertable; i++) {
                int node = nodes[i];
                nodesToFilter.add(node);
            }
            //filterInsertionsRequiredNodes();
            filterInsertionsRequiredNodesBrute();
            //filterInsertionsPossibleNodes();


            // this simple filtering should not be able to remove anything here in theory:
            // it is dominated by the previous one
            /*
            int cost = 0;
            int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
            for (int i = 0; i < nMember-1; i++) {
                cost += dist[nodes[i]][nodes[i+1]];
            }
            nInsertable = seqVar.fillNode(nodes, INSERTABLE_REQUIRED);
            for (int i = 0; i < nInsertable; i++) {
                int node = nodes[i];
                int nInsert = seqVar.fillInsert(node, inserts);
                for (int j = 0 ; j < nInsert ; j++) {
                    int pred = inserts[j];
                    int succ = seqVar.memberAfter(pred);
                    int detour = detourCost(pred, node, succ);
                    if (cost + detour > totalDist.max()) {
                        seqVar.notBetween(pred, node, succ);
                    }
                }
            }

             */
        }
    }

    private void filterInsertionsPossibleNodes() {
        int nInsertable = seqVar.fillNode(nodes, POSSIBLE);
        for (int i = 0; i < nInsertable; i++) {
            int node = nodes[i];
            estimator.init();
            //estimator.lazyInit();
            estimator.compute(node, totalDist.min());
            filterInsertionsOnNodeWithExactCost(node);
        }
    }

    private void filterInsertionsOnNodeWithExactCost(int node) {
        int cost = estimator.cost();
        int minDetour = estimator.costMinInsertOnMember(node);
        int maxDetour = estimator.costMaxInsertOnMember(node);
        if (cost > totalDist.max()) {
            seqVar.exclude(node);
        } else if (cost - minDetour + maxDetour > totalDist.max()) {
            // has a chance to filter some insertions, look for them
            int nInsert = seqVar.fillInsert(node, inserts);
            for (int j = 0; j < nInsert; j++) {
                int pred = inserts[j];
                int succ = seqVar.memberAfter(pred);
                int detour = detourCost(pred, node, succ);
                if (cost - minDetour + detour > totalDist.max()) {
                    seqVar.notBetween(pred, node, succ);
                }
            }
        }
    }

    private void filterInsertionsRequiredNodesBrute() {
        boolean lazyInit = true;
        while (!nodesToFilter.isEmpty()) {
            int node = nodesToFilter.atIndex(0);
            if (lazyInit)
                estimator.lazyInit();
            estimator.compute(node, Integer.MAX_VALUE);
            int nMemberBefore = seqVar.nNode(MEMBER);
            filterInsertionsOnNodeWithExactCost(node);
            int nMemberAfter = seqVar.nNode(MEMBER);

            if (nMemberAfter > nMemberBefore) { // node was inserted
                int pred = seqVar.memberBefore(node);
                int succ = seqVar.memberAfter(node);
                estimator.lazyInit();
                estimator.notifyInsertionPartialSequence(pred, node, succ);
                lazyInit = false;
            } else {
                lazyInit = true;
            }

            nodesToFilter.remove(node);
        }
    }

    private void filterInsertionsRequiredNodes() {
        while (!nodesToFilter.isEmpty()) {
            // applies the filtering on nodes on which the cost was exact.

            int nodeInserted = -1;
            int nNodesConsidered = 0;
            for (Iterator<Integer> it = estimator.iterNodesWithExactCost(); it.hasNext(); ) {
                int node = it.next();
                if (!seqVar.isNode(node, REQUIRED))
                    continue;
                int nMemberBefore = seqVar.nNode(MEMBER);
                nNodesConsidered++;
                filterInsertionsOnNodeWithExactCost(node);
                int nMemberAfter = seqVar.nNode(MEMBER);
                if (nMemberBefore != nMemberAfter) {
                    // node was inserted. Can reconsider again all required nodes for the computation
                    nodeInserted = node;
                    // TODO could force the size of the sparse-set here to gain slight runtime improvements?
                    nodesToFilter.removeAll();
                    int nInsertable = seqVar.fillNode(nodes, INSERTABLE_REQUIRED);
                    for (int i = 0; i < nInsertable; i++) {
                        int v = nodes[i];
                        nodesToFilter.add(v);
                    }
                    break;
                } else {
                    nodesToFilter.remove(node);
                }
            }
            /*estimator.lazyInit();
            if (nodeInserted != -1) {
                int pred = seqVar.memberBefore(nodeInserted);
                int succ = seqVar.memberAfter(nodeInserted);
                estimator.notifyInsertionPartialSequence(pred, nodeInserted, succ);
                estimator.compute(-1, totalDist.min());
            } else {

             */
            estimator.init();
                if (!nodesToFilter.isEmpty()) {
                    // consumed all exact nodes but some nodes still need to be considered ... force one of them
                    // to be included
                    int node = nodesToFilter.atIndex(0);
                    estimator.compute(node, totalDist.min());
                }
            //}
        }
    }

    /**
     * A sparse set that allows for value addition and removal.
     * Does not rely on StateInt for the size - there's nothing to cope with backtracking :-)
     */
    private class SimpleSparseSet {

        int[] values;
        int[] indices;
        int size;

        public SimpleSparseSet(int n) {
            values = new int[n];
            indices = new int[n];
            for (int i = 0; i < n; i++){
                values[i] = i;
                indices[i] = i;
            }
            size = n;
        }

        public void removeAll() {
            size = 0;
        }

        public void addAll() {
            size = indices.length;
        }

        public int size() {
            return size;
        }

        /**
         * Adds a value into the sparse-set
         * @param val value to add into the sparse-set
         */
        public void add(int val) {
            if (contains(val))
                return;
            exchangePosition(val, values[size]);
            size++;
        }

        public int atIndex(int i) {
            return values[i];
        }

        public boolean contains(int val) {
            if (val < 0 || val >= values.length)
                return false;
            else
                return indices[val] < size;
        }

        private void exchangePosition(int val1, int val2) {
            int v1 = val1;
            int v2 = val2;
            int i1 = indices[v1];
            int i2 = indices[v2];
            values[i1] = v2;
            values[i2] = v1;
            indices[v1] = i2;
            indices[v2] = i1;
        }

        /**
         * Removes a value from the sparse-set
         * @param val value to remove from the sparse-set
         */
        public void remove(int val) {
            if (!contains(val))
                return;
            exchangePosition(val, values[size-1]);
            size--;
        }

        /**
         * Selects the included value having the minimum cost according to a given selector
         * @param selector mapping from a value to a given cost
         * @return value with the minimum cost according to the selector, or -1 if no value could be found
         */
        public int selectMin(Function<Integer, Integer> selector) {
            if (size == 0)
                return -1;
            int bestValue = values[0];
            int bestCost = selector.apply(bestValue);
            for (int i = 1; i < size; i++) {
                int val = values[i];
                int cost = selector.apply(val);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestValue = val;
                }
            }
            return bestValue;
        }

        public void forAllIncluded(Consumer<Integer> consumer) {
            forAllBetween(0, size, consumer);
        }

        public boolean isEmpty() {
            return size == 0;
        }

        /**
         * Iterates on the values located between size1 and size2 within the set
         * @param size1
         * @param size2
         * @param consumer
         */
        public void forAllBetween(int size1, int size2, Consumer<Integer> consumer) {
            size2 = Math.min(size2, values.length);
            if (size1 >= size2 || size1 < 0) {
                return;
            }
            for (int i = size1; i < size2; i++) {
                int val = values[i];
                consumer.accept(val);
            }
        }

    }

    private class BoundEstimator {

        private CPSeqVar seqVar;
        private int[][] dist;

        // the nodes considered for insertion within the algorithm
        private SimpleSparseSet nodesToInsert;
        // the nodes for which the cost estimate is exact
        private SimpleSparseSet nodesWithExactCost;

        private int[] member;
        private int[] insertable;
        private int[] inserts;
        private int nMember;
        private int costPartialPath;
        private int cost;

        private int[] minInsertOnMembers; // min insertion cost to add an insertable node within the partial sequence
        private int[] predMinInsertOnMembers;
        private int[] maxInsertOnMembers; // max insertion cost to add an insertable node within the partial sequence
        private int[] predMaxInsertOnMembers;
        // cost of the minimum insertions between an insertable node and non-excluded nodes
        // Not all insertions are considered at every step in the computation ; this is dynamically updated
        private int[] minInsertOnRestrictedNodes;

        // optimistic cost that could be reached if all remaining nodes could be inserted using their minKey value
        // this can only decrement per iteration of the algorithm
        private int optimisticLowerBound;
        private int optimisticLowerBoundInit;

        public BoundEstimator(CPSeqVar seqVar, int[][] dist) {
            this.seqVar = seqVar;
            this.dist = dist;
            int n = seqVar.nNode();
            nodesToInsert = new SimpleSparseSet(n);
            nodesWithExactCost = new SimpleSparseSet(n);
            member = new int[n];
            insertable = new int[n];
            inserts = new int[n];
            minInsertOnMembers = new int[n];
            predMinInsertOnMembers = new int[n];
            maxInsertOnMembers = new int[n];
            predMaxInsertOnMembers = new int[n];
            minInsertOnRestrictedNodes = new int[n];
        }

        /**
         * Initializes the full datastructures. Should be called at the beginning of every filtering.
         * For recomputing the cost several times during the same {@link CPConstraint#propagate()}, use
         * {@link BoundEstimator#lazyInit} instead
         */
        private void init() {
            // fill in the member nodes
            nMember = seqVar.fillNode(member, MEMBER_ORDERED);
            costPartialPath = 0;
            for (int i = 0 ; i < nMember - 1 ; i++) {
                costPartialPath += dist[member[i]][member[i+1]];
            }
            nodesToInsert.removeAll();
            optimisticLowerBoundInit = 0;
            // compute the initial set of insertions for all insertable nodes
            int nToInsert = seqVar.fillNode(insertable, INSERTABLE);
            for (int i = 0 ; i < nToInsert ; i++) {
                int node = insertable[i];
                seekInsertionCostInPartialSeq(node);
                // stores the cost of the best insertion within the sequence for later usage
                minInsertOnRestrictedNodes[node] = minInsertOnMembers[node];
                // if the node is required, stores its impact on the cost
                if (seqVar.isNode(node, REQUIRED)) {
                    optimisticLowerBoundInit += minInsertOnMembers[node];
                    nodesToInsert.add(node);
                }
            }
            optimisticLowerBound = optimisticLowerBoundInit;
        }

        /**
         * Lazy initialization of the structure, exploiting some previously set data.
         * Can be called safely as long as no insertion occurred since the last {@link BoundEstimator#init()} call.
         * If one or more insertion happened since this last call, either call back the init, or use a
         * {@link BoundEstimator#notifyInsertionPartialSequence(int, int, int)} after (better if only 1 insertion happened)
         */
        private void lazyInit() {
            nodesToInsert.removeAll();
            optimisticLowerBoundInit = 0;
            // compute the initial set of insertions for all insertable nodes
            int nToInsert = seqVar.fillNode(insertable, INSERTABLE);
            for (int i = 0 ; i < nToInsert ; i++) {
                int node = insertable[i];
                // if the best predecessor from before still exists, keep it. Otherwise, seek the new min and max
                if (!seqVar.hasInsert(predMinInsertOnMembers[node], node) ||
                        !seqVar.hasInsert(predMaxInsertOnMembers[node], node)) {
                    seekInsertionCostInPartialSeq(node); // seek the new min within the sequence
                }
                minInsertOnRestrictedNodes[node] = minInsertOnMembers[node];
                if (seqVar.isNode(node, REQUIRED)) {
                    optimisticLowerBoundInit += minInsertOnMembers[node];
                    nodesToInsert.add(node);
                }
            }
            optimisticLowerBound = optimisticLowerBoundInit;
        }

        /**
         * Notifies that an insertion pred -> node -> succ occurred within the partial sequence and re-updates the
         * data-structure.
         * Must be preceded by a {@link BoundEstimator#lazyInit} call.
         */
        public void notifyInsertionPartialSequence(int pred, int node, int succ) {
            nMember = seqVar.fillNode(member, MEMBER_ORDERED);
            // add cost to partial path
            int insertionDetour = detourCost(pred, node, succ);
            costPartialPath += insertionDetour;
            // remove the contribution of the inserted node on the estimate
            optimisticLowerBoundInit -= minInsertOnMembers[node];
            // check if the new detour is now the cheapest one for some of the insertable node.
            // if so, updates their cost
            // the insertable nodes are all already located within insertable after a lazyInit call
            int nToInsert = seqVar.nNode(INSERTABLE);
            for (int i = 0 ; i < nToInsert ; i++) {
                int insertable = this.insertable[i];
                if (seqVar.hasInsert(node, insertable)) {
                    // has this new insertion replaced the previous best predecessor?
                    boolean replaceMinPred = pred == predMinInsertOnMembers[insertable];
                    boolean replaceMaxPred = pred == predMaxInsertOnMembers[insertable];
                    // should still be available after a lazyInit call
                    assert seqVar.hasInsert(predMinInsertOnMembers[insertable], insertable);
                    assert seqVar.hasInsert(predMaxInsertOnMembers[insertable], insertable);
                    int previousMinDetour = minInsertOnMembers[insertable];
                    boolean improvedMin = false; // true if found a new better min or one with same cost
                    boolean improvedMax = false; // true if found a new better max or one with same cost
                    // two insertions to evaluate: pred -> insertable -> node & node -> insertable -> succ
                    int insertion1 = detourCost(pred, insertable, node);
                    int insertion2 = detourCost(node, insertable, succ);
                    newInserts[0] = insertion1;
                    newInserts[1] = insertion2;
                    newPreds[0] = pred;
                    newPreds[1] = node;
                    for (int j = 0 ; j < 2 ; j++) { // inspet the new detours
                        if (newInserts[j] <= minInsertOnMembers[insertable]) {
                            minInsertOnMembers[insertable] = newInserts[j];
                            predMinInsertOnMembers[insertable] = newPreds[j];
                            improvedMin = true;
                        }
                        if (newInserts[j] >= maxInsertOnMembers[insertable]) {
                            maxInsertOnMembers[insertable] = newInserts[j];
                            predMaxInsertOnMembers[insertable] = newPreds[j];
                            improvedMax = true;
                        }
                    }
                    /*
                    if (insertion1 <= minInsertOnMembers[insertable]) {
                        minInsertOnMembers[insertable] = insertion1;
                        predMinInsertOnMembers[insertable] = pred;
                        improvedMin = true;
                    }
                    if (insertion1 >= maxInsertOnMembers[insertable]) {
                        maxInsertOnMembers[insertable] = insertion1;
                        predMaxInsertOnMembers[insertable] = pred;
                        improvedMax = true;
                    }
                    if (insertion2 <= minInsertOnMembers[insertable]) {
                        minInsertOnMembers[insertable] = insertion2;
                        predMinInsertOnMembers[insertable] = node;
                        improvedMin = true;
                    }
                    if (insertion2 >= maxInsertOnMembers[insertable]) {
                        maxInsertOnMembers[insertable] = insertion2;
                        predMaxInsertOnMembers[insertable] = node;
                        improvedMax = true;
                    }

                     */
                    if ((!improvedMin && replaceMinPred) || (!improvedMax && replaceMaxPred)) {
                        // insertion has replaced the previous best one without improving it.
                        // need to seek the new min and max
                        seekInsertionCostInPartialSeq(insertable);
                    }
                    minInsertOnRestrictedNodes[insertable] = minInsertOnMembers[insertable];
                    if (seqVar.isNode(insertable, REQUIRED)) {
                        optimisticLowerBoundInit = optimisticLowerBound - previousMinDetour + minInsertOnMembers[insertable];
                    }
                }
            };
            optimisticLowerBound = optimisticLowerBoundInit;
        }

        /**
         * Fills in {@link BoundEstimator#minInsertOnMembers} the cost of the smallest insertion within the sequence
         * @param node node for which the cost of insertion must be filled
         */
        private void seekInsertionCostInPartialSeq(int node) {
            int nInsert = seqVar.fillInsert(node, inserts);
            minInsertOnMembers[node] = Integer.MAX_VALUE;
            predMinInsertOnMembers[node] = Integer.MAX_VALUE;
            maxInsertOnMembers[node] = Integer.MIN_VALUE;
            predMaxInsertOnMembers[node] = Integer.MAX_VALUE;
            for (int j = 0 ; j < nInsert ; j++) {
                int pred = inserts[j];
                int succ = seqVar.memberAfter(pred);
                int detour = detourCost(pred, node, succ);
                if (detour < minInsertOnMembers[node]) {
                    minInsertOnMembers[node] = detour;
                    predMinInsertOnMembers[node] = pred;
                }
                if (detour > maxInsertOnMembers[node]) {
                    maxInsertOnMembers[node] = detour;
                    predMaxInsertOnMembers[node] = pred;
                }
            }
        }

        /**
         * Used to temporarily include a possible node within the bound computation
         * @param node node to add within the bound computation
         */
        private void includeNodeInRelaxation(int node) {
            nodesToInsert.remove(node); // do not consider the node for further iterations
            int val = minInsertOnRestrictedNodes[node];
            if (val == minInsertOnMembers[node]) {
                nodesWithExactCost.add(node);
            }
            cost += val; // increment the cost
            optimisticLowerBound -= minInsertOnRestrictedNodes[node]; // decrement cost on remaining iteration
        }

        /**
         * Computes a lower bound on the total distance, by inserting one by one a node within the sequence.
         * Each inserted node will have some contribution to the bound computation.
         * The first node being inserted always has an exact contribution {@link BoundEstimator#isCostExact}.
         *
         * @param nodeFirstIteration node to consider for the first iteration. Putting -1 uses the default node selection
         *                           of the algorithm
         * @param cutoff if the estimator detects that it cannot get a value higher than this threshold, stops earlier
         */
        public int compute(int nodeFirstIteration, int cutoff) {
            cost = costPartialPath;
            int nRemainingInit = nodesToInsert.size();
            //if (cost + optimisticLowerBound < cutoff) {
                // no filtering will be achieved with this -> early stop
            //    return cost + optimisticLowerBound;
            //}

            while (!nodesToInsert.isEmpty()) {
                int newlyInserted;
                if (nodeFirstIteration != -1) {
                    // force the selection of the node
                    newlyInserted = nodeFirstIteration;
                    assert nodesToInsert.contains(newlyInserted);
                    nodeFirstIteration = -1;
                } else {
                    // select the node with the largest insertion cost into the estimator
                    newlyInserted = nodesToInsert.selectMin(v -> -minInsertOnRestrictedNodes[v]);
                }
                int nRemaining = nodesToInsert.size();
                includeNodeInRelaxation(newlyInserted);
                // considers that the node has been added at all its insertions positions.
                // this generates new insertions candidates for the remaining nodes to consider
                nodesToInsert.forAllIncluded(toInsert -> {
                    // considers all edges into the partial sequence
                    for (int i = 0 ; i < nMember - 1 ; i++) {
                        int pred = member[i];
                        int succ = member[i + 1];
                        // checks if newlyInsertedNode has been added on edge pred -> succ
                        if (seqVar.hasInsert(pred, toInsert) && seqVar.hasInsert(pred, newlyInserted)) {
                            // node `toInsert` and `newlyInserted` have one common insertion, and could be put
                            // one after the other in a sequence from the domain
                            updateCostFor(pred, toInsert, newlyInserted); // try to insert on pred -> newlyInserted
                            updateCostFor(newlyInserted, toInsert, succ); // try to insert on newlyInserted -> succ
                            nodesToInsert.forAllBetween(nRemaining, nRemainingInit, previouslyInserted -> {
                                if (seqVar.hasInsert(pred, previouslyInserted)) {
                                    // TODO could remove this loop:
                                    //  1. precompute in advance how many insertions are in common per insertable nodes, store it in a int[][] matrix
                                    //  2. if >= 1 insertion in common, then the nodes can be inserted one after the other
                                    //  3. during filtering, insertion removal == eventually decrement counters:
                                    //    seqvar.notBetween(i, j, k)
                                    //    forall insertable nodes l : if seqvar.hasInsert(i, l): decrement counter (the insertion was common before)
                                    //
                                    // a chain pred -> previouslyInserted -> has been formed
                                    // new insertion: newlyInserted -> toInsert -> previouslyInserted
                                    updateCostFor(newlyInserted, toInsert, previouslyInserted);
                                    // new insertion: previouslyInserted -> toInsert -> newlyInserted
                                    updateCostFor(previouslyInserted, toInsert, newlyInserted);
                                }
                            });
                        }
                    }
                });
                // the optimistic lower bound may have been decremented. Checks if there is still a chance to improve
                // on the bound
                //if (cost + optimisticLowerBound < cutoff) {
                    // no filtering will be achieved with this -> early stop
                //    return cost + optimisticLowerBound;
                //}
            }
            return cost;
        }

        /**
         * Should only be called from the main loop of the algorithm itself, to declare a new **future** insertion,
         * meaning that one of the endpoints (pred or succ) is not a member node yet
         */
        private void updateCostFor(int pred, int node, int succ) {
            int detour = detourCost(pred, node, succ);
            if (detour < minInsertOnRestrictedNodes[node]) {
                // updates the cost
                int previousCost = minInsertOnRestrictedNodes[node];
                // optimistic cost for connecting all remaining nodes is decremented
                optimisticLowerBound = optimisticLowerBoundInit - previousCost + detour;
                minInsertOnRestrictedNodes[node] = detour;
            }
        }

        /**
         * The current cost of the bound estimator.
         * This cost is exact if {@link BoundEstimator#isCostExact} on the node
         */
        public int cost() {
            return cost;
        }

        public int costMinInsertOnMember(int node) {
            return minInsertOnMembers[node];
        }

        public int costMaxInsertOnMember(int node) {
            return maxInsertOnMembers[node];
        }

        /**
         * Tells if the cost related to adding a node is exact or not
         */
        public boolean isCostExact(int node) {
            return nodesWithExactCost.contains(node);
        }

        private void forAllNodesWithExactCost(Consumer<Integer> consumer) {
            nodesWithExactCost.forAllIncluded(consumer);
        }

        private Iterator<Integer> iterNodesWithExactCost() {
            return new Iterator<>() {
                int i = 0;

                @Override
                public boolean hasNext() {
                    return i < nodesWithExactCost.size();
                }

                @Override
                public Integer next() {
                    int v = nodesWithExactCost.atIndex(i);
                    i++;
                    return v;
                }
            };
        }

    }

    private int detourCost(int pred, int node, int succ) {
        return dist[pred][node] + dist[node][succ] - dist[pred][succ];
    }

}
