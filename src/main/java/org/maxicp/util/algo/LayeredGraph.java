package org.maxicp.util.algo;

import java.util.*;

public final class LayeredGraph<State> {

    /* ---------- Node storage ---------- */

    // For each node, store incoming and outgoing edges
    private final ArrayList<EdgeList> succ = new ArrayList<>();
    private final ArrayList<EdgeList> pred = new ArrayList<>();

    private final ArrayList<State> states = new ArrayList<>();

    /* ---------- Layers ---------- */

    // layerStart[i] = index of first node in layer i
    // layerStart[i+1] = index after last node
    private final int[] layerStart;

    /* ---------- Uniqueness ---------- */

    // Map state → node index (per layer or global)
    private final HashMap<State, Integer> stateToNode = new HashMap<>();

    public LayeredGraph(int nLayers) {
        this.layerStart = new int[nLayers + 1];
    }

    public State getState(int node) {
        return states.get(node);
    }

    // node creation
    public int getOrCreateNode(State s) {
        Integer idx = stateToNode.get(s);
        if (idx != null) return idx;
        int id = states.size();
        states.add(s);
        succ.add(new EdgeList());
        pred.add(new EdgeList());
        stateToNode.put(s, id);
        return id;
    }

    // edge creation
    public void addEdge(int from, int to, int label) {
        succ.get(from).add(to, label);
        pred.get(to).add(from, label);
    }

    public EdgeList successors(int node) {
        return succ.get(node);
    }

    public EdgeList predecessors(int node) {
        return pred.get(node);
    }

    // layer management
    public void startLayer(int layer) {
        layerStart[layer] = states.size();
    }

    public void endLayer(int layer) {
        layerStart[layer + 1] = states.size();
    }

    public int layerSize(int layer) {
        return layerStart[layer + 1] - layerStart[layer];
    }

    public Iterable<Integer> nodesInLayer(int layer) {
        int begin = layerStart[layer];
        int end   = layerStart[layer + 1];
        return () -> new Iterator<>() {
            int cur = begin;
            public boolean hasNext() { return cur < end; }
            public Integer next() { return cur++; }
        };
    }

    public int layerBegin(int l) { return layerStart[l]; }
    public int layerEnd(int l) { return layerStart[l + 1]; }

    public static final class EdgeList {
        private int[] node = new int[4];
        private int[] label = new int[4];
        private int size = 0;

        void add(int target, int label) {
            if (size == node.length) {
                node = Arrays.copyOf(node, size * 2);
                this.label = Arrays.copyOf(this.label, size * 2);
            }
            node[size] = target;
            this.label[size] = label;
            size++;
        }

        public int size() { return size; }
        public int node(int i) { return node[i]; }   // node
        public int label(int i) { return label[i]; }  // label
    }
}