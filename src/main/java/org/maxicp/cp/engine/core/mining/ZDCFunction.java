/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

/**
 * Zero-Diagonal-Convex (ZDC) discriminative scoring functions over the
 * class-specific supports of a pattern: given the support p in the positive
 * class and n in the negative class, the score measures how discriminative
 * the pattern is. All functions are zero on the diagonal
 * {@code p / nTransP == n / nTransN} and convex on each side of it, which is
 * the property exploited by the {@code ZDC} propagator
 * (Aoga, PhD thesis, http://hdl.handle.net/2078.1/218062, formulae 3.16-3.18).
 */
public abstract class ZDCFunction {

    /**
     * Evaluates the score.
     *
     * @param p       the support of the pattern in the positive class
     * @param n       the support of the pattern in the negative class
     * @param nTransP the number of positive transactions
     * @param nTransN the number of negative transactions
     * @return the discriminative score
     */
    public abstract double eval(int p, int n, int nTransP, int nTransN);

    /**
     * Returns this function multiplied by a constant factor, typically used to
     * map fractional scores onto an integer variable.
     *
     * @param scale the scaling factor
     * @return the scaled function
     */
    public ZDCFunction scaled(double scale) {
        ZDCFunction base = this;
        return new ZDCFunction() {
            @Override
            public double eval(int p, int n, int nTransP, int nTransN) {
                return base.eval(p, n, nTransP, nTransN) * scale;
            }
        };
    }

    /**
     * The chi-squared statistic of the 2x2 contingency table (covered/uncovered
     * versus positive/negative). Maximum value is {@code nTransP + nTransN}.
     */
    public static final ZDCFunction CHI2 = new ZDCFunction() {
        @Override
        public double eval(int p, int n, int nTransP, int nTransN) {
            int nTrans = nTransP + nTransN;
            double cover = p + n;
            int pp = nTransP - p;
            int nn = nTransN - n;
            double a = cover / nTrans;
            double ap = a * nTransP;
            double an = a * nTransN;
            double b = (nTrans - cover) / nTrans;
            double bp = b * nTransP;
            double bn = b * nTransN;
            double x1 = 0, x2 = 0, x3 = 0, x4 = 0;
            if (a != 0) {
                x1 = (p - ap) * (p - ap) / ap;
                x2 = (n - an) * (n - an) / an;
            }
            if (b != 0) {
                x3 = (pp - bp) * (pp - bp) / bp;
                x4 = (nn - bn) * (nn - bn) / bn;
            }
            return x1 + x2 + x3 + x4;
        }
    };

    /**
     * The information gain of splitting the database on pattern coverage.
     */
    public static final ZDCFunction INF_GAIN = new ZDCFunction() {
        private double nlogn(double x) {
            return x > 0.0 ? -Math.log(x) * x : 0.0;
        }

        @Override
        public double eval(int p, int n, int nTransP, int nTransN) {
            double nTrans = nTransP + nTransN;
            double covered = p + n;
            double notCovered = nTrans - covered;
            double before = 0.0;
            if (nTrans != 0)
                before = nlogn(nTransP / nTrans) + nlogn(nTransN / nTrans);
            double after = 0.0;
            if (covered != 0)
                after += covered * (nlogn(p / covered) + nlogn(n / covered));
            if (notCovered != 0)
                after += notCovered * (nlogn((nTransP - p) / notCovered) + nlogn((nTransN - n) / notCovered));
            return before - after / nTrans;
        }
    };

    /**
     * The Gini impurity reduction of splitting the database on pattern coverage.
     */
    public static final ZDCFunction GINI = new ZDCFunction() {
        @Override
        public double eval(int p, int n, int nTransP, int nTransN) {
            double nTrans = nTransP + nTransN;
            double covered = p + n;
            double notCovered = nTrans - covered;
            double before = 0.0;
            if (nTrans != 0)
                before = 1.0
                        - ((nTransP / nTrans) * (nTransP / nTrans))
                        - ((nTransN / nTrans) * (nTransN / nTrans));
            double after = 0.0;
            if (covered != 0)
                after = covered * (1.0
                        - ((p / covered) * (p / covered))
                        - ((n / covered) * (n / covered)));
            if (notCovered != 0)
                after += notCovered * (1.0
                        - (((nTransP - p) / notCovered) * ((nTransP - p) / notCovered))
                        - (((nTransN - n) / notCovered) * ((nTransN - n) / notCovered)));
            return before - after / nTrans;
        }
    };

    /**
     * The Fisher score of the coverage split.
     */
    public static final ZDCFunction FISHER = new ZDCFunction() {
        @Override
        public double eval(int p, int n, int nTransP, int nTransN) {
            double nTrans = nTransP + nTransN;
            double covered = n + p;
            double mu = covered / nTrans;
            double muP = p * 1.0 / nTransP;
            double muN = n * 1.0 / nTransN;
            double numer = p * (muP - mu) * (muP - mu) + n * (muN - mu) * (muN - mu);
            double denom = (p - (nTransP * muP * muP)) + (n - (nTransN * muN * muN));
            if (denom == 0)
                return numer == 0 ? 0 : Double.POSITIVE_INFINITY;
            return numer / denom;
        }
    };
}
