package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER_ORDERED;

public class BoundEdgeIterator implements EdgeIterator {

    private final CPSeqVar seqVar;

    private int nNodes;
    private int[] nodes;
    private int[] inserts;

    private int[] position; // position of a node. Only set for member nodes

    private int[] earliestPredecessor; // earliest predecessor of a node. Only set for insertable nodes
    private int[] latestPredecessor; // earliest predecessor of a node. Only set for insertable nodes

    public BoundEdgeIterator(CPSeqVar seqVar) {
        this.seqVar = seqVar;
        nNodes = seqVar.nNode();
        position = new int[nNodes];
        earliestPredecessor = new int[nNodes];
        latestPredecessor = new int[nNodes];
        nodes = new int[nNodes];
        inserts = new int[nNodes];
    }

    /**
     * Gives the position of the latest insertion that could be used for adding this node.
     * Should only be called for insertable nodes.
     */
    private int latestPosition(int node) {
        assert seqVar.isNode(node, INSERTABLE);
        return position[latestPredecessor[node]];
    }

    /**
     * Gives the position of the earliest insertion that could be used for adding this node.
     * Should only be called for insertable nodes.
     */
    private int earliestPosition(int node) {
        assert seqVar.isNode(node, INSERTABLE);
        return position[earliestPredecessor[node]];
    }

    @Override
    public CPSeqVar seqVar() {
        return seqVar;
    }

    @Override
    public boolean hasEdge(int from, int to) {
        if (seqVar.isNode(from, INSERTABLE) && seqVar.isNode(to, INSERTABLE)) {
            // tells if an edge from -> to could appear in a sequence from the domain
            // equivalent to checking if an interval [start1 ... end1] overlaps with an interval [start2 ... end2]
            int start1 = earliestPosition(from);
            int start2 = earliestPosition(to);
            int end1 = latestPosition(from);
            int end2 = latestPosition(to);
            return Math.max(start1, start2) <= Math.min(end1, end2);
        } else {
            return seqVar.hasEdge(from, to);
        }
    }

    @Override
    public void update() {
        int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
        int nInsertable = seqVar.fillNode(inserts, INSERTABLE);
        for (int j = 0; j < nInsertable ; j++) {
            int insert = inserts[j];
            earliestPredecessor[insert] = -1;
        }
        for (int i = 0 ; i < nMember ; i++) {
            int node = nodes[i];
            // updates the position of member nodes
            position[node] = i;
            for (int j = 0; j < nInsertable ; j++) {
                // updates the earliest and latest predecessor of insertable nodes
                int insert = inserts[j];
                if (seqVar.hasEdge(node, insert)) {
                    if (earliestPredecessor[insert] == -1) {
                        earliestPredecessor[insert] = node;
                    }
                    latestPredecessor[insert] = node;
                }
            }
        }
    }
}
