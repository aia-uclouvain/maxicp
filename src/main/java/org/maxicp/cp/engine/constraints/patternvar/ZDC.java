/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.mining.ZDCFunction;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Zero-Diagonal-Convex discriminative score constraint:
 * {@code score = (int) f(p, n)} where p and n are the class-specific supports
 * of a pattern (Aoga, PhD thesis, http://hdl.handle.net/2078.1/218062, §3.16-3.18).
 * <p>
 * The filtering exploits that f is zero on the diagonal and convex on each side:
 * the maximum of f over the box {@code [pmin,pmax] x [nmin,nmax]} is reached at
 * the corners (pmax, nmin) or (pmin, nmax), and the bounds of p and n can be
 * tightened by binary search when whole edges of the box are below the required
 * minimum score. The score is expressed in integer units, so f is typically a
 * {@link ZDCFunction#scaled(double)} function.
 */
public class ZDC extends AbstractCPConstraint {

    private final CPIntVar p;
    private final CPIntVar n;
    private final int nTransP;
    private final int nTransN;
    private final ZDCFunction f;
    private final CPIntVar score;

    /**
     * Creates a ZDC score constraint.
     *
     * @param p       the support of the pattern in the positive class
     * @param n       the support of the pattern in the negative class
     * @param nTransP the number of positive transactions
     * @param nTransN the number of negative transactions
     * @param f       the (typically scaled) ZDC scoring function
     * @param score   the integer variable representing the score
     */
    public ZDC(CPIntVar p, CPIntVar n, int nTransP, int nTransN, ZDCFunction f, CPIntVar score) {
        super(p.getSolver());
        this.p = p;
        this.n = n;
        this.nTransP = nTransP;
        this.nTransN = nTransN;
        this.f = f;
        this.score = score;
    }

    private double eval(int pValue, int nValue) {
        return f.eval(pValue, nValue, nTransP, nTransN);
    }

    @Override
    public void post() {
        p.propagateOnBoundChange(this);
        n.propagateOnBoundChange(this);
        score.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        int pMax = p.max();
        int pMin = p.min();
        int nMax = n.max();
        int nMin = n.min();

        double fPminNmax = eval(pMin, nMax);
        double fPmaxNmax = eval(pMax, nMax);
        double fPminNmin = eval(pMin, nMin);
        double fPmaxNmin = eval(pMax, nMin);

        int scoreMin = score.min();

        // the maximum over the box is at one of the two anti-diagonal corners
        if (fPminNmax < scoreMin && fPmaxNmin < scoreMin) {
            throw new InconsistencyException();
        }
        score.removeAbove((int) Math.max(fPmaxNmin, fPminNmax));

        // the minimum of the corner values is a valid lower bound when the box
        // lies entirely on one side of the diagonal (or is reduced to a point)
        double nMinCoef = nMin * 1.0 / nTransN;
        double nMaxCoef = nMax * 1.0 / nTransN;
        double pMinCoef = pMin * 1.0 / nTransP;
        double pMaxCoef = pMax * 1.0 / nTransP;
        if (((((nMaxCoef < pMinCoef) && (nMinCoef < pMaxCoef))
                || ((nMaxCoef > pMinCoef) && (nMinCoef > pMaxCoef)))
                && fPmaxNmin >= scoreMin && fPminNmax >= scoreMin)
                || (p.isFixed() && n.isFixed())) {
            score.removeBelow((int) Math.min(fPmaxNmin, fPminNmax));
        }

        // both bottom corners below the required score: grow n.min
        if (fPminNmin < scoreMin && fPmaxNmin < scoreMin) {
            int a = nMin;
            int b = nMax;
            while (b - a > 1) {
                int mid = a + (b - a) / 2;
                if (eval(pMin, mid) < scoreMin) {
                    a = mid;
                } else {
                    b = mid;
                }
            }
            n.removeBelow(b);
        }

        // both top corners below the required score: decrease n.max
        if (fPminNmax < scoreMin && fPmaxNmax < scoreMin) {
            int a = nMax;
            int b = nMin;
            while (a - b > 1) {
                int mid = a - (a - b) / 2;
                if (eval(pMax, mid) < scoreMin) {
                    a = mid;
                } else {
                    b = mid;
                }
            }
            n.removeAbove(b);
        }

        // both left corners below the required score: grow p.min
        if (fPminNmin < scoreMin && fPminNmax < scoreMin) {
            int a = pMin;
            int b = pMax;
            while (b - a > 1) {
                int mid = a + (b - a) / 2;
                if (eval(mid, nMin) < scoreMin) {
                    a = mid;
                } else {
                    b = mid;
                }
            }
            p.removeBelow(b);
        }

        // both right corners below the required score: decrease p.max
        if (fPmaxNmax < scoreMin && fPmaxNmin < scoreMin) {
            int a = pMax;
            int b = pMin;
            while (a - b > 1) {
                int mid = a - (a - b) / 2;
                if (eval(mid, nMax) < scoreMin) {
                    a = mid;
                } else {
                    b = mid;
                }
            }
            p.removeAbove(b);
        }
    }
}
