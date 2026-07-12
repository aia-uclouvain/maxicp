/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

/**
 * PrefixProjection/PPIC global constraint over native positional variables:
 * the pattern is an array of symbol variables padded with the epsilon symbol 0,
 * and every pattern must be a subsequence of at least theta database sequences
 * (Aoga, Guns, Schaus, An efficient algorithm for mining frequent sequences
 * with CP, ECML-PKDD 2016).
 * <p>
 * This is the native-variable (NV-GC) counterpart of the pattern-variable model
 * based on {@link org.maxicp.cp.engine.core.CPSeqPatternVar}: the pseudo-projected
 * database is owned by this constraint and the search assigns the positions in
 * prefix order. It serves as experimental baseline; both use the same
 * projection algorithm and database indexes.
 */
public class PrefixProjection extends AbstractCPConstraint {

    private static final int EPSILON = 0;

    private final CPIntVar[] p;
    private final int theta;
    private final SequenceDb db;

    // pseudo-projected database: grow-only parallel arrays + reversible window
    private int[] psdbSeqId;
    private int[] psdbPosInSeq;
    private int innerTrailSize;
    private final StateInt psdbStart;
    private final StateInt psdbSize;

    private final int[] supportCounter; // valid only right after a projection
    private final StateInt curPosInP;
    private final int[] dom;

    /**
     * Creates a PrefixProjection minimum-support constraint.
     *
     * @param p     the positional pattern variables, domains over
     *              {@code {0} ∪ {1..db.nSymbols()-1}} with 0 the epsilon padding;
     *              the search must assign them in index order
     * @param theta the minimum number of covering sequences, {@code theta >= 1}
     * @param db    the sequence database
     */
    public PrefixProjection(CPIntVar[] p, int theta, SequenceDb db) {
        super(p[0].getSolver());
        if (theta < 1)
            throw new IllegalArgumentException("minimum support must be at least 1, got " + theta);
        this.p = p;
        this.theta = theta;
        this.db = db;
        this.innerTrailSize = db.nSeqs() * 5;
        this.psdbSeqId = new int[innerTrailSize];
        this.psdbPosInSeq = new int[innerTrailSize];
        for (int i = 0; i < db.nSeqs(); i++) {
            psdbSeqId[i] = i;
            psdbPosInSeq[i] = -1;
        }
        this.psdbStart = getSolver().getStateManager().makeStateInt(0);
        this.psdbSize = getSolver().getStateManager().makeStateInt(db.nSeqs());
        this.supportCounter = new int[db.nSymbols()];
        this.curPosInP = getSolver().getStateManager().makeStateInt(0);
        this.dom = new int[db.nSymbols() + 1];
    }

    /**
     * Returns the support of the currently projected prefix; on a leaf this is
     * the support of the complete pattern.
     *
     * @return the number of sequences containing the committed prefix
     */
    public int patternSupport() {
        return psdbSize.value();
    }

    @Override
    public void post() {
        if (db.nSeqs() < theta) {
            throw new InconsistencyException();
        }
        // globally infrequent symbols can never appear at any position
        for (CPIntVar position : p) {
            for (int a = 1; a < db.nSymbols(); a++) {
                if (db.symbolSupport(a) < theta) {
                    position.remove(a);
                }
            }
        }
        for (CPIntVar position : p) {
            if (!position.isFixed()) {
                position.propagateOnFix(this);
            }
        }
        propagate();
    }

    @Override
    public void propagate() {
        int v = curPosInP.value();
        if (p[v].isFixed() && p[v].min() == EPSILON) {
            if (v == 0 || !(p[v - 1].isFixed() && p[v - 1].min() == EPSILON)) {
                enforceEpsilonFrom(v);
            }
        } else {
            while (v < p.length && p[v].isFixed() && p[v].min() != EPSILON) {
                if (!filterPrefixProjection(p[v].min())) {
                    throw new InconsistencyException();
                }
                curPosInP.increment();
                v = curPosInP.value();
            }
            if (v > 0 && v < p.length && p[v].isFixed() && p[v].min() == EPSILON) {
                enforceEpsilonFrom(v);
            }
        }
    }

    private void enforceEpsilonFrom(int i) {
        for (int j = i; j < p.length; j++) {
            p[j].fix(EPSILON);
        }
    }

    private boolean filterPrefixProjection(int prefix) {
        int i = curPosInP.value() + 1;
        int support = projectSDB(prefix);
        if (support < theta) {
            return false;
        }
        prune(i);
        return true;
    }

    /**
     * Removes from the next position the symbols that are infrequent in the
     * projected database.
     */
    private void prune(int i) {
        if (i >= p.length)
            return;
        int len = p[i].fillArray(dom);
        for (int k = 0; k < len; k++) {
            int symbol = dom[k];
            if (symbol != EPSILON && supportCounter[symbol] < theta) {
                p[i].remove(symbol);
            }
        }
    }

    /**
     * Projects the pseudo-projected database on a symbol: advances every active
     * sequence to just after its next occurrence of the symbol, appends the
     * surviving entries after the current window (grow-only trail) and
     * recomputes the projected support of every symbol from the last-position
     * lists.
     *
     * @return the support of the extended prefix
     */
    private int projectSDB(int prefix) {
        int startInit = psdbStart.value();
        int sizeInit = psdbSize.value();
        int nbAddedTarget = db.symbolSupport(prefix);
        java.util.Arrays.fill(supportCounter, 0);

        int j = startInit + sizeInit;
        int support = 0;
        int nbAdded = 0;
        for (int i = startInit; i < startInit + sizeInit && nbAdded < nbAddedTarget; i++) {
            int sid = psdbSeqId[i];
            int[] sequence = db.sequence(sid);
            int start = psdbPosInSeq[i];
            // the prefix symbol must occur in the sequence (lastPos 0 means absent,
            // which the position test alone misses for unprojected entries) and
            // still occur after the current projection point
            if (db.lastPos(sid, prefix) != 0 && db.lastPos(sid, prefix) - 1 >= start) {
                nbAdded++;
                int pos;
                if (start == -1) {
                    pos = db.firstPos(sid, prefix) - 1;
                } else {
                    pos = start;
                    while (pos < sequence.length && sequence[pos] != prefix) {
                        pos++;
                    }
                }
                if (j >= innerTrailSize) {
                    growInnerTrail();
                }
                psdbSeqId[j] = sid;
                psdbPosInSeq[j] = pos + 1;
                j++;
                support++;
                // every symbol whose last position lies after the projection point
                // has at least one occurrence in the projected suffix
                int[] lastPositions = db.lastPosList(sid);
                for (int c = 0; c < lastPositions.length && lastPositions[c] - 1 > pos; c++) {
                    supportCounter[sequence[lastPositions[c] - 1]]++;
                }
            }
        }
        psdbStart.setValue(startInit + sizeInit);
        psdbSize.setValue(support);
        return support;
    }

    private void growInnerTrail() {
        int[] newSeqId = new int[innerTrailSize * 2];
        int[] newPosInSeq = new int[innerTrailSize * 2];
        System.arraycopy(psdbSeqId, 0, newSeqId, 0, innerTrailSize);
        System.arraycopy(psdbPosInSeq, 0, newPosInSeq, 0, innerTrailSize);
        psdbSeqId = newSeqId;
        psdbPosInSeq = newPosInSeq;
        innerTrailSize *= 2;
    }
}
