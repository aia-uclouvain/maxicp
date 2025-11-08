package org.maxicp.cp.engine.constraints.seqvar.distance;

public interface EdgeIterator {

    /**
     * Tells if a directed edge really exist between two nodes.
     *
     * @param from source of the edge
     * @param to target of the edge
     * @return true if the directed edge (from,to) exist
     */
    boolean hasEdge(int from, int to);

    /**
     * Copies the successors of a node into an array.
     *
     * @param node   node.
     * @param dest   an array large enough {@code dest.length >= nSucc(node)}.
     * @return the number of successors and {@code dest[0,...,nSucc(node)-1]}
     * contains the successors in an arbitrary order.
     */
    int fillSucc(int node, int[] dest);

    /**
     * Updates the inner data of the iterator
     */
    void update();

}
