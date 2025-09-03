package org.maxicp.util.algo;

import java.util.*;

/**
 * Tree structure for visualizing search trees (e.g., backtracking algorithms).
 * Implements this tree layout algorithm:
 * https://www.microsoft.com/en-us/research/wp-content/uploads/1996/01/drawingtrees.pdf
 */
public class Tree {

    private final Map<Integer, Node> nodeMap;
    private final int rootId;

    public enum NodeType {
        INNER, SKIP, FAIL, SOLUTION
    }

    public Tree(int rootId) {
        this.nodeMap = new HashMap<>();
        this.rootId = rootId;
        Node root = new Node(rootId, -1, "", NodeType.INNER);
        nodeMap.put(rootId, root);
    }

    public void createNode(int id, int parentId, NodeType type) {
        Node parent = nodeMap.get(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("Parent with id " + parentId + " not found");
        }
        Node child = parent.addChild(id, type, "", "");
        nodeMap.put(id, child);
    }

    public void createNode(int id, int parentId, NodeType type, String nodeLabel, String edgeLabel) {
        Node parent = nodeMap.get(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("Parent with id " + parentId + " not found");
        }
        Node child = parent.addChild(id, type, nodeLabel, edgeLabel);
        nodeMap.put(id, child);
    }

    public Node root() {
        return nodeMap.get(rootId);
    }

    /** Immutable pair */
    public static record Pair<L, R>(L left, R right) { }

    /** Tree node containing logical data (not layout). */
    public static class Node {
        public final int id;
        public final int parentId;
        public final NodeType type;
        public final String label;
        public final List<Node> children;
        public final List<String> edgeLabels;

        public Node(int id, int parentId, String label, NodeType type) {
            this.id = id;
            this.parentId = parentId;
            this.label = label;
            this.type = type;
            this.children = new ArrayList<>();
            this.edgeLabels = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "Node{" +
                    "id=" + id +
                    ", parentId=" + parentId +
                    ", type=" + type +
                    ", label='" + label + '\'' +
                    ", children=" + children +
                    ", edgeLabels=" + edgeLabels +
                    '}';
        }

        public Node addChild(int childId, NodeType type, String label, String edgeLabel) {
            Node child = new Node(childId, this.id, label, type);
            children.add(child);
            edgeLabels.add(edgeLabel);
            return child;
        }

        public PositionedNode design() {
            return designInternal().left();
        }

        private Pair<PositionedNode, Extent> designInternal() {
            List<PositionedNode> positionedChildren = new ArrayList<>();
            List<Extent> childExtents = new ArrayList<>();

            for (Node child : children) {
                var result = child.designInternal();
                positionedChildren.add(result.left());
                childExtents.add(result.right());
            }

            List<Double> shifts = Extent.fitList(childExtents);

            List<PositionedNode> shiftedChildren = new ArrayList<>();
            List<Extent> shiftedExtents = new ArrayList<>();
            for (int i = 0; i < positionedChildren.size(); i++) {
                shiftedChildren.add(positionedChildren.get(i).move(shifts.get(i)));
                shiftedExtents.add(childExtents.get(i).move(shifts.get(i)));
            }

            Extent merged = Extent.merge(shiftedExtents);
            merged.addFirst(0, 0);

            PositionedNode positioned = new PositionedNode(this, shiftedChildren, 0);
            return new Pair<>(positioned, merged);
        }
    }

    /** Layout node: wraps a Node with its calculated horizontal position. */
    public static class PositionedNode {
        public final Node node; // Reference to original logical node
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
            return "PositionedNode{ pos=" + position + ", node =" + node + '}';
        }

