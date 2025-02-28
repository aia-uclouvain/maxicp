package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record Precedence(SeqVar seqVar, int... nodes) implements ConstraintFromRecord {
}
