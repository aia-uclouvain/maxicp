/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Data Structure described in
 * Global Constraints in Scheduling, 2008 Petr Vilim, PhD thesis
 * See <a href="http://vilim.eu/petr/disertace.pdf">The thesis.</a>
 * @author Pierre Schaus
 */
public class ThetaTree2 {

    private static class Node {

        private int sump;
        private int ect;
        private int[] q;
        private int ectq;
        private int resp; // 0 for left, 1 for right


        Node() {
            reset();
        }

        void reset() {
            ect = Integer.MIN_VALUE;
            sump = 0;
            q = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE};
            ectq = Integer.MIN_VALUE;
            resp = 0;
        }
    }

    // the root node is at position 1 so that the parent is at i/2, the left at 2*i and the right at 2*i+1
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
    public ThetaTree2(int size) {
        int h = 1; // height
        while ((1 << h) < size) { h++; } // increase height until number of leaf nodes >= size;
        isize = (1 << h) ; // number of internal nodes is 2^h
        nodes = new ThetaTree2.Node[1 << (h+1)]; // total number of nodes is 2^(h+1)
        for (int i = 1; i < nodes.length; i++) {
            nodes[i] = new ThetaTree2.Node();
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
     * such that it is taken into account for the {@link #getEct()}
     * computation.
     *
     * @param pos the index of the leaf node (assumed to start at 0 from left to right)
     * @param est earliest start time
     * @param dur duration
     */
    public void insert(int pos, int est, int dur, int q) {
        //the last size nodes are the leaf nodes so the first one is isize (the number of internal nodes)
        int currPos = isize + pos;
        Node node = nodes[currPos];
        node.ect = est + dur;
        node.sump = dur;
        node.q[0] = q;
        node.q[1] = q;
        node.ectq = est+dur+q;
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
    public int getEct() {

        return nodes[1].ectq;
    }

    private void reCompute(int pos) {
        while (pos >= 1) {
            Node left = nodes[pos << 1]; // left child
            Node right = nodes[(pos << 1) + 1]; // right child
            nodes[pos].sump = left.sump + right.sump;
            int q = Integer.MAX_VALUE;
            if (right.ect> left.ect+right.sump) {
                nodes[pos].ect = right.ect;
                nodes[pos].q[0] = min(left.q[left.resp],min(right.q[0], right.q[1]));
                nodes[pos].q[1] = right.q[right.resp];
                nodes[pos].resp = 1;
                q = nodes[pos].q[1];
            }else {
                nodes[pos].ect = left.ect + right.sump;
                nodes[pos].q[0] = min(left.q[left.resp],min(right.q[0], right.q[1]));
                nodes[pos].q[1] = min(left.q[left.resp],min(right.q[0], right.q[1]));
                q = nodes[pos].q[0];
            }
            nodes[pos].ectq = max(max(right.ectq, left.ectq),left.ect + right.sump + q);
            pos = pos >> 1; // father
        }
    }
}








