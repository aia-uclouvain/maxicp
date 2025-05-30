package org.maxicp.cp.engine.constraints.scheduling;


/**
 * Data Structure described in
 * Global Constraints in Scheduling, 2008 Petr Vilim, PhD thesis
 * See <a href="http://vilim.eu/petr/disertace.pdf">The thesis.</a>
 */
public class ThetaLambdaTree {

    protected static final int UNDEF = -1;

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
    public ThetaLambdaTree(int size) {
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
     * Insert an activity in the theta set
     *
     * @param activityIndex assumed to start at 0 from left to right up to size-1
     * @param ect earliest completion time
     * @param dur duration
     */
    public void insertTheta(int activityIndex, int ect, int dur) {
        //the last size nodes are the leaf nodes so the first one is isize (the number of internal nodes)
        int currPos = isize + activityIndex;
        Node node = nodes[currPos];
        node.thetaEct = ect;
        node.thetaSump = dur;
        node.thetaLambdaEct = ect;
        node.thetaLambdaSump = dur;
        node.responsibleThetaLambdaEct = UNDEF;
        node.responsibleThetaLambdaSump = UNDEF;
        reCompute(getFather(currPos));
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
        reCompute(getFather(currPos));
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
        reCompute(getFather(currPos));
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
        reCompute(getFather(currPos));
    }

    private int getThetaEct(int pos) {
        return nodes[pos].thetaEct;
    }

    private int getThetaLambdaEct(int pos) {
        return nodes[pos].thetaLambdaEct;
    }

    /**
     * The earliest completion time of the activities present in the theta-tree
     * @return the earliest completion time of the activities present in the theta-tree
     */
    public int getThetaEct() {
        return getThetaEct(0);
    }

    public int getThetaLambdaEct() {
        return getThetaLambdaEct(0);
    }


    public int getResponsibleForThetaLambdaEct() {
        return getResponsibleForThetaLambdaEct(0);
    }

    private int getResponsibleForThetaLambdaEct(int pos) {
        return nodes[pos].responsibleThetaLambdaEct;
    }

    private int getThetaSump(int pos) {
        return nodes[pos].thetaSump;
    }

    private int getThetaLambdaSump(int pos) {
        return nodes[pos].thetaLambdaSump;
    }

    public int getResponsibleForThetaLambdaSump() {
        return getResponsibleForThetaLambdaSump(0);
    }

    public int getResponsibleForThetaLambdaSump(int pos) {
        return nodes[pos].responsibleThetaLambdaSump;
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
        int pl = getThetaSump(getLeft(pos));
        int pl_ = getThetaLambdaSump(getLeft(pos));
        int pr = getThetaSump(getRight(pos));
        int pr_ = getThetaLambdaSump(getRight(pos));

        nodes[pos].thetaSump = pl + pr;

        if (pl_ + pr > pl + pr_) {
            nodes[pos].thetaLambdaSump = pl_ + pr;
            nodes[pos].responsibleThetaLambdaSump = getResponsibleForThetaLambdaSump(getLeft(pos));
        } else {
            nodes[pos].thetaLambdaSump = pl + pr_;
            nodes[pos].responsibleThetaLambdaSump = getResponsibleForThetaLambdaSump(getRight(pos));
        }

        int el = getThetaEct(getLeft(pos));
        int el_ = getThetaLambdaEct(getLeft(pos));
        int er = getThetaEct(getRight(pos));
        int er_ = getThetaLambdaEct(getRight(pos));

        // case 1
        nodes[pos].thetaEct = er_;
        nodes[pos].responsibleThetaLambdaEct = getResponsibleForThetaLambdaSump(getRight(pos));
        // case 2
        if (el_ + pr > Math.max(er_, el + pr_)) {
            nodes[pos].thetaLambdaEct = el_ + pr;
            nodes[pos].responsibleThetaLambdaEct =  getResponsibleForThetaLambdaEct(getLeft(pos));
        }
        // case 3
        if (el + pr_ > Math.max(er, el_ + pr)) {
            nodes[pos].thetaLambdaEct = el + pr_;
            nodes[pos].responsibleThetaLambdaEct = getResponsibleForThetaLambdaSump(getRight(pos));
        }
    }


    private void reCompute(int pos) {
        while (pos > 0) {
            reComputeAux(pos);
            pos = getFather(pos);
        }
    }

}