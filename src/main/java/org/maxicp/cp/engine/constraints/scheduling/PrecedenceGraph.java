/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateTriPartition;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Precedence Graph constraint for a set of interval variables.
 * <p>
 * Maintains a reversible, incrementally updated precedence graph.
 * Each edge {@code i → j} means activity {@code i} must finish (plus an optional setup time)
 * before activity {@code j} starts.
 * <p>
 * Features:
 * <ul>
 *   <li>Incremental transitive closure upon adding new precedences.</li>
 *   <li>Cycle detection (throws {@link InconsistencyException}).</li>
 *   <li>Forward (est) and backward (lct) bound propagation along precedence chains.</li>
 *   <li>Detectable precedences: if two activities cannot overlap and the ordering
 *       {@code j before i} is infeasible, the edge {@code i → j} is added automatically.</li>
 *   <li>Setup (transition) times between activities.</li>
 *   <li>Full reversibility through {@link StateTriPartition}.</li>
 *   <li>Incrementally maintained <em>tail</em> values: the minimum remaining processing
 *       time after each activity finishes, along the longest successor chain.</li>
 * </ul>
 *
 * @author Pierre Schaus
 */
public class PrecedenceGraph extends AbstractCPConstraint {

    private final CPIntervalVar[] vars;
    private final int n;

    // predecessors[j] contains the set of activities that must precede j
    private final StateTriPartition[] predecessors;
    // successors[i] contains the set of activities that must follow i
    private final StateTriPartition[] successors;

    // Setup/transition times: setupTimes[i][j] = minimum gap between end of i and start of j
    private final int[][] setupTimes;

    // Fast lookup from variable reference to index
    private final Map<CPIntervalVar, Integer> varIndex;

    // Whether to automatically detect and add forced precedences (pairwise O(n²) check).
    // Disable when a separate NoOverlap constraint handles machine disjunctions.
    private boolean detectPrecedencesEnabled = true;

    // Tail (Q value): tail[i] = minimum remaining processing time after activity i finishes,
    // along the longest path through its successors in the precedence graph.
    // Incrementally maintained when precedences are added.
    private final StateInt[] tail;

    // Scratch arrays for iteration (not reversible, just working buffers)
    private final int[] iterBuf1;
    private final int[] iterBuf2;
    private final int[] possibleBuf; // used by detectPrecedences (separate from iterBuf used by addPrecedenceInternal)
    private final Integer[] sortBuf;

    /**
     * Creates a precedence graph constraint with zero setup times.
     *
     * @param vars the interval variables
     */
    public PrecedenceGraph(CPIntervalVar... vars) {
        this(new int[vars.length][vars.length], vars);
    }

    /**
     * Creates a precedence graph constraint with the given setup times.
     *
     * @param setupTimes setupTimes[i][j] is the minimum time between end of activity i and start of activity j
     * @param vars       the interval variables
     */
    public PrecedenceGraph(int[][] setupTimes, CPIntervalVar... vars) {
        super(vars[0].getSolver());
        this.vars = vars;
        this.n = vars.length;
        this.setupTimes = setupTimes;

        predecessors = new StateTriPartition[n];
        successors = new StateTriPartition[n];
        for (int i = 0; i < n; i++) {
            predecessors[i] = new StateTriPartition(getSolver().getStateManager(), n);
            successors[i] = new StateTriPartition(getSolver().getStateManager(), n);
            // Exclude self-edges
            predecessors[i].exclude(i);
            successors[i].exclude(i);
        }

        varIndex = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            varIndex.put(vars[i], i);
        }

        tail = new StateInt[n];
        for (int i = 0; i < n; i++) {
            tail[i] = getSolver().getStateManager().makeStateInt(0);
        }

