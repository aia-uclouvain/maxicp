/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

/**
 * Immutable time restrictions for time-constrained sequential pattern mining
 * (Aoga, Guns, Schaus, Mining Time-constrained Sequential Patterns with
 * Constraint Programming, Constraints 22:548-570, 2017).
 * <p>
 * For a pattern embedding matching events at times {@code t_1 <= ... <= t_k}:
 * <ul>
 *     <li>gap window: {@code mingap <= t_{i+1} - t_i <= maxgap} for every pair of
 *         consecutive matched events (all bounds inclusive). Following the PPICt
 *         convention, a minimum gap of 0 is interpreted as 1
 *         (see {@link #effectiveMingap()}): consecutive matched events must be
 *         strictly separated in time;</li>
 *     <li>span window: {@code minspan <= t_k - t_1 <= maxspan} for the whole
 *         embedding (both inclusive). Patterns of length 0 or 1 have span 0.</li>
 * </ul>
 * The gap bounds and the maximum span are anti-monotone under prefix extension
 * and can be used for support-based pruning; the minimum span is not (extending
 * a prefix can only increase the span) and only restricts the complete pattern.
 * <p>
 * Time arithmetic such as {@code t + maxgap} must be performed with 64-bit
 * (long) additions or clamped by the caller: {@link #unconstrained()} uses
 * {@link Integer#MAX_VALUE} bounds that overflow 32-bit additions.
 *
 * @param minspan the minimum span, non-negative
 * @param maxspan the maximum span, at least {@code minspan}
 * @param mingap  the minimum gap, non-negative (0 is interpreted as 1)
 * @param maxgap  the maximum gap, at least {@code mingap}
 */
public record TimeOption(int minspan, int maxspan, int mingap, int maxgap) {

    public TimeOption {
        if (minspan < 0)
            throw new IllegalArgumentException("minspan must be non-negative: " + minspan);
        if (maxspan < minspan)
            throw new IllegalArgumentException("maxspan " + maxspan + " must be at least minspan " + minspan);
        if (mingap < 0)
            throw new IllegalArgumentException("mingap must be non-negative: " + mingap);
        if (maxgap < mingap)
            throw new IllegalArgumentException("maxgap " + maxgap + " must be at least mingap " + mingap);
    }

    /**
     * Returns time restrictions that do not restrict anything: gaps and span
     * in {@code [0, Integer.MAX_VALUE]}. On a database whose events are at
     * distinct times this is equivalent to untimed subsequence matching.
     *
     * @return the unconstrained time restrictions
     */
    public static TimeOption unconstrained() {
        return new TimeOption(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
    }

    /**
     * Returns the effective minimum gap, i.e. {@code max(1, mingap)}: as in
     * PPICt, a minimum gap of 0 is interpreted as 1, so that two consecutive
     * matched events are always strictly separated in time.
     *
     * @return the effective minimum gap
     */
    public int effectiveMingap() {
        return Math.max(1, mingap);
    }
}
