package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public abstract class AbstractDistance extends AbstractCPConstraint {

    protected final int nNodes;
    protected final CPSeqVar seqVar;
    protected final int[] nodes;
    protected final int[] inserts;
    protected final int[][] dist;
    protected final CPIntVar totalDist;
    protected int currentDist;

    public AbstractDistance(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar.getSolver());
        this.seqVar  = seqVar;
        this.dist = dist;
        checkTriangularInequality(dist);
        this.totalDist = totalDist;
        this.nNodes = seqVar.nNode();
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
        seqVar.propagateOnInsertRemoved(this);
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnFix(this);
        totalDist.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // update the current distance
        int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
        currentDist = 0;
        for (int i = 0 ; i < nMember-1 ; ++i) {
            currentDist += dist[nodes[i]][nodes[i+1]];
        }
        if (seqVar.isFixed()) {
            totalDist.fix(currentDist);
            setActive(false);
            return;
        } else {
            totalDist.removeBelow(currentDist);
            updateLowerBound();
        }
        int maxDetour = totalDist.max() - currentDist;
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

    private void filterEdge(int pred, int node, int maxDetour) {
        if (seqVar.isNode(pred, MEMBER)) {
            int succ = seqVar.memberAfter(pred);
            int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
            if (detour > maxDetour) { // detour is too long
                seqVar.notBetween(pred, node, succ); // first obvious filtering
            } else if (seqVar.isNode(node, REQUIRED)) {
                filterDetourForRequired(pred, node, succ, detour); // additional filtering for detour of required node
            } else {
                filterDetourForOptional(pred, node, succ, detour); // additional filtering for detour of optional node
            }
        }
    }

    /**
     * Updates the lower bound of the distance based on a specific method
     */
    public abstract void updateLowerBound();

    /**
     * Eventually filters a detour when node is required
     * @param pred predecessor of the detour
     * @param node required node to examine
     * @param succ successor of the detour
     * @param detour cost of the detour
     */
    public abstract void filterDetourForRequired(int pred, int node, int succ, int detour);

    /**
     * Eventually filters a detour when node is optional
     * @param pred predecessor of the detour
     * @param node optional node to examine
     * @param succ successor of the detour
     * @param detour cost of the detour
     */
    public abstract void filterDetourForOptional(int pred, int node, int succ, int detour);

}