        public String toTikz(double xScale, double yStep, double labelOffsetPt, double nodeDiameterMm) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("\\begin{tikzpicture}[x=%.3fcm,y=%.3fcm,font=\\tiny]\n", xScale, yStep));
            sb.append("\\usetikzlibrary{positioning}\n");

            // TikZ styles with configurable diameter
            sb.append(String.format("\\tikzstyle{inner}=[circle, draw, fill=white!90!black, minimum size=%.2fmm, inner sep=1pt]\n", nodeDiameterMm));
            sb.append(String.format("\\tikzstyle{skip}=[circle, draw, fill=yellow!50!white, minimum size=%.2fmm, inner sep=1pt]\n", nodeDiameterMm));
            sb.append(String.format("\\tikzstyle{fail}=[circle, draw, fill=red!50!white, minimum size=%.2fmm, inner sep=1pt]\n", nodeDiameterMm));
            sb.append(String.format("\\tikzstyle{solution}=[circle, draw, fill=green!50!white, minimum size=%.2fmm, inner sep=1pt]\n\n", nodeDiameterMm));

            // Pass 1: nodes with absolute coordinates
            emitNodes(sb, 0, 0.0, labelOffsetPt);

            sb.append("\n");

            // Pass 2: edges
            emitEdges(sb, 0.0);

            sb.append("\\end{tikzpicture}\n");
            return sb.toString();
        }

        // Overload with default diameter for backward compatibility
        public String toTikz() {
            return toTikz(1.0, 1.0, 2.0, 4.0); // default 4mm diameter
        }

        private void emitNodes(StringBuilder sb, int depth, double absX, double labelOffsetPt) {
            double x = absX + position; // convert relative to absolute
            double y = -depth;
            String style = nodeTypeToStyle(node.type);
            String nodeName = "n" + node.id;

            sb.append(String.format("\\node[%s] (%s) at (%.3f,%.3f) {};\n", style, nodeName, x, y));
            sb.append(String.format("\\node[right=%.0fpt of %s] {%s};\n", labelOffsetPt, nodeName, node.label));

            for (PositionedNode child : children) {
                child.emitNodes(sb, depth + 1, x, labelOffsetPt); // pass absolute X
            }
        }

        private void emitEdges(StringBuilder sb, double absX) {
            String parentName = "n" + node.id;
            double parentAbsX = absX + position;
            for (int i = 0; i < children.size(); i++) {
                PositionedNode child = children.get(i);
                String childName = "n" + child.node.id;
                String edgeLabel = (i < node.edgeLabels.size()) ? node.edgeLabels.get(i) : "";
                sb.append(String.format("\\draw (%s) -- node[midway, left]{%s} (%s);\n",
                        parentName, edgeLabel, childName));
                child.emitEdges(sb, parentAbsX); // pass parent absolute X
            }
        }

        private String nodeTypeToStyle(NodeType type) {
            return switch (type) {
                case INNER -> "inner";
                case SKIP -> "skip";
                case FAIL -> "fail";
                case SOLUTION -> "solution";
            };
        }
    }

    /** Used for computing horizontal spacing between subtrees. */
    private static class Extent {
        private final List<Pair<Double, Double>> extentList;

        public Extent() {
            this.extentList = new ArrayList<>();
        }

        public Extent(List<Pair<Double, Double>> extentList) {
            this.extentList = extentList;
        }

        public Extent move(double x) {
            List<Pair<Double, Double>> moved = new ArrayList<>();
            for (Pair<Double, Double> p : extentList) {
                moved.add(new Pair<>(p.left() + x, p.right() + x));
            }
            return new Extent(moved);
        }

        public void addFirst(double x1, double x2) {
            extentList.add(0, new Pair<>(x1, x2));
        }

        public static Extent merge(List<Extent> extents) {
            Extent result = new Extent();
            for (Extent e : extents) {
                result = result.merge(e);
            }
            return result;
        }

        public Extent merge(Extent other) {
            List<Pair<Double, Double>> merged = new ArrayList<>();
            Iterator<Pair<Double, Double>> it1 = extentList.iterator();
            Iterator<Pair<Double, Double>> it2 = other.extentList.iterator();

            while (it1.hasNext() && it2.hasNext()) {
                merged.add(new Pair<>(it1.next().left(), it2.next().right()));
            }
            while (it1.hasNext()) merged.add(it1.next());
            while (it2.hasNext()) merged.add(it2.next());

            return new Extent(merged);
        }

        private double fit(Extent other) {
            Iterator<Pair<Double, Double>> it1 = extentList.iterator();
            Iterator<Pair<Double, Double>> it2 = other.extentList.iterator();
            double minDist = 0.0;
            while (it1.hasNext() && it2.hasNext()) {
                minDist = Math.max(minDist, it1.next().right() - it2.next().left() + 1);
            }
            return minDist;
        }

        private static List<Double> fitListLeft(List<Extent> extents) {
            List<Double> res = new ArrayList<>();
            Extent acc = new Extent();
            for (Extent e : extents) {
                double x = acc.fit(e);
                res.add(x);
                acc = acc.merge(e.move(x));
            }
            return res;
        }

        private static List<Double> fitListRight(List<Extent> extents) {
            Collections.reverse(extents);
            List<Double> res = new ArrayList<>();
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
            List<Double> res = new ArrayList<>();
            for (int i = 0; i < left.size(); i++) {
                res.add((left.get(i) + right.get(i)) / 2);
            }
            return res;
        }
    }

    public static void main(String[] args) {
        Tree t = new Tree(0); // root id = 0

        // Level 1
        t.createNode(1, 0, Tree.NodeType.INNER, "$D(x)=\\{1,2,3\\}$", "$x=1$");
        t.createNode(2, 0, Tree.NodeType.SOLUTION);
        t.createNode(3, 0, Tree.NodeType.SKIP);

        // Level 2
        t.createNode(4, 1, Tree.NodeType.FAIL);
        t.createNode(5, 1, Tree.NodeType.SOLUTION);

        t.createNode(6, 2, Tree.NodeType.INNER);
        t.createNode(7, 2, Tree.NodeType.FAIL);

        t.createNode(8, 3, Tree.NodeType.SKIP);

        // Level 3
        t.createNode(9, 6, Tree.NodeType.SOLUTION);
        t.createNode(10, 8, Tree.NodeType.FAIL);

        // Design and print TikZ
        Tree.PositionedNode pn = t.root().design();
        System.out.println(pn.toTikz());
    }
}