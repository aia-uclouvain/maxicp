/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;

/**
 * Data Structure described in
 * Global Constraints in Scheduling, 2008 Petr Vilim, PhD thesis
 * See <a href="http://vilim.eu/petr/disertace.pdf">The thesis.</a>
 */
public class ThetaTree {

    private static class Node {

        private int sump;
        private int ect;

        Node() {
            reset();
        }

        void reset() {
            ect = Integer.MIN_VALUE;
            sump = 0;
        }
    }

    private Node[] nodes;
    private int isize; //number of internal nodes

    /**
     * Creates a theta-tree able to store
     * the specified number of activities, each identified
     * as a number between 0 and size-1.
     * The activities inserted in a theta tree are assumed
     * to be of increasing earliest start time.
     * That is activity identified as i must possibly start earlier than
     * activity i+1.
     *
     * @param size the number of activities that can possibly be inserted in the tree
     */
    public ThetaTree(int size) {
        int h = 1; // height of the tree
        while ((1 << h) < size) { // while the number of leaf nodes is less than size, increase height
            h++;
        }
        isize = (1 << h) ; // number of internal nodes is 2^h
        nodes = new ThetaTree.Node[1 << (h+1)]; // total number of nodes is 2^(h+1)
        for (int i = 1; i < nodes.length; i++) {
            nodes[i] = new ThetaTree.Node();
        }
    }

    /**
     * Remove all the activities from this theta-tree
     */
    public void reset() {
        for (int i = 1; i < nodes.length; i++) {
            nodes[i].reset();
        }
    }

    /**
     * Insert activity in leaf nodes at given position
     * such that it is taken into account for the {@link #getECT()}
     * computation.
     *
     * @param pos the index of the leaf node (assumed to start at 0 from left to right)
     * @param ect earliest completion time
     * @param dur duration
     */
    public void insert(int pos, int ect, int dur) {
        //the last size nodes are the leaf nodes so the first one is isize (the number of internal nodes)
        int currPos = isize + pos;
        Node node = nodes[currPos];
        node.ect = ect;
        node.sump = dur;
        reCompute(currPos >> 1); // re-compute from the parent node
    }

    /**
     * Remove activity at given position that it has no impact
     * on the earliest completion time computation
     *
     * @param pos the index of the leaf nodes, assumed to start at 0 from left to right
     */
    public void remove(int pos) {
        int currPos = isize + pos;
        Node node = nodes[currPos];
        node.reset();
        reCompute(currPos >> 1); // re-compute from the parent node
    }

    /**
     * The earliest completion time of the activities present in the theta-tree
     * @return the earliest completion time of the activities present in the theta-tree
     */
    public int getECT() {
        return nodes[1].ect;
    }

    private void reCompute(int pos) {
        while (pos >= 1) {
            Node left = nodes[pos << 1]; // left child
            Node right = nodes[(pos << 1) + 1]; // right child
            nodes[pos].sump = left.sump + right.sump;;
            nodes[pos].ect = Math.max(right.ect, left.ect + right.sump);
            pos = pos >> 1; // father
        }
    }


}









