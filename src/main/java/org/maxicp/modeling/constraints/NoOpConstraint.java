package org.maxicp.modeling.constraints;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.algebra.Expression;

import java.util.Collection;
import java.util.List;

/**
 * A constraint that does nothing. This is supposed to be used to force a solver to trigger its fixpoint.
 */
public class NoOpConstraint implements Constraint {

    public static NoOpConstraint noOp = new NoOpConstraint();

    @Override
    public Collection<? extends Expression> scope() {
        return List.of();
    }

    public static NoOpConstraint noOp() {
        return noOp;
    };

}