        iterBuf1 = new int[n];
        iterBuf2 = new int[n];
        possibleBuf = new int[n];
        sortBuf = new Integer[n];
    }

    @Override
    public void post() {
        for (CPIntervalVar var : vars) {
            var.propagateOnChange(this);
        }
        propagate();
    }

    // ------ Public API ------

    /**
     * Returns the number of activities in the graph.
     */
    public int size() {
        return n;
    }

    /**
     * Enables or disables automatic detection of forced precedences (detectable precedences).
     * <p>
     * When enabled (the default), the propagator performs an O(n²) pairwise check to detect
     * orderings that are forced by the current time windows. Disable this when a separate
     * {@code NoOverlap} constraint already handles disjunctive resource propagation.
     *
     * @param enabled true to enable, false to disable
     */
    public void setDetectPrecedences(boolean enabled) {
        this.detectPrecedencesEnabled = enabled;
    }

    /**
     * Returns the interval variable at the given index.
     *
     * @param idx the index of the variable
     * @return the interval variable at that index
     */
    public CPIntervalVar getVar(int idx) {
        return vars[idx];
    }

    /**
     * Returns the index of the given interval variable in this graph.
     *
     * @param var the interval variable to look up
     * @return the index of the variable
     * @throws IllegalArgumentException if the variable is not in this graph
     */
    public int indexOf(CPIntervalVar var) {
        Integer idx = varIndex.get(var);
        if (idx == null) throw new IllegalArgumentException("Variable not found in this precedence graph");
        return idx;
    }

    /**
     * Returns the interval variables managed by this graph.
     */
    public CPIntervalVar[] getVars() {
        return vars;
    }

    /**
     * Returns the tail value for activity {@code idx}.
     * <p>
     * The tail represents the minimum remaining processing time after the activity
     * finishes, along the longest successor chain in the precedence graph.
     * For a leaf activity (no successors), the tail is 0.
     * <p>
     * Formally: {@code tail(i) = max over successors j of { setup(i,j) + dur(j) + tail(j) }}
     *
     * @param idx the activity index
     * @return the tail value
     */
    public int getTail(int idx) {
        return tail[idx].value();
    }

    /**
     * Returns the tail value for the given interval variable.
     *
     * @param var the interval variable
     * @return the tail value
     */
    public int getTail(CPIntervalVar var) {
        return getTail(indexOf(var));
    }

    /**
     * Returns the tail (Q) value for the given interval variable.
     * Alias for {@link #getTail(CPIntervalVar)}.
     *
     * @param var the interval variable
     * @return the tail value
     */
    public int getQ(CPIntervalVar var) {
        return getTail(var);
    }

    public void setTail(int val, CPIntervalVar var) {
        tail[indexOf(var)].setValue(val);
    }

    /**
     * Adds a precedence edge {@code i → j} (activity i must end before activity j starts,
     * respecting the setup time).
     * <p>
     * This incrementally updates the transitive closure and triggers propagation.
     * Throws {@link InconsistencyException} if a cycle is detected.
     *
     * @param i the predecessor activity index
     * @param j the successor activity index
     */
    public void addPrecedence(int i, int j) {
        if (i == j) {
            throw InconsistencyException.INCONSISTENCY; // self-loop = cycle
        }
        if (predecessors[j].isIncluded(i)) {
            return; // already known
        }
        // Cycle detection: if j is already a predecessor of i, then adding i→j creates a cycle
        if (predecessors[i].isIncluded(j)) {
            throw InconsistencyException.INCONSISTENCY;
        }

        addPrecedenceInternal(i, j);
        propagate();
        getSolver().fixPoint();
    }

    /**
     * Adds precedence edges from a single source to multiple targets in batch.
     * Only triggers propagation and fixpoint once after all edges are added.
     * <p>
     * Equivalent to calling {@link #addPrecedence(int, int)} for each target,
     * but more efficient because propagation happens only once.
     *
     * @param from the predecessor activity index
     * @param tos  array of successor activity indices
     * @param nTos number of valid entries in {@code tos}
     */
    public void addPrecedences(int from, int[] tos, int nTos) {
        boolean anyNew = false;
        for (int k = 0; k < nTos; k++) {
            int j = tos[k];
            if (from == j) throw InconsistencyException.INCONSISTENCY;
            if (predecessors[j].isIncluded(from)) continue;
            if (predecessors[from].isIncluded(j)) throw InconsistencyException.INCONSISTENCY;
            addPrecedenceInternal(from, j);
            anyNew = true;
        }
        if (anyNew) {
            propagate();
            getSolver().fixPoint();
        }
    }

    /**
     * Returns true if there is a known (included) precedence edge {@code i → j}.
     */
    public boolean hasPrecedence(int i, int j) {
        return predecessors[j].isIncluded(i);
    }

    /**
     * Returns true if the precedence edge {@code i → j} is still possible (not excluded and not included).
     */
    public boolean isPossiblePrecedence(int i, int j) {
        return successors[i].isPossible(j);
    }

    /**
     * Fills the output array with the indices of included predecessors of activity {@code varIdx}.
     *
     * @return the number of predecessors written to {@code out}
     */
    public int fillPredecessors(int varIdx, int[] out) {
        return predecessors[varIdx].fillIncluded(out);
    }

    /**
     * Fills the output array with the indices of included successors of activity {@code varIdx}.
     *
     * @return the number of successors written to {@code out}
     */
    public int fillSuccessors(int varIdx, int[] out) {
        return successors[varIdx].fillIncluded(out);
    }

    /**
     * Returns the number of included predecessors of activity {@code varIdx}.
     */
    public int nPredecessors(int varIdx) {
        return predecessors[varIdx].nIncluded();
    }

    /**
     * Returns the number of included successors of activity {@code varIdx}.
     */
    public int nSuccessors(int varIdx) {
        return successors[varIdx].nIncluded();
    }

    // ------ Propagation ------

    @Override
    public void propagate() {
        boolean changed = true;
        while (changed) {
            changed = false;
            changed |= propagateForward();
            changed |= propagateBackward();
            if (detectPrecedencesEnabled) {
                changed |= detectPrecedences();
            }
        }
    }

    /**
     * Forward propagation: for each activity, compute est from its predecessors.
     * est(j) >= max over predecessors p of { est(p) + dur(p) + setup(p, j) }
     * <p>
     * We process activities sorted by their current est (topological-ish order)
     *
     * @return true if any bound was tightened
     */
    private boolean propagateForward() {
        boolean changed = false;
        // Sort activities by est for a good processing order
        for (int k = 0; k < n; k++) sortBuf[k] = k;
        Arrays.sort(sortBuf, 0, n, Comparator.comparingInt(a -> vars[a].startMin()));

        for (int idx = 0; idx < n; idx++) {
            int i = sortBuf[idx];
            if (vars[i].isAbsent()) continue;

            int nPred = predecessors[i].fillIncluded(iterBuf1);
            if (nPred == 0) continue;

            // est(i) >= max over all predecessors p of { est(p) + dur(p) + setup(p, i) }
            int newEst = Integer.MIN_VALUE;
            for (int k = 0; k < nPred; k++) {
                int p = iterBuf1[k];
                if (vars[p].isAbsent()) continue;
                int bound = vars[p].startMin() + vars[p].lengthMin() + setupTimes[p][i];
                newEst = Math.max(newEst, bound);
            }

            if (newEst > vars[i].startMin()) {
                vars[i].setStartMin(newEst);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Backward propagation: for each activity, compute lct from its successors.
     * endMax(i) <= min over successors s of { endMax(s) - dur(s) - setup(i, s) }
     *
     * @return true if any bound was tightened
     */
    private boolean propagateBackward() {
        boolean changed = false;
        // Sort activities by endMax descending for a good processing order
        for (int k = 0; k < n; k++) sortBuf[k] = k;
        Arrays.sort(sortBuf, 0, n, Comparator.comparingInt(a -> -vars[a].endMax()));

        for (int idx = 0; idx < n; idx++) {
            int i = sortBuf[idx];
            if (vars[i].isAbsent()) continue;

            int nSucc = successors[i].fillIncluded(iterBuf1);
            if (nSucc == 0) continue;

            // endMax(i) <= min over all successors s of { endMax(s) - dur(s) - setup(i, s) }
            int newLct = Integer.MAX_VALUE;
            for (int k = 0; k < nSucc; k++) {
                int s = iterBuf1[k];
                if (vars[s].isAbsent()) continue;
                int bound = vars[s].endMax() - vars[s].lengthMin() - setupTimes[i][s];
                newLct = Math.min(newLct, bound);
            }

            if (newLct < vars[i].endMax()) {
                vars[i].setEndMax(newLct);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Detectable precedences: for each activity, iterate only its <em>possible</em> successors
     * (using {@link StateTriPartition#fillPossible}), checking if one ordering is forced.
     * <p>
     * This is much more efficient than the O(n²) all-pairs scan because pairs that
     * have already been decided (included or excluded) are skipped automatically.
     * <p>
     * Absent activities encountered during iteration are dynamically excluded from the
     * predecessor/successor partitions.
     *
     * @return true if any new precedence was detected
     */
    private boolean detectPrecedences() {
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            if (vars[i].isAbsent()) continue;
            // Only iterate possible successors of i — already-decided pairs are skipped
            int nPossSucc = successors[i].fillPossible(possibleBuf);
            for (int k = 0; k < nPossSucc; k++) {
                int j = possibleBuf[k];
                if (vars[j].isAbsent()) continue; // skip absent, but don't exclude (precedences may still transit through them)
                // May have been decided by a previous iteration in this pass
                if (hasPrecedence(i, j) || hasPrecedence(j, i)) continue;

                // ect(i) + setup(i,j) > lst(j) means i cannot precede j
                boolean iBj_infeasible =
                        vars[i].startMin() + vars[i].lengthMin() + setupTimes[i][j]
                                > vars[j].startMax();
                // ect(j) + setup(j,i) > lst(i) means j cannot precede i
                boolean jBi_infeasible =
                        vars[j].startMin() + vars[j].lengthMin() + setupTimes[j][i]
                                > vars[i].startMax();

                if (iBj_infeasible && jBi_infeasible) {
                    throw InconsistencyException.INCONSISTENCY;
                }
                if (iBj_infeasible) {
                    // i cannot precede j, so j must precede i
                    addPrecedenceInternal(j, i);
                    changed = true;
                } else if (jBi_infeasible) {
                    // j cannot precede i, so i must precede j
                    addPrecedenceInternal(i, j);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Internal version of addPrecedence that does NOT call fixPoint.
     * Also dynamically excludes reverse directions in the tri-partitions
     * and incrementally updates tail values.
     */
    private void addPrecedenceInternal(int i, int j) {
        if (i == j) {
            throw InconsistencyException.INCONSISTENCY;
        }
        if (predecessors[j].isIncluded(i)) {
            return;
        }
        if (predecessors[i].isIncluded(j)) {
            throw InconsistencyException.INCONSISTENCY;
        }

        predecessors[j].include(i);
        successors[i].include(j);
        // Exclude the reverse: j→i is now impossible
        predecessors[i].exclude(j);
        successors[j].exclude(i);

        // Incremental transitive closure:
        // pred(i) ∪ {i} all must precede succ(j) ∪ {j}
        int nPredI = predecessors[i].fillIncluded(iterBuf1);
        iterBuf1[nPredI] = i;
        nPredI++;

        int nSuccJ = successors[j].fillIncluded(iterBuf2);
        iterBuf2[nSuccJ] = j;
        nSuccJ++;

        for (int pi = 0; pi < nPredI; pi++) {
            int pred = iterBuf1[pi];
            for (int sj = 0; sj < nSuccJ; sj++) {
                int succ = iterBuf2[sj];
                if (pred == succ) {
                    throw InconsistencyException.INCONSISTENCY;
                }
                predecessors[succ].include(pred);
                successors[pred].include(succ);
                // Exclude reverse direction: succ→pred is impossible
                predecessors[pred].exclude(succ);
                successors[succ].exclude(pred);
            }
        }

        // Incrementally update tail for the new edge i → j.
        // The chain path through intermediaries always dominates direct transitive edges,
        // so backward propagation from i is sufficient (no tail updates needed in the
        // transitive closure loop above).
        int newTailI = setupTimes[i][j] + vars[j].lengthMin() + tail[j].value();
        if (newTailI > tail[i].value()) {
            tail[i].setValue(newTailI);
            propagateTailBackward(i);
        }
    }

    /**
     * Propagates tail values backward through predecessors.
     * <p>
     * For each predecessor {@code p} of {@code idx}, checks if
     * {@code tail[p] < setup(p, idx) + dur(idx) + tail[idx]}
     * and updates it if so, recursing further backward.
     * Terminates because tail values are monotonically increasing.
     */
    private void propagateTailBackward(int idx) {
        int[] predBuf = new int[n];
        int nPred = predecessors[idx].fillIncluded(predBuf);
        for (int k = 0; k < nPred; k++) {
            int p = predBuf[k];
            int nt = setupTimes[p][idx] + vars[idx].lengthMin() + tail[idx].value();
            if (nt > tail[p].value()) {
                tail[p].setValue(nt);
                propagateTailBackward(p);
            }
        }
    }
}
