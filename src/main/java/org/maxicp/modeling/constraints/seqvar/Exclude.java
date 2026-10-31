/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint excluding a node from a sequence variable ({@link SeqVar}) so that it is not visited.
 *
 * @param seqVar the sequence variable
 * @param node   the node to exclude from the sequence
 */
public record Exclude(SeqVar seqVar, int node) implements ConstraintFromRecord {
}
