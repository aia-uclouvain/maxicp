/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.constraints.Equal;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.ArrayList;
import java.util.List;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.util.exception.InconsistencyException.INCONSISTENCY;

public class Precedence extends AbstractCPConstraint {

    private final CPSeqVar seqVar;
    private final int[] order; // ordering that must appear
    private final boolean appearTogether; // set to true if the nodes must all appear together

    private int[] nodes;
    private int nCommon;
    private int nMember;
    private List<Integer> nodesToFilter = new ArrayList<>();

    // TODO make a specialized constraint for precedence between 2 nodes, which might be slightly less heavy than this one

    /**
     * Ensures that a list of nodes appears in a given relative order within the sequence, if they appear
     * Example of a solution:
     * order = [1, 3, 5, 9]
     * seqVar = 1 -> 2 -> 3 -> 4 -> 5 -> 8
     * Note that nodes can be put in between and some nodes in `order` may be excluded
     * Example of a non solution (since 1 cannot appear before 5):
     * order = [1, 3, 5, 9]
     * seqVar = 5 -> 2 -> 4 -> 1 -> 8
     *
     * @param seqVar sequence variable
     * @param order  nodes that must respect the given ordering if they are included in the sequence
     */
    public Precedence(CPSeqVar seqVar, int... order) {
        this(seqVar, false, order);
    }

    /**
     * Ensures that a list of nodes appears in a given relative order within the sequence, if they appear.
     * Example of a solution:
     * order = [1, 3, 5, 9].
     * seqVar = 1 -> 2 -> 3 -> 4 -> 5 -> 8.
     * Note that nodes can be put in between and some nodes in `order` may be excluded, provided that {@code appearTogether} is set to false.
     * Example of a non solution (since 1 cannot appear before 5):
     * order = [1, 3, 5, 9].
     * seqVar = 5 -> 2 -> 4 -> 1 -> 8.
     *
     * @param seqVar         sequence variable
     * @param appearTogether if set to true, all nodes in order must appear together.
     *                       Requiring one means requiring all of them, and excluding one means excluding all of them
     * @param order          nodes that must respect the given ordering if they are included in the sequence
     */
    public Precedence(CPSeqVar seqVar, boolean appearTogether, int... order) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.order = order;
        this.nodes = new int[seqVar.nNode()];
        this.appearTogether = appearTogether;
    }

    @Override
    public void post() {
        if (appearTogether) {
            for (int i = 0; i < order.length - 1; i++) {
                getSolver().post(new Equal((CPIntVar) seqVar.isNodeRequired(order[i]), (CPIntVar) seqVar.isNodeRequired(order[i + 1])));
            }
        }
        seqVar.propagateOnInsert(this);
        for (int node : order)
            seqVar.getNodeVar(node).propagateOnInsertRemoved(this);
        propagate();
    }

    @Override
    public void propagate() {
        boolean oneNodeInserted = false;
        int nNodesNotExcluded = 0; // only nodes useful for the filtering
        for (int node : order) {
            nNodesNotExcluded += seqVar.isNode(node, NOT_EXCLUDED) ? 1 : 0;
            oneNodeInserted = oneNodeInserted || seqVar.isNode(node, MEMBER);
        }
        if (nNodesNotExcluded <= 1) {
            // trying to do a precedence with 0 or 1 node, the constraint can be deactivated
            setActive(false);
        } else {
            if (oneNodeInserted) {
                nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
                checkOrder();
                if (!isActive()) // the check might set the constraint as inactive
                    return;
                filterBasedOnOrderInserted();
            }
            filterBasedOnOrderInsertable();
        }
    }

    private void checkOrder() {
        int idx = 0;
        nCommon = 0;
        for (int node : order) {
            if (seqVar.isNode(node, MEMBER)) {
                nCommon += 1;
                // find the location of the node 'visit'
                for (; idx < nMember && nodes[idx] != node; ++idx) {

                }
                if (idx == nMember) {
                    // all member nodes have been evaluated without encountering the node 'visit'
                    // this means that the order is not respected here
                    throw INCONSISTENCY;
                }
                if (nCommon == order.length) {
                    setActive(false);
                    return;
                }
            }
        }
    }

    private void filterBasedOnOrderInserted() {
        nodesToFilter.clear();
        int pred = seqVar.start();
        for (int i = 0; i <= order.length; i++) {
            // end is always added to be sure to go into the else if instruction below at the last iteration
            int node = i < order.length ? order[i] : seqVar.end();
            if (seqVar.isNode(node, INSERTABLE)) {
                nodesToFilter.add(node); // node that is not in common, and whose insertions will be filtered
            } else if (seqVar.isNode(node, MEMBER)) {
                for (int nodeToFilter : nodesToFilter) {
                    // nodeToFilter must be placed between pred and node
                    seqVar.notBetween(seqVar.start(), nodeToFilter, pred);
                    seqVar.notBetween(node, nodeToFilter, seqVar.end());
                }
                pred = node;
                nodesToFilter.clear();
            }
        }
    }

    private void filterBasedOnOrderInsertable() {
        for (int i = 0; i < order.length; i++) {
            int node = order[i];
            if (seqVar.isNode(node, INSERTABLE_REQUIRED) || (appearTogether && seqVar.isNode(node, INSERTABLE))) {
                // forward filtering: filter positions for subsequent nodes in order:
                // they can only come after the first insertion of this node
                if (i < order.length - 1) {
                    int current = seqVar.start();
                    while (!seqVar.hasInsert(current, node))
                        current = seqVar.memberAfter(current);
                    for (int j = i + 1; j < order.length; j++) {
                        int subSequentNode = order[j];
                        seqVar.notBetween(seqVar.start(), subSequentNode, current);
                    }
                }
                // backward filtering: filter positions for precedent nodes:
                // they can only come before the last insertion of this node
                if (i > 0) {
                    int current = seqVar.end();
                    while (!seqVar.hasEdge(node, current))
                        current = seqVar.memberBefore(current);
                    for (int j = i - 1; j >= 0; j--) {
                        int precedentNode = order[j];
                        seqVar.notBetween(current, precedentNode, seqVar.end());
                    }
                }
            }
        }
    }

}
