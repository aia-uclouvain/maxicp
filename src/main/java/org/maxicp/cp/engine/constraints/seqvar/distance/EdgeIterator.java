package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public interface EdgeIterator {

    CPSeqVar seqVar();

    /**
     * Tells if a directed edge really exist between two nodes.
     *
     * @param from source of the edge
     * @param to   target of the edge
     * @return true if the directed edge (from,to) exist
     */
    boolean hasEdge(int from, int to);

    /**
     * Copies the successors of a node matching a status into an array.
     *
     * @param node   node.
     * @param dest   an array large enough {@code dest.length >= nSucc(node, status)}.
     * @param status status that must be matched by the nodes.
     * @return the number of successors matching the status and {@code dest[0,...,nSucc(node, status)-1]}
     * contains the successors in an arbitrary order.
     */
    default int fillSucc(int node, int[] dest, SeqStatus status) {
        CPSeqVar seqVar = seqVar();
        /*
        int[] newDest = new int[seqVar.nNode()];
        int nSucc = seqVar.fillSucc(node, newDest, status);
        int newNSucc = 0;
        for (int i = 0; i < nSucc; i++) {
            int succ = newDest[i];
            if (hasEdge(node, succ)) {
                dest[newNSucc++] = succ;
            }
        }
        return newNSucc;
        */
        int nSucc = seqVar.fillSucc(node, dest, status);
        if (seqVar.isNode(node, INSERTABLE)) {
            boolean insertableInSucc = status == INSERTABLE ||
                    status == NOT_EXCLUDED ||
                    status == POSSIBLE ||
                    (seqVar.nNode(INSERTABLE_REQUIRED) >= 1 && (status == INSERTABLE_REQUIRED || status == REQUIRED));
            if (insertableInSucc) {
                // post filtering of the successor of the node
                for (int i = 0; i < nSucc; ) {
                    int succ = dest[i];
                    if (!hasEdge(node, succ)) {
                        // remove this successor by swapping it with the latest dest
                        int toSwap = dest[nSucc - 1];
                        dest[i] = toSwap;
                        dest[nSucc - 1] = succ;
                        nSucc--;
                    } else {
                        i++;
                    }
                }
            }
        }
        return nSucc;
    }

    /**
     * Copies the successors of a node into an array.
     *
     * @param node node.
     * @param dest an array large enough {@code dest.length >= nSucc(node)}.
     * @return the number of successors and {@code dest[0,...,nSucc(node)-1]}
     * contains the successors in an arbitrary order.
     */
    default int fillSucc(int node, int[] dest) {
        return fillSucc(node, dest, NOT_EXCLUDED);
    }

    default int fillPred(int node, int[] dest, SeqStatus status) {
        CPSeqVar seqVar = seqVar();
        /*
        int[] newDest = new int[seqVar.nNode()];
        int nPred = seqVar.fillPred(node, newDest, status);
        int newNPred = 0;
        for (int i = 0; i < nPred; i++) {
            int pred = newDest[i];
            if (hasEdge(pred, node)) {
                dest[newNPred++] = pred;
            }
        }
        return newNPred;
        */
        int nPred = seqVar.fillPred(node, dest, status);
        if (seqVar.isNode(node, INSERTABLE)) {
            boolean insertableInPred = status == INSERTABLE ||
                    status == NOT_EXCLUDED ||
                    status == POSSIBLE ||
                    (seqVar.nNode(INSERTABLE_REQUIRED) >= 1 && (status == INSERTABLE_REQUIRED || status == REQUIRED));
            // post filtering of the successor of the node
            if (insertableInPred) {
                for (int i = 0; i < nPred; ) {
                    int pred = dest[i];
                    if (!hasEdge(pred, node)) {
                        // remove this successor by swapping it with the latest dest
                        int toSwap = dest[nPred - 1];
                        dest[i] = toSwap;
                        dest[nPred - 1] = pred;
                        nPred--;
                    } else {
                        i++;
                    }
                }
            }
        }
        return nPred;
    }

    default int fillPred(int node, int[] dest) {
        return fillPred(node, dest, NOT_EXCLUDED);
    }

    /**
     * Updates the inner data of the iterator
     */
    void update();

}
