package org.maxicp.util.algo;


// https://www.microsoft.com/en-us/research/wp-content/uploads/1996/01/drawingtrees.pdf

import java.util.*;

public class Tree {

    HashMap<Integer, Node> nodeMap;
    int rootId;

    public enum NodeType {
        INNER,
        SKIP,
        FAIL,
        SOLUTION
    }

    public Tree(int rootId) {
        nodeMap = new HashMap<>();
        this.rootId = rootId;
        System.out.println("put root " + rootId);
        nodeMap.put(rootId, new Node("root"));
    }


    public void createNode(int id, int pId, NodeType type) {
        Node n = nodeMap.get(pId).addChild(id, type, "child", "branch");
        nodeMap.put(id, n);
    }


    public Node root() {
        return nodeMap.get(rootId);
    }


    static record Pair<L, R>(L left, R right) { }



    public static class Node {

        public int nodeId;
        public int nodePid;
        public NodeType type;
        public String nodeLabel;
        public List<Node> children;
        public List<String> edgeLabels;

        @Override
        public String toString() {
            return "Node [" +
                    " label=" + nodeLabel +
                    ", children=" + children +
                    ", edgeLabels=" + edgeLabels +
                    ", type=" + type +
                    ']';
        }

        public Node() {
            this.type = NodeType.INNER;
            this.children = new LinkedList<>();
            this.edgeLabels = new LinkedList<>();
        }

        public Node(String nodeLabel) {
            this.nodeLabel = nodeLabel;
            this.type = NodeType.INNER;
            this.children = new LinkedList<>();
            this.edgeLabels = new LinkedList<>();
        }


        public Node(int nodeId, String nodeLabel, NodeType type, List<Node> children, List edgeLabels) {
            this.nodeId = nodeId;
            this.nodeLabel = nodeLabel;
            this.type = type;
            this.children = children;
            this.edgeLabels = edgeLabels;
        }

        public Node addChild(int nodeId, NodeType type, String nodeLabel, String branchLabel) {
            Node child = new Node(nodeId, nodeLabel, type, new LinkedList<>(), new LinkedList());
            children.add(child);
            edgeLabels.add(branchLabel);
            return child;
        }

        public PositionedNode design() {
            Pair<PositionedNode, Extent> res = design_();
            return res.left();
        }

        private Pair<PositionedNode, Extent> design_() {
            List<PositionedNode> subtrees = new LinkedList<>();
            List<Extent> subtreeExtents = new LinkedList<>();
            for (Node child : children) {
                Pair<PositionedNode, Extent> res = child.design_();
                subtrees.add(res.left());
                subtreeExtents.add(res.right());
            }
            List<Double> positions = Extent.fitList(subtreeExtents);

            List<PositionedNode> subtreesMoved = new LinkedList<>();
            List<Extent> extentsMoved = new LinkedList<>();

            Iterator<PositionedNode> childIte = subtrees.iterator();
            Iterator<Extent> extentIte = subtreeExtents.iterator();
            Iterator<Double> posIte = positions.iterator();

            while (childIte.hasNext() && posIte.hasNext() && extentIte.hasNext()) {

                double pos = posIte.next();
                subtreesMoved.add(childIte.next().moveTree(pos));
                extentsMoved.add(extentIte.next().move(pos));
            }

            Extent resExtent = Extent.merge(extentsMoved);
            resExtent.addFirst(0, 0);
            PositionedNode resTree = new PositionedNode(nodeId, nodeLabel, type, subtreesMoved, edgeLabels, 0);
            return new Pair(resTree, resExtent);
        }

        public void addChildren(Node newChild) {
            children.add(newChild);
        }

        public String getNodeLabel() {
            return nodeLabel;
        }

        public int getNodeId() {
            return nodeId;
        }

        public int getNodePid() {
            return nodePid;
        }

        public NodeType getType() {
            return type;
        }

    }


