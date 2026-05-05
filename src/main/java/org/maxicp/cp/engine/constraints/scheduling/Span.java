/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */
package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.Constants;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Span Constraint:
 * Enforces that if the interval span is present, then it begins with the first interval intervals from the array alt and ends with the latest.
 * If span is absent, then all the intervals of alt must be absent.
 *
 * @author Augustin Delecluse
 */
public class Span extends AbstractCPConstraint {

    private final CPIntervalVar span;
    private final CPIntervalVar[] alts;

    public Span(CPIntervalVar span, CPIntervalVar[] alts) {
        super(span.getSolver());
        this.span = span;
        this.alts = alts;
    }

    @Override
    public void post() {
        span.propagateOnChange(this);
        for(CPIntervalVar alt : alts) alt.propagateOnChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        if (span.isAbsent()) {
            for (CPIntervalVar alt : alts) {
                alt.setAbsent();
            }
            setActive(false);
        } else {
            // new bounds for span
            int startMinAlts = Constants.HORIZON;
            int startMaxAlts = 0;
            int endMinAlts = Constants.HORIZON;
            int endMaxAlts = 0;
            // count of absence / optional, and tracking of one optional interval
            int nAbsent = 0;
            int nOptional = 0;
            int nPresent = 0;
            CPIntervalVar onlyOptional = null;
            for (CPIntervalVar alt : alts) {
                if (alt.isAbsent()) {
                    nAbsent++;
                } else {
                    if (alt.isPresent()) {
                        span.setPresent();
                        nPresent++;
                    } else {
                        nOptional++;
                        onlyOptional = alt;
                    }
                    // interval cannot begin before / end after the span
                    alt.setStartMin(span.startMin());
                    alt.setEndMax(span.endMax());
                    // update candidate for start / end of the span
                    startMinAlts = Math.min(startMinAlts, alt.startMin());
                    startMaxAlts = Math.max(startMaxAlts, alt.startMax());
                    endMinAlts = Math.min(endMinAlts, alt.endMin());
                    endMaxAlts = Math.max(endMaxAlts, alt.endMax());
                }
            }
            if (nAbsent == alts.length) {
                span.setAbsent();
                setActive(false);
            } else {
                if (nOptional == 1 && nPresent == 0 && span.isPresent()) {
                    onlyOptional.setPresent();
                }
                // shrink the time of span
                span.setStartMin(startMinAlts);
                span.setStartMax(startMaxAlts);
                span.setEndMin(endMinAlts);
                span.setEndMax(endMaxAlts);
            }
        }
    }
}
