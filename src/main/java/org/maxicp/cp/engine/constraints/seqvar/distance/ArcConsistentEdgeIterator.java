package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.Arrays;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;

public class ArcConsistentEdgeIterator implements EdgeIterator {

    private CPSeqVar seqVar;

    private boolean[][] hasEdge; // tells if there is an undirected between nodes. Only set for insertable nodes
    private int[][] insertions;
    private boolean[] updated;
    private int[] nInsertions;
    private int[] nodes;

    public ArcConsistentEdgeIterator(CPSeqVar seqVar) {
        this.seqVar = seqVar;
        int nNodes = seqVar.nNode();
        updated = new boolean[nNodes];
        nodes = new int[nNodes];
        insertions = new int[nNodes][nNodes];
        hasEdge = new boolean[nNodes][nNodes];
        nInsertions = new int[nNodes];
    }

    @Override
    public CPSeqVar seqVar() {
        return seqVar;
    }

    @Override
    public boolean hasEdge(int from, int to) {
        if (seqVar.isNode(from, INSERTABLE) && seqVar.isNode(to, INSERTABLE)) {
            return hasEdge[from][to];
        } else {
            return seqVar.hasEdge(from, to);
        }
    }

    @Override
    public void update() {
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        // resets the arrays
        for (int i = 0 ; i < hasEdge.length ; i++) {
            Arrays.fill(hasEdge[i], false);
        }
        Arrays.fill(updated, false);
        int maxNInsert = seqVar.nNode(MEMBER) - 1;
        // check if there is at least one common insertion for each pair of insertable nodes
        for (int i = 0; i < nInsertable; i++) {
            int from = nodes[i];
            int nInsertFrom = seqVar.nInsert(from);
            //int nInsertFrom = seqVar.fillInsert(from, insertions[from]);
            for (int j = i+1; j < nInsertable; j++) {
                int to = nodes[j];
                int nInsertTo = seqVar.nInsert(to);
                //int nInsertTo = seqVar.fillInsert(to, insertions[to]);
                if (nInsertFrom + nInsertTo > maxNInsert) {
                    // there must be at least one insertion in common
                    hasEdge[from][to] = true;
                    hasEdge[to][from] = true;
                } else {
                    // check if there is a common insertion between the nodes
                    fillInsertIfNecessary(from);
                    fillInsertIfNecessary(to);
                    for (int i1 = 0; i1 < nInsertFrom && !hasEdge[from][to]; i1++) {
                        int insert1 = insertions[from][i1];
                        for (int i2 = 0; i2 < nInsertTo && !hasEdge[from][to]; i2++) {
                            int insert2 = insertions[to][i2];
                            if (insert1 == insert2) {
                                hasEdge[from][to] = true;
                                hasEdge[to][from] = true;
                            }
                        }
                    }
                }
            }
        }
    }

    private void fillInsertIfNecessary(int node) {
        if (!updated[node]) {
            nInsertions[node] = seqVar.fillInsert(node, insertions[node]);
            updated[node] = true;
        }
    }
}
