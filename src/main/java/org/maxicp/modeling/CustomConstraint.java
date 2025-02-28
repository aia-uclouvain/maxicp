package org.maxicp.modeling;

import org.maxicp.modeling.concrete.ConcreteConstraint;
import org.maxicp.modeling.concrete.ConcreteModel;

/**
 * This class should be used to implement custom constraints.
 * It is responsible for instantiating a constraint into a constraint understandable by a given ConcreteModel
 * @param <C> type of concrete model
 * @param <P> type of concrete constraint
 */
public interface CustomConstraint<C extends ConcreteModel, P extends ConcreteConstraint<C>> extends Constraint {

    /**
     * Instantiate the modeling constraint into a concrete constraint
     * @param concreteModel model where the constraint must be added
     * @return concrete constraint
     */
    P instantiate(C concreteModel);
}
