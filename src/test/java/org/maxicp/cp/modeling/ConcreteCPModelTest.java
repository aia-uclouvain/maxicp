package org.maxicp.cp.modeling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.CustomConstraint;
import org.maxicp.modeling.Model;
import org.maxicp.modeling.concrete.ConcreteModelTest;
import org.maxicp.state.StateManager;

public class ConcreteCPModelTest extends ConcreteModelTest<ConcreteCPModel, CPConstraint> {

    @Override
    public ConcreteCPModel modelSupplier(StateManager stateManager) {
        Model model = Factory.makeModelDispatcher().getModel();
        ConcreteCPModel concreteCPModel = new CPModelInstantiator.Instantiator(() -> stateManager).instantiate(model);
        return concreteCPModel;
    }

    @Override
    public CustomConstraint<ConcreteCPModel, CPConstraint> mockConstraint() {
        return new MockConstraint() {
            @Override
            public CPConstraint instantiate(ConcreteCPModel concreteModel) {
                return new AbstractCPConstraint(concreteModel.solver) {
                };
            }
        };
    }
}
