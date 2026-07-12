/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.state.datastructures.StateStack;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;

/**
 * Implementation of a sequence pattern variable.
 * <p>
 * The occurrence kernel is a port of the PPIC pseudo-projection algorithm
 * (Aoga, Guns, Schaus, An efficient algorithm for mining frequent sequence
 * with constraint programming, ECML-PKDD 2016). The pseudo-projected database
 * is stored in two parallel grow-only arrays (sequence id, position of the
 * prefix in the sequence); two reversible integers delimit the segment of the
 * current projection, so backtracking simply moves them back.
 * <p>
 * The candidate frontier is one refill array per prefix depth: appending a
 * symbol REWRITES the next depth's array with exactly the symbols supported by
 * the new projection (an O(supported) operation, independent of the alphabet
 * size), and exclusions swap-and-shrink under a reversible size. Rewriting is
 * sound because the search is LIFO: when an append reaches a depth, every
 * deeper decision has been undone, so the old content is dead. Each prefix
 * thus has its own frontier, which is required for the append/exclude search
 * to enumerate every pattern exactly once.
 */
public class CPSeqPatternVarImpl implements CPSeqPatternVar {

    private final CPSolver cp;
    private final SequenceDb db;
    private final int nSymbols;

    private final int[] prefix;
    private final StateInt prefixLen;

    // frontier[d] = candidates when prefixLen == d; positions in [0, nFrontier[d])
    // are the current members, posOf[d][a] is a's position (checked against the
    // content, since entries of older rewrites may be stale)
    private final int[][] frontier;
    private final int[][] posOf;
    private final StateInt[] nFrontier;
    private final StateInt status;

    // PPIC pseudo-projected database: grow-only trail of (sequence id, position) pairs
    private int innerTrailSize;
    private int[] psdbSeqId;
    private int[] psdbPosInSeq; // 0-based start of the projected suffix, -1 for the whole sequence
    private final StateInt psdbStart; // start of the current projection segment
    private final StateInt psdbSize; // number of sequences in the current projection

    // supportCounter[d][a] = support of prefix·a when prefixLen == d; computed by
    // the projection that enters depth d and valid for the whole subtree by the
    // same LIFO argument as the frontier arrays (deeper appends write deeper rows)
    private final int[][] supportCounter;

    private final StateStack<CPConstraint> onDomain;
    private final StateStack<CPConstraint> onFix;

    private final int[] tmpSymbols;

    /**
     * Creates a sequence pattern variable with an empty prefix over a database.
     *
     * @param cp the solver in which the variable is created
     * @param db the sequence database over which the pattern is mined
     */
    public CPSeqPatternVarImpl(CPSolver cp, SequenceDb db) {
        this.cp = cp;
        this.db = db;
        this.nSymbols = db.nSymbols();
        StateManager sm = cp.getStateManager();
        prefix = new int[db.maxSeqLength()];
        prefixLen = sm.makeStateInt(0);
        int nDepths = db.maxSeqLength() + 1;
        frontier = new int[nDepths][Math.max(nSymbols - 1, 1)];
        posOf = new int[nDepths][nSymbols];
        nFrontier = new StateInt[nDepths];
        for (int d = 0; d < nDepths; d++)
            nFrontier[d] = sm.makeStateInt(0);
        status = sm.makeStateInt(POSSIBLE);
        int nSeqs = db.nSeqs();
        innerTrailSize = Math.max(nSeqs * 5, 4);
        psdbSeqId = new int[innerTrailSize];
        for (int i = 0; i < innerTrailSize; i++)
            psdbSeqId[i] = i; // only the first nSeqs entries are meaningful
        psdbPosInSeq = new int[innerTrailSize];
        Arrays.fill(psdbPosInSeq, -1);
        psdbStart = sm.makeStateInt(0);
        psdbSize = sm.makeStateInt(nSeqs);
        supportCounter = new int[nDepths][nSymbols];
        for (int a = 1; a < nSymbols; a++)
            supportCounter[0][a] = db.symbolSupport(a);
        onDomain = new StateStack<>(sm);
        onFix = new StateStack<>(sm);
        tmpSymbols = new int[Math.max(nSymbols - 1, 1)];
        int n = 0;
        for (int a = 1; a < nSymbols; a++)
            if (db.symbolSupport(a) > 0)
                tmpSymbols[n++] = a;
        refillFrontier(0, tmpSymbols, n);
        if (n == 0)
            status.setValue(TERMINATED); // the empty pattern is the only possible value
    }

