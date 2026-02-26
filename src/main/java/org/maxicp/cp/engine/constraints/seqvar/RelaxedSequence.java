package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.modeling.algebra.sequence.SeqStatus;


import java.util.Set;

/**
 * Imposes that the sequence defined
 * by the provided orderedSequence where the relaxed nodes
 * have been removed is a subsequence of the provided sequence variable.
 * This constraint is useful for Large Neighborhood Search to restrict
 * some subsequence on the next restart.
 */
public class RelaxedSequence extends AbstractCPConstraint {

    private final CPSeqVar sequence;
    private final int[] orderedSequence;
    private final int n;
    private final Set<Integer> relaxed;


    public RelaxedSequence(CPSeqVar sequence, int [] orderedSequence, Set<Integer> relaxed) {
        super(sequence.getSolver());
        this.sequence = sequence;
        this.orderedSequence = orderedSequence;
        this.n = orderedSequence.length;
        this.relaxed = relaxed;
        assert(sequence.start() == orderedSequence[0]);
        assert(sequence.end() == orderedSequence[n-1]);
        if (sequence.start() != orderedSequence[0]) {
            throw new IllegalArgumentException(String.format("node %d is not the first of the orderedSequence", sequence.start()));
        }
        if (sequence.end() != orderedSequence[n-1]) {
            throw new IllegalArgumentException(String.format("node %d is not the last of the orderedSequence", sequence.end()));
        }
        // check all the nodes are part of the domain of the sequence
        int dom = sequence.nNode();
        boolean[] present = new boolean[n];
        for (int v: orderedSequence) {
            if (present[v]) {
                throw new IllegalArgumentException(String.format("node %d cannot be present more than once in the orderedSequence", v));
            }
            present[v] = true;
        }
    }

    @Override
    public void post() {
        int current = orderedSequence[0];
        int n = orderedSequence.length;
        for (int i = 1; i < n - 1; i++) {
            int nextNode = orderedSequence[i];
            if (!relaxed.contains(nextNode)) {
                if (!sequence.isNode(nextNode, SeqStatus.MEMBER)) {
                    // the node may have been inserted already, if not, insert it
                    sequence.insert(current, nextNode);
                }
                current = nextNode;
            }
        }
    }

}
