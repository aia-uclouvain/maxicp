package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record RemoveDetour(SeqVar seqVar, int prev, int node, int after) implements ConstraintFromRecord {
}