    /**
     * Rewrites the frontier of a depth with the given symbols; only sound when
     * the depth is being (re-)entered, i.e. no deeper state is alive.
     */
    private void refillFrontier(int depth, int[] symbols, int n) {
        int[] arr = frontier[depth];
        int[] pos = posOf[depth];
        for (int i = 0; i < n; i++) {
            arr[i] = symbols[i];
            pos[symbols[i]] = i;
        }
        nFrontier[depth].setValue(n);
    }

    @Override
    public SequenceDb database() {
        return db;
    }

    @Override
    public int nSymbols() {
        return nSymbols;
    }

    @Override
    public int prefixLength() {
        return prefixLen.value();
    }

    @Override
    public int prefixSymbol(int j) {
        if (j < 0 || j >= prefixLen.value())
            throw new IndexOutOfBoundsException("index " + j + " for prefix of length " + prefixLen.value());
        return prefix[j];
    }

    @Override
    public int fillPrefix(int[] dest) {
        int len = prefixLen.value();
        System.arraycopy(prefix, 0, dest, 0, len);
        return len;
    }

    @Override
    public boolean isCandidate(int a) {
        if (a <= 0 || a >= nSymbols)
            return false;
        int d = prefixLen.value();
        int p = posOf[d][a];
        return p < nFrontier[d].value() && frontier[d][p] == a;
    }

    @Override
    public int nCandidates() {
        return nFrontier[prefixLen.value()].value();
    }

    @Override
    public int fillCandidates(int[] dest) {
        int d = prefixLen.value();
        int n = nFrontier[d].value();
        System.arraycopy(frontier[d], 0, dest, 0, n);
        return n;
    }

    @Override
    public boolean canTerminate() {
        return status.value() != FORBIDDEN;
    }

    @Override
    public boolean isTerminated() {
        return status.value() == TERMINATED;
    }

    @Override
    public boolean isFixed() {
        return isTerminated();
    }

    @Override
    public int support() {
        return psdbSize.value();
    }

    @Override
    public int supportAfter(int a) {
        if (a <= 0 || a >= nSymbols)
            return 0;
        return supportCounter[prefixLen.value()][a];
    }

    @Override
    public int completeSupport() {
        // the subsequence policy is entirely anti-monotone
        return support();
    }

    @Override
    public void append(int a) {
        if (!isCandidate(a))
            throw new InconsistencyException();
        prefix[prefixLen.value()] = a;
        prefixLen.increment();
        int nSupported = projectSDB(a);
        if (status.value() == FORBIDDEN)
            status.setValue(POSSIBLE); // the forbidden prefix has been strictly extended
        refillFrontier(prefixLen.value(), tmpSymbols, nSupported);
        scheduleAll(onDomain);
        if (nSupported == 0)
            autoTerminate();
    }

    @Override
    public void excludeNext(int a) {
        if (isTerminated())
            return;
        if (!isCandidate(a))
            return;
        int d = prefixLen.value();
        int n = nFrontier[d].value();
        int p = posOf[d][a];
        int lastSymbol = frontier[d][n - 1];
        frontier[d][p] = lastSymbol;
        posOf[d][lastSymbol] = p;
        frontier[d][n - 1] = a;
        posOf[d][a] = n - 1;
        nFrontier[d].setValue(n - 1);
        scheduleAll(onDomain);
        if (n - 1 == 0)
            autoTerminate();
    }