    public static class PositionedNode {
        public final Node node;  // Reference to original node data
        public final List<PositionedNode> children;
        public final double position;

        public PositionedNode(Node node, List<PositionedNode> children, double position) {
            this.node = node;
            this.children = children;
            this.position = position;
        }

        public PositionedNode move(double dx) {
            return new PositionedNode(node, children, position + dx);
        }

        @Override
        public String toString() {
            return "PositionedNode{" +
                    "id=" + node.id +
                    ", pos=" + position +
                    ", label=" + node.label +
                    ", type=" + node.type +
                    ", children=" + children +
                    '}';
        }
    }

    static class Extent {

        double minDist = 1.0;

        List<Pair<Double, Double>> extentList;

        public Extent() {
            this(new LinkedList<Pair<Double, Double>>());
        }

        public Extent(List<Pair<Double, Double>> extentList) {
            this.extentList = extentList;
        }

        public Extent(double left, double right) {
            List.of(new Pair(left, right));
        }


        public boolean isEmpty() {
            return extentList.isEmpty();
        }

        public void add(double x1, double x2) {
            extentList.add(new Pair(x1, x2));
        }

        public void addFirst(double x1, double x2) {
            extentList.add(0, new Pair(x1, x2));
        }

        public Extent move(double x) {
            return new Extent(extentList.stream().map(p -> new Pair<Double, Double>(p.left() + x, p.right() + x)).toList());
        }


        public Extent merge(Extent other) {

            List<Pair<Double, Double>> f = extentList;
            List<Pair<Double, Double>> s = other.extentList;
            List<Pair<Double, Double>> r = new LinkedList<>();

            Iterator<Pair<Double, Double>> fi = f.iterator();
            Iterator<Pair<Double, Double>> si = s.iterator();

            while (fi.hasNext() && si.hasNext()) {
                r.add(new Pair(fi.next().left(), si.next().right()));
            }

            if (!fi.hasNext()) {
                while (si.hasNext()) {
                    r.add(si.next());
                }
            }

            if (!si.hasNext()) {
                while (fi.hasNext()) {
                    r.add(fi.next());
                }
            }
            return new Extent(r);
        }

        public static Extent merge(List<Extent> extents) {
            Extent r = new Extent(); // empty
            for (Extent e : extents) {
                r = r.merge(e);
            }
            return r;
        }

        public Double fit(Extent other) {
            List<Pair<Double, Double>> f = extentList;
            List<Pair<Double, Double>> s = other.extentList;

            Iterator<Pair<Double, Double>> fi = f.iterator();
            Iterator<Pair<Double, Double>> si = s.iterator();

            Double minDist = 0.0;

            while (fi.hasNext() && si.hasNext()) {
                minDist = Math.max(minDist, fi.next().right() - si.next().left() + 1);
            }
            return minDist;

        }

        public static List<Double> fitListLeft(List<Extent> extents) {
            List<Double> res = new LinkedList<>();
            Extent acc = new Extent();
            for (Extent e : extents) {
                double x = acc.fit(e);
                res.add(x);
                acc = acc.merge(e.move(x));
            }
            return res;
        }

        public static List<Double> fitListRight(List<Extent> extents) {
            Collections.reverse(extents);
            List<Double> res = new LinkedList<>();
            Extent acc = new Extent();
            for (Extent e : extents) {
                double x = -e.fit(acc);
                res.add(x);
                acc = e.move(x).merge(acc);
            }
            Collections.reverse(extents);
            Collections.reverse(res);
            return res;
        }

        public static List<Double> fitList(List<Extent> extents) {
            List<Double> left = fitListLeft(extents);
            List<Double> right = fitListRight(extents);
            List<Double> res = new LinkedList<>();
            for (Iterator<Double> leftIte = left.iterator(), rightIte = right.iterator(); leftIte.hasNext() && rightIte.hasNext(); ) {
                res.add((leftIte.next() + rightIte.next()) / 2);
            }
            return res;
        }
    }
}





