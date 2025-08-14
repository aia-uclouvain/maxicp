package org.maxicp.search;

import org.maxicp.util.algo.Tree;

public class SearchTree implements DFSListener {

    Tree tree = new Tree(-1);

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

    public void toTikZ() {
        Tree.PositionedNode root = tree.root().design();
        StringBuilder tikz = new StringBuilder();
    }
}
