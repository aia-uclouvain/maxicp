/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Require a node to be visited within a {@link SeqVar}
 * @param seqVar sequence where a particular node must be visited
 * @param node node that must be visited
 */
public record Require(SeqVar seqVar, int node) implements ConstraintFromRecord {}
