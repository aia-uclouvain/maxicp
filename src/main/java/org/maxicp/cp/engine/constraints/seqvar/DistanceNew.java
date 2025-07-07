package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class DistanceNew extends AbstractCPConstraint {

    private final CPSeqVar seqVar;
    private final int[] nodes;
    private final int[] inserts;
    private final int[][] dist;
    private final CPIntVar totalDist;

    public DistanceNew(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar.getSolver());
        this.seqVar  = seqVar;
        this.dist = dist;
        checkTriangularInequality(dist);
        this.totalDist = totalDist;
        this.nodes = new int[seqVar.nNode()];
        this.inserts = new int[seqVar.nNode()];
    }

    private static void checkTriangularInequality(int[][] dist) {
        for (int i = 0 ; i < dist.length ; i++) {
            for (int j = 0 ; j < dist[i].length ; j++) {
                int smallestDist = dist[i][j];
                for (int k = 0 ; k < dist.length ; k++) {
                    int distWithDetour = dist[i][k] + dist[k][j];
                    if (distWithDetour < smallestDist) {
                        System.err.println("[WARNING]: triangular inequality not respected with distance matrix");
                        System.err.printf("[WARNING]: dist[%d][%d] + dist[%d][%d] < dist[%d][%d] (%d + %d < %d)%n", i, k, k, j, i, j,
                                dist[i][k], dist[k][j], dist[i][j]);
                        System.err.println("[WARNING]: this might remove some solutions");
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void post() {
        propagate();
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnFix(this);
        totalDist.propagateOnBoundChange(this);
    }

    @Override
    public void propagate() {
        // update the current distance
        int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
        int d = 0;
        for (int i = 0 ; i < nMember-1 ; ++i) {
            d += dist[nodes[i]][nodes[i+1]];
        }
        if (seqVar.isFixed()) {
            totalDist.fix(d);
            setActive(false);
            return;
        } else {
            // current distance is at least the current travel
            totalDist.removeBelow(d);
            // take into account required nodes for the remaining distance
            updateLowerBound();
            updateUpperBound();
        }
        int maxDetour = totalDist.max() - d;
        // filter invalid insertions
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0 ; i < nInsertable ; i++) {
            int node = nodes[i];
            int nPreds = seqVar.fillInsert(node, inserts);
            for (int p = 0 ; p < nPreds ; p++) {
                int pred = inserts[p];
                filterEdge(pred, node, maxDetour);
            }
        }
    }

    /**
     * Updates the lower bound on the distance, based on the current sequence variable
     */
    private void updateLowerBound() {
        // TODO 1
    }

    /**
     * Updates the upper bound on the distance, based on the current sequence variable
     */
    private void updateUpperBound() {
        // TODO 2
    }

    private void filterEdge(int pred, int node, int maxDetour) {
        if (seqVar.isNode(pred, MEMBER)) {
            int succ = seqVar.memberAfter(pred);
            int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
            if (detour > maxDetour) { // detour is too long
                seqVar.notBetween(pred, node, succ);
            }
        }
    }

}