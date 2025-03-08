package org.maxicp.modeling;

import org.maxicp.modeling.algebra.bool.BoolExpression;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.modeling.constraints.ExpressionIsTrue;
import org.maxicp.modeling.constraints.NoOpConstraint;
import org.maxicp.modeling.symbolic.SymbolicModel;

import java.util.function.Supplier;

/**
 * Maintains the current model and proxies calls to it.
 */
public interface ModelProxy {

    Model getModel();

    default boolean isSymbolic() {
        return getModel() instanceof SymbolicModel;
    }

    default boolean isConcrete() {
        return getModel() instanceof ConcreteModel;
    }

    /**
     * Returns the current symbolic model (if isConcrete() is true, it returns a symbolic copy of the current Concrete
     * model)
     */
    default SymbolicModel getSymbolicModel() {
        return getModel().symbolicCopy();
    }

    /**
     * Returns the current ConcreteModel. isConcrete() needs to be true, otherwise it raises NotConcreteException.
     */
    default ConcreteModel getConcreteModel() throws NotConcreteException {
        return switch (getModel()) {
            case ConcreteModel m -> m;
            default -> throw new NotConcreteException();
        };
    }

    default void add(Constraint c) {
        add(c, true);
    }

    default void add(BoolExpression c) {
        add(c, true);
    }

    default void fixpoint() {
        add(NoOpConstraint.noOp, true);
    }

    /**
     * Shortcut for baseModel.getModel().add(c, enforceFixPoint);
     * @param c constraint to add
     */
    default void add(Constraint c, boolean enforceFixPoint) {
        switch (getModel()) {
            case SymbolicModel sm -> setModel(sm.add(c));
            case ConcreteModel cm -> cm.add(c, enforceFixPoint);
            default -> throw new IllegalStateException("Unexpected value: " + getModel());
        }
    }

    default void add(BoolExpression c, boolean enforceFixPoint) {
        add(new ExpressionIsTrue(c), enforceFixPoint);
    }

    class NotConcreteException extends RuntimeException {};

    default <R> R runWithModel(ConcreteModel model, Supplier<R> fun) {
        Model oldModel = getModel();
        try {
            setModel(model);
            return fun.get();
        }
        finally {
            setModel(oldModel);
        }
    }

    default void runWithModel(ConcreteModel model, Runnable fun) {
        Model oldModel = getModel();
        try {
            setModel(model);
            fun.run();
        }
        finally {
            setModel(oldModel);
        }
    }

    <T extends Model> T setModel(T m);
}
