/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.cp.engine.core.mining.TimeOption;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateLong;
import org.maxicp.state.StateManager;
import org.maxicp.state.datastructures.StateSparseSet;
import org.maxicp.state.datastructures.StateStack;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;

/**
 * Time-constrained implementation of a sequence pattern variable.
 * <p>
 * The occurrence kernel is a port of the PPICt embedding-window pseudo-projection
 * algorithm (Aoga, Guns, Schaus, Mining Time-constrained Sequential Patterns with
 * Constraint Programming, Constraints 22:548-570, 2017; OscarML PPICt.scala).
 * An occurrence of the prefix in a sequence is an embedding whose consecutive
 * matched events are separated by a gap in {@code [effectiveMingap, maxgap]} and
 * whose total span does not exceed {@code maxspan} (see {@link TimeOption}).
 * These three restrictions are anti-monotone under prefix extension, so
 * {@link #support()} and {@link #supportAfter(int)} count them and the candidate
 * frontier contains exactly the symbols whose projected support is positive.
 * The minimum span is not anti-monotone and never prunes the frontier: it is
 * only accounted for by {@link #completeSupport()}, which counts the sequences
 * with an embedding of span at least {@code minspan} and decides whether the
 * pattern may terminate at the current prefix.
 * <p>
 * The pseudo-projected database is stored in grow-only parallel arrays: for each
 * projected sequence, its id and the list of embedding windows (first and last
 * matched positions of every valid embedding of the prefix, dominated embeddings
 * excluded). Two reversible integers delimit the segment of the current
 * projection, so backtracking simply moves them back and the slot contents are
 * overwritten by later projections. Extending the projection with a symbol scans,
 * for each embedding window (first, end), the positions
 * {@code u in [nextPosGap(end), min(time(end)+maxgap, time(first)+maxspan)]}
 * (precomputed {@code nextPosGap} gives the first position at gap at least
 * {@code effectiveMingap}); the support counters of the one-symbol extensions are
 * maintained on the fly over the same windows with a per-sequence visited flag.
 * <p>
 * Dominance rule: within a projected sequence the embeddings are kept sorted by
 * strictly increasing end position (and non-decreasing first position). When
 * extending embedding k, the scan is capped at
 * {@code time(end_{k+1}) + effectiveMingap - 1}: a later extension of embedding k
 * is dominated by the same extension of embedding k+1, whose first position is
 * not earlier (its remaining span window is at least as large). This differs
 * slightly from the OscarML code, which caps at {@code time(end_{k+1})} under a
 * span guard: both coincide for {@code effectiveMingap == 1} (the common case,
 * including {@code mingap == 0} which is normalized to 1), and the present cap
 * keeps the projected supports exact for any {@code mingap}. Dominated
 * embeddings never affect the anti-monotone supports; {@link #completeSupport()}
 * therefore recomputes the minimum-span check on the original sequences of the
 * current projection instead of relying on the stored embeddings.
 */
public class CPSeqPatternVarTimedImpl implements CPSeqPatternVar {

    private final CPSolver cp;
    private final SequenceDb db;
    private final int nSymbols;
    private final int nSeqs;
    private final TimeOption timeOption;
    private final int minspan;
    private final int maxspan;
    private final int effMingap;
    private final int maxgap;

    private final int[][] times; // times[s] = timestamps of the events of sequence s
    private final int[][] nextPosGap; // nextPosGap[s][j] = first u >= j with time(u) >= time(j) + effMingap

    private final int[] prefix;
    private final StateInt prefixLen;

    private final StateSparseSet[] frontier; // frontier[d] = candidates when prefixLen == d
    private final StateInt status;

    // PPICt pseudo-projected database: grow-only trail of (sequence id, embedding windows)
    private int innerTrailSize;
    private int[] sids;
    private int[] embSize; // number of embeddings of the prefix in the sequence of the slot
    private int[][] embsFirst; // embsFirst[slot][k] = first matched position of embedding k
    private int[][] embsEnd; // embsEnd[slot][k] = last matched position of embedding k
    private final StateInt psdbStart; // start of the current projection segment
    private final StateInt psdbSize; // number of sequences in the current projection

    // supportCounter[a] = support of prefix·a, valid for the projection stamped cachedVersion
    private final int[] supportCounter;
    private final boolean[] visited; // per-sequence visited flags for support counting
    private final StateLong projVersion; // version of the projection of the current node
    private long versionCounter = 0;
    private long cachedVersion = 0;

