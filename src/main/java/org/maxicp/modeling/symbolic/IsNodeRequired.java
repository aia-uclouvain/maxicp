/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.SeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.EXCLUDED;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class IsNodeRequired extends BoolVarImpl {

    public final SeqVar seqVar;
    public final int node;

    public IsNodeRequired(SeqVar seqVar, int node) {
        super(seqVar.getModelProxy(), !seqVar.isNode(node, REQUIRED), !seqVar.isNode(node, EXCLUDED));
        this.seqVar = seqVar;
        this.node = node;
    }
}
