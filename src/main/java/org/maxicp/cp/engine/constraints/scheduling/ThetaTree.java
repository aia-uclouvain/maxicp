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

        public void reset() {
            setECT(Integer.MIN_VALUE);
            setSUMP(0);
        }

        public int getECT() {
            return ect;
        }

        public int getSUMP() {
            return sump;
        }

        public void setECT(int ect) {
            this.ect = ect;
        }

        public void setSUMP(int sump) {
            this.sump = sump;
        }

    }

    private Node[] nodes;
    private int isize; //number of internal nodes
    private int size;

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
        // http://en.wikipedia.org/wiki/Binary_heap#Adding_to_the_heap
        this.size = size;
        isize = 1;
        //enumerate multiples of two 2, 4, 6, 8 ... until isize larger than size
        while (isize < size) {
            isize <<= 1; //shift the pattern to the left by 1 (i.e. multiplies by 2)
        }
        //number of nodes in a complete  binary tree with isize leaf nodes is (isize*2)-1
        nodes = new Node[(isize << 2) - 1];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new Node();
        }
        isize--;
    }

    /**
     * Remove all the activities from this theta-tree
     */
    public void reset() {
        for (Node n : nodes) {
            n.reset();
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
        node.setECT(ect);
        node.setSUMP(dur);
        reCompute(getFather(currPos));
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
        reCompute(getFather(currPos));
    }

    private int getECT(int pos) {
        return nodes[pos].getECT();
    }

    /**
     * The earliest completion time of the activities present in the theta-tree
     * @return the earliest completion time of the activities present in the theta-tree
     */
    public int getECT() {
        return getECT(0);
    }

    private int getSUMP(int pos) {
        return nodes[pos].getSUMP();
    }

    private int getFather(int pos) {
        //the father of node in pos is (pos-1)/2
        return (pos - 1) >> 1;
    }

    private int getLeft(int pos) {
        //the left child of pos is pos*2+1
        return (pos << 1) + 1;
    }

    private int getRight(int pos) {
        //the right child of pos is (pos+1)*2
        return (pos + 1) << 1;
    }

    private void reComputeAux(int pos) {
        int pl = getSUMP(getLeft(pos));
        int pr = getSUMP(getRight(pos));
        nodes[pos].setSUMP(pl + pr);

        int el = getECT(getLeft(pos));
        int er = getECT(getRight(pos));
        int en = Math.max(er, el + pr);
        nodes[pos].setECT(en);
    }


    private void reCompute(int pos) {
        while (pos > 0) {
            reComputeAux(pos);
            pos = getFather(pos);
        }
        // Fast recompute the top node. We do not need all info.
        nodes[0].setECT(Math.max(nodes[2].getECT(),
                nodes[1].getECT() + nodes[2].getSUMP()));
    }


}









