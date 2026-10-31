/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint inserting {@code node} immediately after {@code prev} in a sequence variable ({@link SeqVar}).
 *
 * @param seqVar the sequence variable
 * @param prev   the predecessor node
 * @param node   the node to insert after {@code prev}
 */
public record Insert(SeqVar seqVar, int prev, int node) implements ConstraintFromRecord {
}
