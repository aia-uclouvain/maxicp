/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.IntervalVar;

public class IntervalStatus extends BoolVarImpl{
    public final IntervalVar intervalVar;

    public IntervalStatus(IntervalVar intervalVar) {
        super(intervalVar.getModelProxy(), !intervalVar.isPresent(), !intervalVar.isAbsent());
        this.intervalVar = intervalVar;
    }
}
