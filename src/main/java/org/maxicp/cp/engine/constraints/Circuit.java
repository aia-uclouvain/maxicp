/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.util.GraphUtil;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.Stack;
import java.util.stream.IntStream;

/**
 * Hamiltonian Circuit Constraint with a successor model
 */
public class Circuit extends AbstractCPConstraint {

    int n;
    public boolean deactivateSCC = false;
    private final CPIntVar[] x;

    // otherEnd[u] stores the opposite endpoint of the maximal path passing through node u.
    // For a path s ->* d:
    //   otherEnd[s] = d (start points to end)
    //   otherEnd[d] = s (end points to start)
    // For singletons (path of length 0): otherEnd[u] = u.
    // Internal nodes in paths are never accessed because in-degree and out-degree are at most 1.
    private final StateInt[] otherEnd;
    private final StateInt nFixed;
    private int [][] domArray;

    // SCC variables
    int[] discoveryTime;     // Discovery times of visited vertices
    int[] low;               // Lowest points of reachable vertices
    boolean[] isInStack; // Boolean array to keep track of stack members
    Stack<Integer> stack; // Stack to store visited vertices


    /**
     * Creates a Hamiltonian Circuit Constraint
     * with a successor model.
     *
     * @param x the variables representing the successor array that is
     *          {@code x[i]} is the city visited after city i
     */
    public Circuit(CPIntVar[] x) {
        super(x[0].getSolver());
        assert (x.length > 0);
        nFixed = getSolver().getStateManager().makeStateInt(0);
        this.x = x;
        this.n = x.length;

        otherEnd = new StateInt[x.length];
        domArray = new int[x.length][];
        for (int i = 0; i < x.length; i++) {
            otherEnd[i] = getSolver().getStateManager().makeStateInt(i);
            domArray[i] = new int[x[i].size()];
        }
        stack = new Stack<>();
        discoveryTime = new int[x.length];
        low = new int[x.length];
        isInStack = new boolean[x.length];
    }


    @Override
    public void post() {
        for (CPIntVar var: x) {
            var.removeBelow(0);
            var.removeAbove(x.length - 1);
        }
        getSolver().post(new AllDifferentDC(x));
        if (x.length == 1) {
            x[0].fix(0);
            return;
        }
        for (int i = 0; i < x.length; i++) {
            x[i].remove(i);
        }
        for (int i = 0; i < x.length; i++) {
            x[i].propagateOnDomainChange(this);
            if (!x[i].isFixed()) {
                int idx = i;
                x[idx].whenFixed(() -> fixed(idx));
            } else {
                fixed(i);
            }
        }
        propagate();
    }

    @Override
    public void propagate() {
        if (!deactivateSCC) {
            int nSCC = findSCCCount();
            if (nSCC > 1) {
                throw InconsistencyException.INCONSISTENCY;
            }
        }
    }

    /**
     * Called whenever variable x[idx] is fixed to val (adding directed edge idx -> val).
     * Connects two disjoint paths:
     *   (orig_i ->* idx) and (val ->* dest_j)
     * into a single merged path:
     *   orig_i ->* idx -> val ->* dest_j
     * and forbids closing a premature subtour by removing orig_i from x[dest_j].
     *
     * @param idx source node of the newly fixed edge
     */
    public void fixed(int idx) {
        int val = x[idx].min();
        // idx is the end of the first path; otherEnd[idx] gives its start orig_i
        int orig_i = otherEnd[idx].value();
        // val is the start of the second path; otherEnd[val] gives its end dest_j
        int dest_j = otherEnd[val].value();

        // Connect the two paths: orig_i ->* idx -> val ->* dest_j.
        // Update the opposite extremities of this newly merged path:
        otherEnd[orig_i].setValue(dest_j);
        otherEnd[dest_j].setValue(orig_i);
        nFixed.increment();
        if (nFixed.value() < x.length - 1) {
            // Avoid inner loops: dest_j cannot close the tour to orig_i before all nodes are included
            x[dest_j].remove(orig_i);
        }
    }

    // Returns the number of SCCs
    public int findSCCCount() {

        stack.clear();
        // Initialize discovery and low values
        for (int i = 0; i < n; i++) {
            discoveryTime[i] = -1;
            low[i] = -1;
            isInStack[i] = false;
        }

        // Counter for the number of SCCs
        int sccCount = 0;
        int time = 0; // Local time variable for each SCC calculation

        // Call the recursive helper function for each vertex
        for (int i = 0; i < n; i++) {
            if (discoveryTime[i] == -1) {
                sccCount += nSCC(i, discoveryTime, low, isInStack, stack, time);
            }
        }
        return sccCount;
    }

    // Recursive function to find strongly connected components
    private int nSCC(int u, int[] discoveryTime, int[] low, boolean[] isInStack, Stack<Integer> stack, int time) {
        // Initialize discovery time and low value for u
        discoveryTime[u] = low[u] = ++time;
        stack.push(u);
        isInStack[u] = true;

        // Go through all vertices v adjacent to u
        int nVal = x[u].fillArray(domArray[u]);
        for (int i = 0; i < nVal; i++) {
            int v = domArray[u][i];
            if (discoveryTime[v] == -1) { // If v is not visited
                time = nSCC(v, discoveryTime, low, isInStack, stack, time);
                low[u] = Math.min(low[u], low[v]);
            } else if (isInStack[v]) { // If v is in stack, it is part of the current SCC
                low[u] = Math.min(low[u], discoveryTime[v]);
            }
        }

        // If u is a root node, pop all vertices in the current SCC
        int sccRootCount = 0;
        if (low[u] == discoveryTime[u]) {
            sccRootCount++;
            while (stack.peek() != u) {
                int w = stack.pop();
                isInStack[w] = false;
            }
            stack.pop();
            isInStack[u] = false;
        }
        return sccRootCount;
    }
}