    @Override
    public void terminate() {
        if (status.value() == FORBIDDEN)
            throw new InconsistencyException();
        if (status.value() == TERMINATED)
            return;
        status.setValue(TERMINATED);
        nFrontier[prefixLen.value()].setValue(0);
        scheduleAll(onDomain);
        scheduleAll(onFix);
    }

    @Override
    public void forbidTerminate() {
        if (status.value() == TERMINATED)
            throw new InconsistencyException();
        if (nFrontier[prefixLen.value()].value() == 0)
            throw new InconsistencyException();
        if (status.value() == FORBIDDEN)
            return;
        status.setValue(FORBIDDEN);
        scheduleAll(onDomain);
    }

    private void autoTerminate() {
        if (status.value() == FORBIDDEN)
            throw new InconsistencyException();
        status.setValue(TERMINATED);
        scheduleAll(onFix);
    }

    /**
     * Extends the pseudo-projected database with symbol a (PPIC projectSDB).
     * The new segment is appended after the current one and the reversible
     * start/size are moved to it; the support counters of the one-symbol
     * extensions of the new prefix are recomputed on the fly from the ordered
     * last-position lists, and the distinct supported symbols are collected in
     * {@code tmpSymbols} for the frontier refill.
     *
     * @return the number of distinct supported next symbols (in tmpSymbols)
     */
    private int projectSDB(int a) {
        int startInit = psdbStart.value();
        int sizeInit = psdbSize.value();
        int nbAddedTarget = db.symbolSupport(a); // at most this many sequences contain a
        int[] counter = supportCounter[prefixLen.value()];
        Arrays.fill(counter, 0);
        int nSupported = 0;
        int i = startInit;
        int j = startInit + sizeInit;
        int nbAdded = 0;
        while (i < startInit + sizeInit && nbAdded < nbAddedTarget) {
            int sid = psdbSeqId[i];
            int pos = psdbPosInSeq[i];
            int last = db.lastPos(sid, a);
            if (last != 0 && last - 1 >= pos) {
                // a occurs in the projected suffix of sid
                nbAdded++;
                int[] ti = db.sequence(sid);
                if (pos == -1) {
                    pos = db.firstPos(sid, a) - 1;
                } else {
                    while (pos < ti.length && ti[pos] != a)
                        pos++;
                }
                if (j >= innerTrailSize)
                    growInnerTrail();
                psdbSeqId[j] = sid;
                psdbPosInSeq[j] = pos + 1;
                j++;
                int[] tiLast = db.lastPosList(sid);
                for (int c = 0; c < tiLast.length && tiLast[c] - 1 > pos; c++) {
                    int symbol = ti[tiLast[c] - 1];
                    if (counter[symbol]++ == 0)
                        tmpSymbols[nSupported++] = symbol;
                }
            }
            i++;
        }
        psdbStart.setValue(startInit + sizeInit);
        psdbSize.setValue(nbAdded);
        return nSupported;
    }

    private void growInnerTrail() {
        psdbSeqId = Arrays.copyOf(psdbSeqId, innerTrailSize * 2);
        psdbPosInSeq = Arrays.copyOf(psdbPosInSeq, innerTrailSize * 2);
        innerTrailSize *= 2;
    }

    protected void scheduleAll(StateStack<CPConstraint> constraints) {
        for (int i = 0; i < constraints.size(); i++)
            cp.schedule(constraints.get(i));
    }

    @Override
    public void propagateOnDomainChange(CPConstraint c) {
        onDomain.push(c);
    }

    @Override
    public void propagateOnFix(CPConstraint c) {
        onFix.push(c);
    }

    @Override
    public CPSolver getSolver() {
        return cp;
    }

    @Override
    public ModelProxy getModelProxy() {
        return getSolver().getModelProxy();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("⟨");
        int len = prefixLen.value();
        for (int j = 0; j < len; j++) {
            if (j > 0)
                sb.append(' ');
            sb.append(prefix[j]);
        }
        sb.append("⟩ +").append(nCandidates()).append(" candidates, support=").append(support());
        return sb.toString();
    }
}