    // cache of completeSupport(), valid for the projection stamped completeCacheVersion
    private long completeCacheVersion = -1;
    private int completeCache;
    private boolean[] dpCur; // scratch buffers of the minimum-span check
    private boolean[] dpNext;

    private final StateStack<CPConstraint> onDomain;
    private final StateStack<CPConstraint> onFix;

    private final int[] tmpSymbols;

    /**
     * Creates a time-constrained sequence pattern variable with an empty prefix.
     *
     * @param cp         the solver in which the variable is created
     * @param db         the timed sequence database over which the pattern is mined
     * @param timeOption the time restrictions of the occurrence policy
     */
    public CPSeqPatternVarTimedImpl(CPSolver cp, SequenceDb db, TimeOption timeOption) {
        if (!db.hasTimes())
            throw new IllegalArgumentException("database " + db.name() + " has no timestamps");
        this.cp = cp;
        this.db = db;
        this.nSymbols = db.nSymbols();
        this.nSeqs = db.nSeqs();
        this.timeOption = timeOption;
        this.minspan = timeOption.minspan();
        this.maxspan = timeOption.maxspan();
        this.effMingap = timeOption.effectiveMingap();
        this.maxgap = timeOption.maxgap();
        this.times = new int[nSeqs][];
        for (int s = 0; s < nSeqs; s++)
            times[s] = db.times(s);
        this.nextPosGap = db.nextPosGap(effMingap);
        StateManager sm = cp.getStateManager();
        prefix = new int[db.maxSeqLength()];
        prefixLen = sm.makeStateInt(0);
        frontier = new StateSparseSet[db.maxSeqLength() + 1];
        for (int d = 0; d < frontier.length; d++)
            frontier[d] = new StateSparseSet(sm, nSymbols - 1, 1);
        status = sm.makeStateInt(POSSIBLE);
        innerTrailSize = Math.max(nSeqs * 5, 4);
        sids = new int[innerTrailSize];
        embSize = new int[innerTrailSize];
        embsFirst = new int[innerTrailSize][];
        embsEnd = new int[innerTrailSize][];
        psdbStart = sm.makeStateInt(0);
        psdbSize = sm.makeStateInt(nSeqs); // virtual root segment: every sequence, no embedding
        projVersion = sm.makeStateLong(0L);
        supportCounter = new int[nSymbols];
        for (int a = 1; a < nSymbols; a++)
            supportCounter[a] = db.symbolSupport(a); // a single event always satisfies the anti-monotone policy
        visited = new boolean[nSymbols];
        onDomain = new StateStack<>(sm);
        onFix = new StateStack<>(sm);
        tmpSymbols = new int[Math.max(nSymbols - 1, 1)];
        for (int a = 1; a < nSymbols; a++)
            if (db.symbolSupport(a) == 0)
                frontier[0].remove(a);
        if (frontier[0].isEmpty())
            autoTerminate(); // the empty pattern is the only possible value: it must occur
    }

    @Override
    public SequenceDb database() {
        return db;
    }

