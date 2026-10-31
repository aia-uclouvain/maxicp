package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint forbidding {@code node} from being inserted immediately between {@code prev} and {@code after}
 * in a sequence variable ({@link SeqVar}).
 *
 * @param seqVar the sequence variable
 * @param prev   the predecessor node
 * @param node   the node whose insertion between {@code prev} and {@code after} is forbidden
 * @param after  the successor node
 */
public record NotBetween(SeqVar seqVar, int prev, int node, int after) implements ConstraintFromRecord {
}
