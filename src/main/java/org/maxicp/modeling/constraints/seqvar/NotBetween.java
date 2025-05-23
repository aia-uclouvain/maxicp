package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record NotBetween(SeqVar seqVar, int prev, int node, int after) implements ConstraintFromRecord {
}
