package org.maxicp.cp.engine.constraints.scheduling;


/**
 * Data Structure described in
 * Global Constraints in Scheduling, 2008 Petr Vilim, PhD thesis
 * See <a href="http://vilim.eu/petr/disertace.pdf">The thesis.</a>
 */
public class ThetaLambdaTree {

    public static final int UNDEF = -1;

    private static class Node {

        int thetaSump;
        int thetaEct;
        int thetaLambdaSump;
        int thetaLambdaEct;
        int responsibleThetaLambdaSump = UNDEF;
        int responsibleThetaLambdaEct = UNDEF;

        Node() {
            reset();
        }

        void reset() {
            thetaEct = Integer.MIN_VALUE;
            thetaSump = 0;
            thetaLambdaEct = Integer.MIN_VALUE;
            responsibleThetaLambdaEct = UNDEF;
            thetaLambdaSump = 0;
            responsibleThetaLambdaSump = UNDEF;
        }
    }

    // the root node is at position 1 so that the parent is at i/2, the left at 2*i and the right at 2*i+1
    private Node[] nodes;
    private int isize; // number of internal nodes

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
    public ThetaLambdaTree(int size) {
        int h = 1; // height of the tree
        while ((1 << h) < size) { // while the number of leaf nodes is less than size, increase height
            h++;
        }
        isize = (1 << h) ; // number of internal nodes is 2^h
        nodes = new ThetaLambdaTree.Node[1 << (h+1)]; // total number of nodes is 2^(h+1)
        for (int i = 1; i < nodes.length; i++) {
            nodes[i] = new ThetaLambdaTree.Node();
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
     * Insert an activity in the theta set
     *
     * @param activityIndex assumed to start at 0 from left to right up to size-1
     * @param est earliest completion time
     * @param dur duration
     */
    public void insertTheta(int activityIndex, int est, int dur) {
        //the last size nodes are the leaf nodes so the first one is isize (the number of internal nodes)
        int currPos = isize + activityIndex;
        Node node = nodes[currPos];
        node.thetaEct = est + dur;
        node.thetaSump = dur;
        node.thetaLambdaEct = est + dur;
        node.thetaLambdaSump = dur;
        node.responsibleThetaLambdaEct = UNDEF;
        node.responsibleThetaLambdaSump = UNDEF;
        reCompute(currPos >> 1); // re-compute from the parent node
    }


    /**
     * Insert an activity in the lambda set
     *
     * @param activityIndex assumed to start at 0 from left to right up to size-1
     * @param ect earliest completion time
     * @param dur duration
     */
    public void insertLambda(int activityIndex, int ect, int dur) {
        //the last size nodes are the leaf nodes so the first one is isize (the number of internal nodes)
        int currPos = isize + activityIndex;
        Node node = nodes[currPos];
        node.thetaEct = ect;
        node.thetaSump = dur;
        node.thetaLambdaEct = ect;
        node.thetaLambdaSump = dur;
        node.responsibleThetaLambdaEct = activityIndex;
        node.responsibleThetaLambdaSump = activityIndex;
        reCompute(currPos >> 1); // re-compute from the parent node
    }

    /**
     * Move an activity from the theta set to the lambda set.
     * Of course this activity must be present in the theta set (not verified).
     * @param activityIndex assumed to start at 0 up to size-1
     */
    public void moveFromThetaToLambda(int activityIndex) {
        int currPos = isize + activityIndex;
        Node node = nodes[currPos];
        node.responsibleThetaLambdaSump = activityIndex;
        node.responsibleThetaLambdaEct = activityIndex;
        node.thetaEct = Integer.MIN_VALUE;
        node.thetaSump = 0;
        reCompute(currPos >> 1); // re-compute from the parent node
    }

    /**
     * Remove activity from the theta set or lambda set
     *
     * @param activityIndex assumed to start at 0 up to size-1
     */
    public void remove(int activityIndex) {
        int currPos = isize + activityIndex;
        Node node = nodes[currPos];
        node.reset();
        reCompute(currPos >> 1); // re-compute from the parent node
    }

    public int getThetaEct() {
        return nodes[1].thetaEct;
    }

    public int getThetaLambdaEct() {
        return nodes[1].thetaLambdaEct;
    }


    public int getResponsibleForThetaLambdaEct() {
        return nodes[1].responsibleThetaLambdaEct;
    }


    private int getResponsibleForThetaLambdaSump() {
        return nodes[1].responsibleThetaLambdaSump;
    }

    private void reCompute(int pos) {
        while (pos >= 1) {
            Node node = nodes[pos];
            Node left = nodes[pos << 1]; // left child
            Node right = nodes[(pos << 1) + 1]; // right child

            // ----- theta tree update -----
            nodes[pos].thetaSump = left.thetaSump + right.thetaSump;
            nodes[pos].thetaEct = Math.max(right.thetaEct, left.thetaEct + right.thetaSump);

            // ----- theta-lambda update -----

            // sump update
            node.thetaSump = left.thetaSump + right.thetaSump;
            if (left.thetaLambdaSump + right.thetaSump > left.thetaSump + right.thetaLambdaSump) {
                nodes[pos].thetaLambdaSump = left.thetaSump + right.thetaLambdaSump;
                nodes[pos].responsibleThetaLambdaSump = left.responsibleThetaLambdaSump;
            } else {
                nodes[pos].thetaLambdaSump = left.thetaLambdaSump + right.thetaSump;
                nodes[pos].responsibleThetaLambdaSump = right.responsibleThetaLambdaSump;
            }

            // ect update
            // case 1
            node.thetaLambdaEct = right.thetaLambdaEct;
            node.responsibleThetaLambdaEct = right.responsibleThetaLambdaEct;
            // case 2
            if (left.thetaEct + right.thetaLambdaSump > node.thetaLambdaEct) {
                node.thetaLambdaEct = left.thetaEct + right.thetaLambdaSump;
                node.responsibleThetaLambdaEct =  right.responsibleThetaLambdaSump;
            }
            // case 3
            if (left.thetaLambdaEct + right.thetaSump > node.thetaLambdaEct) {
                node.thetaLambdaEct = left.thetaLambdaEct + right.thetaSump;
                node.responsibleThetaLambdaEct = left.responsibleThetaLambdaEct;
            }

            pos = pos >> 1; // parent
        }
    }

}