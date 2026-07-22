/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Reified temporal ordering constraint with transition times.
 *
 * <p>Links a boolean variable {@code isBefore} to the temporal ordering of two intervals
 * with minimum transition times:
 * <ul>
 *   <li>{@code isBefore = true}  &hArr; {@code end(a) + transitionAB <= start(b)}
 *       (interval a finishes before b starts, with at least {@code transitionAB} in between)</li>
 *   <li>{@code isBefore = false} &hArr; {@code end(b) + transitionBA <= start(a)}
 *       (interval b finishes before a starts, with at least {@code transitionBA} in between)</li>
 * </ul>
 *
 * <p>The constraint propagates in three directions:
 * <ol>
 *   <li><b>Boolean &rarr; temporal:</b> when {@code isBefore} is fixed, enforce the
 *       corresponding start/end bounds with transition times.</li>
 *   <li><b>Temporal &rarr; boolean:</b> when the temporal gap between the intervals is
 *       large enough, fix {@code isBefore} to the entailed ordering.</li>
 *   <li><b>Infeasibility detection:</b> when one ordering is impossible (the earliest
 *       end of one interval plus the transition exceeds the latest start of the other),
 *       fix {@code isBefore} to the only feasible ordering.</li>
 * </ol>
 *
 * <p>This constraint is a building block of {@link NoOverlapWithPosition} and replaces the
 * more generically named {@link NoOverlapBinaryWithTransitionTime} in that context,
 * making the reified semantics explicit.
 *
 * @author Pierre Schaus
 */
public class IsEndBeforeStartWithTransition extends AbstractCPConstraint {

    private final CPIntervalVar a, b;
    private final CPBoolVar isBefore;
    private final int transitionAB; // minimum gap if a is before b
    private final int transitionBA; // minimum gap if b is before a

    /**
     * Creates a reified temporal ordering constraint with transition times.
     *
     * @param isBefore    boolean variable: true iff a ends before b starts (with transitionAB gap)
     * @param a           first interval
     * @param b           second interval
     * @param transitionAB minimum gap between end(a) and start(b) when a precedes b
     * @param transitionBA minimum gap between end(b) and start(a) when b precedes a
     */
    public IsEndBeforeStartWithTransition(CPBoolVar isBefore, CPIntervalVar a, CPIntervalVar b,
                                          int transitionAB, int transitionBA) {
        super(a.getSolver());
        this.a = a;
        this.b = b;
        this.isBefore = isBefore;
        this.transitionAB = transitionAB;
        this.transitionBA = transitionBA;
    }

    @Override
    public void post() {
        if (!a.isAbsent() && !b.isAbsent()) {
            a.propagateOnChange(this);
            b.propagateOnChange(this);
            isBefore.propagateOnFix(this);
            propagate();
        }
    }

    @Override
    public void propagate() {
        if (a.isPresent() && b.isPresent()) {
            // Direction 1: boolean is fixed -> enforce temporal bounds
            // Direction 3: one ordering is infeasible -> fix boolean
            // Both are checked together, as in NoOverlapBinaryWithTransitionTime.

            if (a.endMin() + transitionAB > b.startMax() || isBefore.isFalse()) {
                // a cannot be before b (or isBefore is already false) -> b must be before a
                isBefore.fix(false);
                b.setEndMax(a.startMax() - transitionBA);
                a.setStartMin(b.endMin() + transitionBA);
            }
            if (b.endMin() + transitionBA > a.startMax() || isBefore.isTrue()) {
                // b cannot be before a (or isBefore is already true) -> a must be before b
                isBefore.fix(true);
                a.setEndMax(b.startMax() - transitionAB);
                b.setStartMin(a.endMin() + transitionAB);
            }
        }
        if (a.isAbsent() || b.isAbsent()) {
            setActive(false);
        }
    }
}
