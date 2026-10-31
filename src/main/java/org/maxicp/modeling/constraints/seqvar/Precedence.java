package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Precedence constraint on a sequence variable ({@link SeqVar}), ensuring that the given {@code nodes}
 * appear in the specified relative order if visited in the sequence.
 *
 * @param seqVar the sequence variable
 * @param nodes  the nodes that must respect the given relative ordering
 */
public record Precedence(SeqVar seqVar, int... nodes) implements ConstraintFromRecord {
}
