package org.maxicp.search;

import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.state.StateManager;

import java.util.function.Supplier;

public abstract class SymbolicSearchMethod extends AbstractSearchMethod<SymbolicModel> {
    public SymbolicSearchMethod(StateManager sm, Supplier<SymbolicModel[]> branching) {
        super(sm, branching);
    }
}
