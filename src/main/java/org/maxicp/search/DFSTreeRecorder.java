package org.maxicp.search;

import org.maxicp.util.algo.Tree;

/**
 * A DFSListener that records the search tree in a Tree structure.
 * It can be used to visualize the search process by exoprting the tree to TikZ format.
 */
public class DFSTreeRecorder implements DFSListener {

    public final int root = -1;

    Tree tree = new Tree(root);

    @Override
    public void branch(int nodeId, int parentId) {
        tree.createNode(nodeId, parentId, Tree.NodeType.INNER);
    }

    @Override
    public void solution(int nodeId, int parentId) {
        tree.createNode(nodeId, parentId, Tree.NodeType.SOLUTION);
    }

    @Override
    public void fail(int nodeId, int parentId) {
        tree.createNode(nodeId, parentId, Tree.NodeType.FAIL);
    }

    @Override
    public String toString() {
        return "SearchTree{" +
                "tree=" + tree +
                '}';
    }


    public void toTikz() {
       toTikz(1.0, 1.0, 2.0, 4.0);
    }

    public void toTikz(double xScale, double yStep, double labelOffsetPt, double nodeDiameter) {
        Tree.PositionedNode root = tree.root().design();
        String tikz = root.toTikz(xScale, yStep, labelOffsetPt, nodeDiameter);
        System.out.println(tikz);
    }
}
