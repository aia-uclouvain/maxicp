/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateLong;
import org.maxicp.state.StateManager;
import org.maxicp.state.datastructures.StateSparseSet;
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
 * The candidate frontier is one {@link StateSparseSet} per prefix depth:
 * appending a symbol moves to the next depth whose set is full (all removals
 * at deeper depths having been undone by backtracking) and restricts it to the
 * symbols with a positive projected support. This makes every frontier update
 * removal-only while giving each prefix its own frontier, which is required
 * for the append/exclude search to enumerate every pattern exactly once.
 */
public class CPSeqPatternVarImpl implements CPSeqPatternVar {

    private final CPSolver cp;
    private final SequenceDb db;
    private final int nSymbols;

    private final int[] prefix;
    private final StateInt prefixLen;

    private final StateSparseSet[] frontier; // frontier[d] = candidates when prefixLen == d
    private final StateInt status;

    // PPIC pseudo-projected database: grow-only trail of (sequence id, position) pairs
    private int innerTrailSize;
    private int[] psdbSeqId;
    private int[] psdbPosInSeq; // 0-based start of the projected suffix, -1 for the whole sequence
    private final StateInt psdbStart; // start of the current projection segment
    private final StateInt psdbSize; // number of sequences in the current projection

    // supportCounter[a] = support of prefix·a, valid for the projection stamped cachedVersion
    private final int[] supportCounter;
    private final StateLong projVersion; // version of the projection of the current node
    private long versionCounter = 0;
    private long cachedVersion = 0;

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
        frontier = new StateSparseSet[db.maxSeqLength() + 1];
        for (int d = 0; d < frontier.length; d++)
            frontier[d] = new StateSparseSet(sm, nSymbols - 1, 1);
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
        projVersion = sm.makeStateLong(0L);
        supportCounter = new int[nSymbols];
        for (int a = 1; a < nSymbols; a++)
            supportCounter[a] = db.symbolSupport(a);
        onDomain = new StateStack<>(sm);
        onFix = new StateStack<>(sm);
        tmpSymbols = new int[Math.max(nSymbols - 1, 1)];
        for (int a = 1; a < nSymbols; a++)
            if (db.symbolSupport(a) == 0)
                frontier[0].remove(a);
        if (frontier[0].isEmpty())
            status.setValue(TERMINATED); // the empty pattern is the only possible value
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
        return frontier[prefixLen.value()].contains(a);
    }

    @Override
    public int nCandidates() {
        return frontier[prefixLen.value()].size();
    }

    @Override
    public int fillCandidates(int[] dest) {
        return frontier[prefixLen.value()].fillArray(dest);
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
        // recompute the counters if the projection changed since they were last computed
        if (cachedVersion != projVersion.value()) {
            recomputeSupportCounter();
            cachedVersion = projVersion.value();
        }
        return supportCounter[a];
    }

    @Override
    public void append(int a) {
        if (!isCandidate(a))
            throw new InconsistencyException();
        prefix[prefixLen.value()] = a;
        prefixLen.increment();
        projectSDB(a);
        if (status.value() == FORBIDDEN)
            status.setValue(POSSIBLE); // the forbidden prefix has been strictly extended
        StateSparseSet next = frontier[prefixLen.value()];
        int n = next.fillArray(tmpSymbols);
        for (int i = 0; i < n; i++)
            if (supportCounter[tmpSymbols[i]] == 0)
                next.remove(tmpSymbols[i]);
        scheduleAll(onDomain);
        if (next.isEmpty())
            autoTerminate();
    }

    @Override
    public void excludeNext(int a) {
        if (isTerminated())
            return;
        StateSparseSet f = frontier[prefixLen.value()];
        if (!f.remove(a))
            return;
        scheduleAll(onDomain);
        if (f.isEmpty())
            autoTerminate();
    }

    @Override
    public void terminate() {
        if (status.value() == FORBIDDEN)
            throw new InconsistencyException();
        if (status.value() == TERMINATED)
            return;
        status.setValue(TERMINATED);
        StateSparseSet f = frontier[prefixLen.value()];
        if (!f.isEmpty())
            f.removeAll();
        scheduleAll(onDomain);
        scheduleAll(onFix);
    }

    @Override
    public void forbidTerminate() {
        if (status.value() == TERMINATED)
            throw new InconsistencyException();
        if (frontier[prefixLen.value()].isEmpty())
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
     * last-position lists.
     */
    private void projectSDB(int a) {
        int startInit = psdbStart.value();
        int sizeInit = psdbSize.value();
        int nbAddedTarget = db.symbolSupport(a); // at most this many sequences contain a
        Arrays.fill(supportCounter, 0);
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
                for (int c = 0; c < tiLast.length && tiLast[c] - 1 > pos; c++)
                    supportCounter[ti[tiLast[c] - 1]]++;
            }
            i++;
        }
        psdbStart.setValue(startInit + sizeInit);
        psdbSize.setValue(nbAdded);
        versionCounter++;
        projVersion.setValue(versionCounter);
        cachedVersion = versionCounter;
    }

    /**
     * Recomputes the support counters from the current projection segment,
     * needed after backtracking since the counters are not reversible.
     */
    private void recomputeSupportCounter() {
        Arrays.fill(supportCounter, 0);
        int start = psdbStart.value();
        int end = start + psdbSize.value();
        for (int i = start; i < end; i++) {
            int sid = psdbSeqId[i];
            int startPos = psdbPosInSeq[i];
            int[] ti = db.sequence(sid);
            int[] tiLast = db.lastPosList(sid);
            for (int c = 0; c < tiLast.length && tiLast[c] - 1 >= startPos; c++)
                supportCounter[ti[tiLast[c] - 1]]++;
        }
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
