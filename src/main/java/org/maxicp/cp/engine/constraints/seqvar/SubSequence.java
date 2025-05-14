package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.core.*;

import java.util.ArrayList;
import java.util.List;

import static org.maxicp.cp.CPFactory.implies;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.util.exception.InconsistencyException.INCONSISTENCY;

/**
 * Links two {@link CPSeqVar}, ensuring a subsequence appears within a super sequence
 */
public class SubSequence extends AbstractCPConstraint {

    private final CPSeqVar mainSequence;
    private final CPSeqVar subSequence;

    private final int[] nodes;
    private final int[] common;
    private int nCommon;
    private final List<Integer> nodesToFilter = new ArrayList<>();

    /**
     * Ensures that the ordering from a subsequence appears within a super sequence, including all of its nodes
     * The super sequence can have other nodes belonging to its ordering as well
     * Example:
     * mainSequence: 0 -> 1 -> 2 -> 3 -> 4
     * subSequence:  0 -> 2 -> 4
     *
     * @param mainSequence super sequence
     * @param subSequence  sub sequence, whose ordering appears within the main sequence. The ordering appearing in the
     *                     super sequence must not be consecutive
     */
    public SubSequence(CPSeqVar mainSequence, CPSeqVar subSequence) {
        super(mainSequence.getSolver());
        this.mainSequence = mainSequence;
        this.subSequence = subSequence;
        int nNodes = Math.max(mainSequence.nNode(), subSequence.nNode());
        nodes = new int[mainSequence.nNode()];
        common = new int[nNodes];
    }

    @Override
    public void post() {
        // nodes excluded from main cannot appear in sub
        int nExcluded = mainSequence.fillNode(nodes, EXCLUDED);
        for (int i = 0; i < nExcluded; i++) {
            subSequence.exclude(nodes[i]);
        }
        // presence in sub-sequence implies presence in main sequence
        int nPossible = subSequence.fillNode(nodes, NOT_EXCLUDED);
        for (int i = 0; i < nPossible; i++) {
            int node = nodes[i];
            getSolver().post(implies(subSequence.isNodeRequired(node), mainSequence.isNodeRequired(node)));
        }
        propagate();
        if (isActive()) {
            mainSequence.propagateOnRequire(this);
            mainSequence.propagateOnInsert(this);
            subSequence.propagateOnRequire(this);
            subSequence.propagateOnInsert(this);
        }
    }

    private void checkCurrentOrdering() {
        int nMemberSub = subSequence.fillNode(nodes, MEMBER_ORDERED);
        nCommon = 0;
        for (int i = 0; i < nMemberSub; i++) {
            int nodeSub = nodes[i];
            if (mainSequence.isNode(nodeSub, MEMBER)) {
                common[nCommon++] = nodeSub;
            }
        }
        // common[0..nCommon] contains the common members from both sequence, based on the ordering from sub
        // check that this ordering is preserved in main
        int idx = 0;
        int nMemberMain = mainSequence.fillNode(nodes, MEMBER_ORDERED);
        for (int i = 0; i < nCommon; i++) {
            int node = common[i];
            while (idx != nMemberMain - 1 && node != nodes[idx]) {
                idx++;
            }
            if (idx == nMemberMain - 1 && node != nodes[nMemberMain-1])
                throw INCONSISTENCY;
        }
    }

    private void enforcePrecedence(CPSeqVar seqVar, int nNodes, int[] nodes) {
        nodesToFilter.clear();
        int pred = seqVar.start();
        for (int i = 0; i <= nNodes; i++) {
            // end is always added to be sure to go into the else if instruction below at the last iteration
            int node = i < nNodes ? nodes[i] : seqVar.end();
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

    @Override
    public void propagate() {
        // check che current ordering between the sequences to see they are coherent
        // and fill the arrays common, mainNodes and subNodes
        checkCurrentOrdering();
        // enforces precedence from main into subsequence
        int nMemberMain = mainSequence.fillNode(nodes, MEMBER_ORDERED);
        enforcePrecedence(subSequence, nMemberMain, nodes);
        // enforces precedence from subsequence into main
        int nMemberSub = subSequence.fillNode(nodes, MEMBER_ORDERED);
        enforcePrecedence(mainSequence, nMemberSub, nodes);
    }

}
