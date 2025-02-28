package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record SubSequence(SeqVar main, SeqVar sub) implements ConstraintFromRecord {
}