    /**
     * Returns the time restrictions of the occurrence policy.
     *
     * @return the time restrictions
     */
    public TimeOption timeOption() {
        return timeOption;
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
    public int completeSupport() {
        if (minspan <= 0)
            return support(); // every embedding has a non-negative span
        int len = prefixLen.value();
        if (len <= 1)
            return 0; // an empty or single-event embedding has span 0 < minspan
        if (completeCacheVersion != projVersion.value()) {
            int count = 0;
            int start = psdbStart.value();
            int end = start + psdbSize.value();
            for (int c = start; c < end; c++)
                if (hasMinSpanEmbedding(sids[c]))
                    count++;
            completeCache = count;
            completeCacheVersion = projVersion.value();
        }
        return completeCache;
    }

    @Override
    public void append(int a) {
        if (!isCandidate(a))
            throw new InconsistencyException();
        int depth = prefixLen.value();
        prefix[depth] = a;
        prefixLen.increment();
        projectSDB(a, depth);
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
        if (completeSupport() == 0)
            throw new InconsistencyException(); // the pattern must occur under the complete policy
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
        if (completeSupport() == 0)
            throw new InconsistencyException(); // no completion nor valid termination is possible
        status.setValue(TERMINATED);
        scheduleAll(onFix);
    }

    /**
     * Extends the pseudo-projected database with symbol a (PPICt projectSDB).
     * The new segment is appended after the current one and the reversible
     * start/size are moved to it. At depth 0 the initial embeddings of a are
     * collected over the whole sequences; deeper, each stored embedding window
     * is extended within its gap/span window. The support counters of the
     * one-symbol extensions of the new prefix are maintained on the fly.
     *
     * @param a     the appended symbol
     * @param depth the length of the prefix before the append
     */
    private void projectSDB(int a, int depth) {
        int startInit = psdbStart.value();
        int sizeInit = psdbSize.value();
        Arrays.fill(supportCounter, 0);
        int j = startInit + sizeInit;
        int nbAdded = 0;
        if (depth == 0) {
            for (int sid = 0; sid < nSeqs; sid++) {
                int pLast = db.lastPos(sid, a); // 1-based last occurrence, 0 if absent
                if (pLast == 0)
                    continue;
                if (j == innerTrailSize)
                    growInnerTrail();
                int[] seq = db.sequence(sid);
                Arrays.fill(visited, false);
                int nEmb = 0;
                int curPos = 0;
                boolean done = false;
                for (int pos = 0; !done && pos < pLast; pos++) {
                    if (seq[pos] == a) {
                        ensureEmbCapacity(j, nEmb);
                        embsFirst[j][nEmb] = pos;
                        embsEnd[j][nEmb] = pos;
                        nEmb++;
                        curPos = updateSupport(sid, pos, pos, curPos);
                        if (curPos >= seq.length)
                            done = true; // later embeddings are dominated: this one reaches the end of the sequence
                    }
                }
                sids[j] = sid;
                embSize[j] = nEmb;
                nbAdded++;
                j++;
            }
        } else {
            for (int c = startInit; c < startInit + sizeInit; c++) {
                int sid = sids[c];
                int pLast = db.lastPos(sid, a);
                if (embsEnd[c][0] >= pLast) // no occurrence of a after the earliest embedding end
                    continue;
                if (j == innerTrailSize)
                    growInnerTrail();
                int[] seq = db.sequence(sid);
                int[] t = times[sid];
                Arrays.fill(visited, false);
                int nEmb = 0;
                int curPos = 0;
                boolean done = false;
                int nWindows = embSize[c];
                for (int k = 0; !done && k < nWindows && embsEnd[c][k] < pLast; k++) {
                    int first = embsFirst[c][k];
                    int end = embsEnd[c][k];
                    long maxTime = Math.min((long) t[end] + maxgap, (long) t[first] + maxspan);
                    if (k + 1 < nWindows) // dominance: from there on, extending embedding k+1 is at least as good
                        maxTime = Math.min(maxTime, (long) t[embsEnd[c][k + 1]] + effMingap - 1);
                    for (int u = nextPosGap[sid][end]; !done && u < pLast && t[u] <= maxTime; u++) {
                        if (seq[u] == a) {
                            ensureEmbCapacity(j, nEmb);
                            embsFirst[j][nEmb] = first;
                            embsEnd[j][nEmb] = u;
                            nEmb++;
                            curPos = updateSupport(sid, first, u, curPos);
                            if (curPos >= seq.length)
                                done = true;
                        }
                    }
                }
                if (nEmb > 0) {
                    sids[j] = sid;
                    embSize[j] = nEmb;
                    nbAdded++;
                    j++;
                }
            }
        }
        psdbStart.setValue(startInit + sizeInit);
        psdbSize.setValue(nbAdded);
        versionCounter++;
        projVersion.setValue(versionCounter);
        cachedVersion = versionCounter;
    }

    /**
     * Counts, in the support counters, the distinct symbols occurring in the
     * extension window of an embedding (first, end):
     * {@code u in [nextPosGap(end), min(time(end)+maxgap, time(first)+maxspan)]}.
     * The scan starts after the stopping point of the previous embedding of the
     * same sequence (the windows are processed by non-decreasing end time, so
     * skipped positions have already been visited) and per-sequence visited
     * flags prevent counting a symbol twice for the same sequence.
     *
     * @param sid   the sequence
     * @param first the first matched position of the embedding
     * @param end   the last matched position of the embedding
     * @param from  the stopping point of the scan of the previous embedding
     * @return the stopping point of the scan
     */
    private int updateSupport(int sid, int first, int end, int from) {
        int[] seq = db.sequence(sid);
        int[] t = times[sid];
        int u = Math.max(nextPosGap[sid][end], from);
        long maxTime = Math.min((long) t[end] + maxgap, (long) t[first] + maxspan);
        int len = seq.length;
        while (u < len && t[u] <= maxTime) {
            int item = seq[u];
            if (!visited[item]) {
                visited[item] = true;
                supportCounter[item]++;
            }
            u++;
        }
        return u;
    }

    /**
     * Recomputes the support counters from the embedding windows of the current
     * projection segment, needed after backtracking since the counters are not
     * reversible. Replays exactly the counting of {@link #projectSDB(int, int)}.
     */
    private void recomputeSupportCounter() {
        Arrays.fill(supportCounter, 0);
        if (prefixLen.value() == 0) {
            for (int a = 1; a < nSymbols; a++)
                supportCounter[a] = db.symbolSupport(a);
            return;
        }
        int start = psdbStart.value();
        int end = start + psdbSize.value();
        for (int c = start; c < end; c++) {
            int sid = sids[c];
            Arrays.fill(visited, false);
            int curPos = 0;
            int n = embSize[c];
            for (int k = 0; k < n; k++)
                curPos = updateSupport(sid, embsFirst[c][k], embsEnd[c][k], curPos);
        }
    }

    /**
     * Returns whether a sequence contains an embedding of the current prefix
     * satisfying the complete policy, i.e. the gap/span windows and additionally
     * a span of at least minspan. Since the stored embedding windows exclude
     * dominated embeddings (whose first positions may be earlier, hence with
     * larger spans), the check runs on the original sequence: for every first
     * matched position, a reachability sweep over the pattern positions collects
     * the feasible last matched positions, and the span of the farthest one is
     * compared to minspan. Only called when {@code prefixLength() >= 2} and
     * {@code minspan > 0}.
     */
    private boolean hasMinSpanEmbedding(int sid) {
        int len = prefixLen.value();
        int[] seq = db.sequence(sid);
        int[] t = times[sid];
        int seqLen = seq.length;
        if (dpCur == null || dpCur.length < seqLen) {
            dpCur = new boolean[db.maxSeqLength()];
            dpNext = new boolean[db.maxSeqLength()];
        }
        boolean[] cur = dpCur;
        boolean[] next = dpNext;
        for (int p1 = 0; p1 < seqLen; p1++) {
            if (seq[p1] != prefix[0] || (long) t[seqLen - 1] - t[p1] < minspan)
                continue; // even the last event of the sequence is too close
            long spanCap = (long) t[p1] + maxspan;
            Arrays.fill(cur, 0, seqLen, false);
            cur[p1] = true;
            boolean any = true;
            for (int i = 1; i < len && any; i++) {
                Arrays.fill(next, 0, seqLen, false);
                any = false;
                int a = prefix[i];
                for (int p = p1; p < seqLen; p++) {
                    if (!cur[p])
                        continue;
                    long maxTime = Math.min((long) t[p] + maxgap, spanCap);
                    for (int u = nextPosGap[sid][p]; u < seqLen && t[u] <= maxTime; u++) {
                        if (seq[u] == a) {
                            next[u] = true;
                            any = true;
                        }
                    }
                }
                boolean[] tmp = cur;
                cur = next;
                next = tmp;
            }
            if (!any)
                continue;
            for (int p = p1; p < seqLen; p++)
                if (cur[p] && (long) t[p] - t[p1] >= minspan)
                    return true;
        }
        return false;
    }

    private void ensureEmbCapacity(int slot, int nEmb) {
        if (embsFirst[slot] == null) {
            embsFirst[slot] = new int[4];
            embsEnd[slot] = new int[4];
        } else if (nEmb == embsFirst[slot].length) {
            embsFirst[slot] = Arrays.copyOf(embsFirst[slot], nEmb * 2);
            embsEnd[slot] = Arrays.copyOf(embsEnd[slot], nEmb * 2);
        }
    }

    private void growInnerTrail() {
        sids = Arrays.copyOf(sids, innerTrailSize * 2);
        embSize = Arrays.copyOf(embSize, innerTrailSize * 2);
        embsFirst = Arrays.copyOf(embsFirst, innerTrailSize * 2);
        embsEnd = Arrays.copyOf(embsEnd, innerTrailSize * 2);
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
