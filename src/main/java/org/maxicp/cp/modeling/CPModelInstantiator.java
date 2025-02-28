/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.modeling;

import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.MaxiCP;
import org.maxicp.modeling.Model;
import org.maxicp.modeling.ModelProxyInstantiator;
import org.maxicp.state.StateManager;
import org.maxicp.state.copy.Copier;
import org.maxicp.state.trail.Trailer;

import java.util.function.Supplier;

public class CPModelInstantiator {
    public record Instantiator(Supplier<StateManager> stateManagerSupplier) implements ModelProxyInstantiator.ModelInstantiator<ConcreteCPModel> {
        @Override
        public ConcreteCPModel instantiate(Model m) {
            CPSolver s = new MaxiCP(stateManagerSupplier.get(), m.getModelProxy());
            return new ConcreteCPModel(m.getModelProxy(), s, m.symbolicCopy());
        }
    }

    static public final Instantiator withTrailing = new Instantiator(Trailer::new);
    static public final Instantiator withCopying = new Instantiator(Copier::new);
    static public final Instantiator base = withTrailing;
}
