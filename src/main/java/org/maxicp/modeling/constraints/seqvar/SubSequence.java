package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint ensuring that the sequence {@code sub} is a subsequence of {@code main}
 * (all nodes visited in {@code sub} are visited in {@code main} in the same relative order).
 *
 * @param main the main (super) sequence variable
 * @param sub  the subsequence variable
 */
public record SubSequence(SeqVar main, SeqVar sub) implements ConstraintFromRecord {
}
